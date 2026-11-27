import { Agent, type AgentContext } from "agents";
import { Sessions, type SessionMessage } from "agents/sessions";
import {
	convertToModelMessages,
	createGateway,
	createUIMessageStream,
	createUIMessageStreamResponse,
	isStepCount,
	isTextUIPart,
	pruneMessages,
	streamText,
	toUIMessageStream,
	type UIMessage,
} from "ai";
import { parseEnv, runtimeConfigSchema, type RuntimeConfig } from "../env.ts";
import { AppError } from "../errors.ts";
import { saveChatSummary } from "../repositories/chatRepository.ts";
import {
	assertSufficientCredits,
	computeStepMaxOutputTokens,
	deductCoinsOrThrow,
	extractStepCostUsd,
} from "../services/billingService.ts";
import {
	createGeneratedFileOffloadStream,
	decodeAttachments,
	deleteFilesFromR2,
	hydrateModelMessages,
	uploadDecodedAttachments,
} from "../services/chatMediaService.ts";
import type { ChatTurnPayload, StoredFile } from "../types/chat.ts";
import { createChatTools } from "./tools.ts";

const TITLE_LENGTH = 48;
const PREVIEW_LENGTH = 200;
const MAX_AGENT_STEPS = 6;
const MAX_CONTEXT_MESSAGES = 40;
const SSE_KEEP_ALIVE_MS = 15_000;

const AGENT_INSTRUCTIONS =
	"You are a concise, helpful AI assistant. You have tools to inspect chat files, create downloadable text files, and check the current time. Use tools only when they genuinely help answer the user's request.";

const BILLING_RETRY_OPTIONS = {
	maxAttempts: 5,
	baseDelayMs: 500,
	maxDelayMs: 3000,
};

const STREAM_TIMEOUT = {
	totalMs: 300_000,
	stepMs: 120_000,
	chunkMs: 30_000,
};

interface BillingRetryPayload {
	uid: string;
	gatewayCostUsd: number;
	idempotencyKey: string;
}

function buildUserMessage(
	id: string,
	text: string,
	uploadedFiles: readonly StoredFile[],
): UIMessage {
	const parts: UIMessage["parts"] = [];
	if (text.trim().length > 0) {
		parts.push({ type: "text", text });
	}
	for (const file of uploadedFiles) {
		parts.push({
			type: "file",
			mediaType: file.mimeType,
			filename: file.name,
			url: file.url,
		});
	}
	return { id, role: "user", parts };
}

function deriveChatTitle(text: string): string {
	const firstLine = text
		.split("\n")
		.map((line) => line.trim())
		.find((line) => line.length > 0);
	return (firstLine || "New chat").slice(0, TITLE_LENGTH);
}

export class ChatAgent extends Agent<Env> {
	readonly sessions = new Sessions();
	private readonly session = this.sessions.session();
	private turnInFlight = false;

	constructor(ctx: AgentContext, env: Env) {
		super(ctx, env);
		this.lifecycle.use(this.sessions);
	}

	async retryBillingDeduction(payload: BillingRetryPayload): Promise<void> {
		const config = parseEnv(runtimeConfigSchema, this.env);
		await deductCoinsOrThrow(config, payload.uid, payload.gatewayCostUsd, payload.idempotencyKey);
	}

	override async onRequest(request: Request): Promise<Response> {
		try {
			const pathname = new URL(request.url).pathname;
			if (request.method === "GET" && pathname === "/messages") {
				return this.streamMessagesResponse(request.signal);
			}
			if (request.method === "POST" && pathname === "/turn") {
				return await this.handleChatTurn(request);
			}
			return Response.json(
				{ error: { code: "not-found", message: "route not found" } },
				{ status: 404 },
			);
		} catch (error) {
			if (error instanceof AppError) {
				return Response.json(
					{ error: { code: error.code, message: error.message } },
					{ status: error.status },
				);
			}
			console.error(
				JSON.stringify({
					event: "chat_agent_request_error",
					error: error instanceof Error ? error.message : String(error),
				}),
			);
			return Response.json(
				{ error: { code: "internal", message: "internal error" } },
				{ status: 500 },
			);
		}
	}

	private streamMessagesResponse(signal: AbortSignal): Response {
		const encoder = new TextEncoder();
		const iterator = this.session.historyBatches({ signal })[Symbol.asyncIterator]();
		let started = false;
		let wroteMessage = false;

		const stream = new ReadableStream<Uint8Array>({
			pull: async (controller) => {
				if (!started) {
					started = true;
					controller.enqueue(encoder.encode("["));
				}
				while (true) {
					const next = await iterator.next();
					if (next.done) {
						controller.enqueue(encoder.encode("]"));
						controller.close();
						return;
					}
					if (next.value.length === 0) {
						continue;
					}
					let chunk = "";
					for (const message of next.value) {
						chunk += (wroteMessage ? "," : "") + JSON.stringify(message);
						wroteMessage = true;
					}
					controller.enqueue(encoder.encode(chunk));
					return;
				}
			},
			cancel: async () => {
				await iterator.return?.();
			},
		});

		return new Response(stream, {
			status: 200,
			headers: {
				"Content-Type": "application/json; charset=utf-8",
				"Cache-Control": "no-store",
			},
		});
	}

	private async loadRecentContextMessages(limit: number): Promise<UIMessage[]> {
		const messages: UIMessage[] = [];
		for await (const message of this.session.history({ newestFirst: true })) {
			if (message.role === "user" || message.role === "assistant" || message.role === "system") {
				messages.push(message as unknown as UIMessage);
				if (messages.length >= limit) {
					break;
				}
			}
		}
		messages.reverse();
		return messages;
	}

	private async handleChatTurn(request: Request): Promise<Response> {
		if (this.turnInFlight) {
			throw new AppError("conflict", "another response is already in progress for this chat");
		}

		this.turnInFlight = true;
		let streamHandedOff = false;
		let uploadedFiles: StoredFile[] = [];

		try {
			const payload = (await request.json()) as ChatTurnPayload;
			const config = parseEnv(runtimeConfigSchema, this.env);
			const decodedAttachments = decodeAttachments(payload.message.attachments);
			const recentHistory = await this.loadRecentContextMessages(MAX_CONTEXT_MESSAGES);

			const model = payload.model;
			const availableBudgetUsd = assertSufficientCredits({
				coinBalance: payload.coinBalance,
				usdPerCoin: config.USD_PER_COIN,
				pricing: model.pricing,
				recentHistory,
				userText: payload.message.text,
				newAttachmentCount: decodedAttachments.length,
			});

			const userMessageId = crypto.randomUUID();
			const assistantMessageId = crypto.randomUUID();
			const billingIdempotencyKey = crypto.randomUUID();
			const generatedFiles: StoredFile[] = [];

			const uploaded = await uploadDecodedAttachments(decodedAttachments, {
				filesBucket: this.env.FILES_BUCKET,
				uid: payload.user.uid,
				chatId: payload.chatId,
				messageId: userMessageId,
			});
			uploadedFiles = uploaded.uploadedFiles;

			const userMessage = buildUserMessage(userMessageId, payload.message.text, uploadedFiles);
			const hydratedMessages = await hydrateModelMessages([...recentHistory, userMessage], {
				db: this.env.DB,
				filesBucket: this.env.FILES_BUCKET,
				uid: payload.user.uid,
				chatId: payload.chatId,
				uploadedBytesById: uploaded.uploadedBytesById,
			});
			const modelMessages = pruneMessages({
				messages: await convertToModelMessages(hydratedMessages),
				reasoning: "all",
				toolCalls: "before-last-4-messages",
				emptyMessages: "remove",
			});

			const tools = createChatTools({
				db: this.env.DB,
				filesBucket: this.env.FILES_BUCKET,
				uid: payload.user.uid,
				chatId: payload.chatId,
				assistantMessageId,
				uploadedFiles,
				generatedFiles,
			});

			let completedStepsCostUsd = 0;
			const handleStreamError = (error: unknown): string => {
				console.error(
					JSON.stringify({
						event: "chat_stream_error",
						uid: payload.user.uid,
						chatId: payload.chatId,
						error: error instanceof Error ? error.message : String(error),
					}),
				);
				return "An error occurred while generating the response.";
			};

			const stream = createUIMessageStream({
				generateId: () => assistantMessageId,
				onError: handleStreamError,
				execute: ({ writer }) => {
					writer.write({ type: "start", messageId: assistantMessageId });
					writer.write({
						type: "data-chat",
						data: {
							chatId: payload.chatId,
							userMessageId,
							assistantMessageId,
							files: uploadedFiles.map(({ id, name, mimeType, size, url }) => ({
								id,
								name,
								mimeType,
								size,
								url,
							})),
						},
						transient: true,
					});

					const result = streamText({
						model: createGateway({ apiKey: config.AI_GATEWAY_API_KEY })(model.id),
						instructions: AGENT_INSTRUCTIONS,
						messages: modelMessages,
						tools,
						timeout: STREAM_TIMEOUT,
						stopWhen: [
							isStepCount(MAX_AGENT_STEPS),
							() => completedStepsCostUsd >= availableBudgetUsd,
						],
						prepareStep: () => ({
							maxOutputTokens: computeStepMaxOutputTokens(
								availableBudgetUsd - completedStepsCostUsd,
								model.pricing.output,
							),
						}),
						reasoning: payload.reasoning as Parameters<typeof streamText>[0]["reasoning"],
						abortSignal: request.signal,
						providerOptions: {
							gateway: { user: payload.user.uid, tags: ["chat", "agent"] },
						},
						onStepEnd: (step) => {
							completedStepsCostUsd += extractStepCostUsd(step, model.pricing);
						},
					});

					const uiStream = toUIMessageStream({
						stream: result.stream,
						tools,
						sendStart: false,
						sendReasoning: true,
						sendSources: true,
						onError: handleStreamError,
						onEnd: ({ outcome }) => {
							writer.setOutcome(outcome);
						},
					});

					writer.merge(
						uiStream.pipeThrough(
							createGeneratedFileOffloadStream({
								filesBucket: this.env.FILES_BUCKET,
								uid: payload.user.uid,
								chatId: payload.chatId,
								assistantMessageId,
								generatedFiles,
							}),
						),
					);
				},
				onEnd: async ({ responseMessage, outcome, isAborted, isCancelled }) => {
					try {
						await this.finalizeTurn({
							config,
							payload,
							modelId: model.id,
							userMessage,
							assistantMessage: { ...responseMessage, id: assistantMessageId },
							uploadedFiles,
							generatedFiles,
							completedStepsCostUsd,
							billingIdempotencyKey,
							succeeded: outcome.status === "completed" && !isAborted && !isCancelled,
						});
					} finally {
						this.turnInFlight = false;
					}
				},
			});

			streamHandedOff = true;
			return createUIMessageStreamResponse({
				stream,
				keepAliveMs: SSE_KEEP_ALIVE_MS,
				headers: {
					"X-Chat-Id": payload.chatId,
					"X-User-Message-Id": userMessageId,
					"X-Assistant-Message-Id": assistantMessageId,
				},
			});
		} catch (error) {
			if (!streamHandedOff && uploadedFiles.length > 0) {
				await deleteFilesFromR2(this.env.FILES_BUCKET, uploadedFiles);
			}
			throw error;
		} finally {
			if (!streamHandedOff) {
				this.turnInFlight = false;
			}
		}
	}

	private async finalizeTurn(input: {
		config: RuntimeConfig;
		payload: ChatTurnPayload;
		modelId: string;
		userMessage: UIMessage;
		assistantMessage: UIMessage;
		uploadedFiles: readonly StoredFile[];
		generatedFiles: readonly StoredFile[];
		completedStepsCostUsd: number;
		billingIdempotencyKey: string;
		succeeded: boolean;
	}): Promise<void> {
		const { payload } = input;
		if (input.completedStepsCostUsd > 0) {
			try {
				await this.billOrEnqueue(input.config, {
					uid: payload.user.uid,
					gatewayCostUsd: input.completedStepsCostUsd,
					idempotencyKey: input.billingIdempotencyKey,
				});
			} catch (error) {
				console.error(
					JSON.stringify({
						event: "billing_queue_failed",
						uid: payload.user.uid,
						chatId: payload.chatId,
						error: error instanceof Error ? error.message : String(error),
					}),
				);
			}
		}

		const turnFiles = [...input.uploadedFiles, ...input.generatedFiles];
		if (!input.succeeded) {
			await deleteFilesFromR2(this.env.FILES_BUCKET, turnFiles);
			return;
		}

		const responseText = input.assistantMessage.parts
			.filter(isTextUIPart)
			.map((part) => part.text)
			.join("");
		const preview =
			responseText.slice(0, PREVIEW_LENGTH) || (input.generatedFiles.length > 0 ? "[file]" : "");

		try {
			await saveChatSummary(this.env.DB, {
				user: payload.user,
				chatId: payload.chatId,
				title: deriveChatTitle(payload.message.text),
				modelId: input.modelId,
				preview,
				at: Date.now(),
				files: turnFiles,
			});
		} catch (error) {
			console.error(
				JSON.stringify({
					event: "chat_summary_save_failed",
					uid: payload.user.uid,
					chatId: payload.chatId,
					error: error instanceof Error ? error.message : String(error),
				}),
			);
			await deleteFilesFromR2(this.env.FILES_BUCKET, turnFiles);
			return;
		}

		await this.session.appendMessage(input.userMessage as unknown as SessionMessage);
		await this.session.appendMessage(input.assistantMessage as unknown as SessionMessage);
	}

	private async billOrEnqueue(config: RuntimeConfig, payload: BillingRetryPayload): Promise<void> {
		try {
			await deductCoinsOrThrow(config, payload.uid, payload.gatewayCostUsd, payload.idempotencyKey);
		} catch (error) {
			console.error(
				JSON.stringify({
					event: "billing_deduction_failed",
					uid: payload.uid,
					gatewayCostUsd: payload.gatewayCostUsd,
					idempotencyKey: payload.idempotencyKey,
					error: error instanceof Error ? error.message : String(error),
				}),
			);
			await this.queue("retryBillingDeduction", payload, {
				id: payload.idempotencyKey,
				retry: BILLING_RETRY_OPTIONS,
			});
		}
	}
}
