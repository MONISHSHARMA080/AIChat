import {
	AIChatAgent,
	type ChatResponseResult,
	type OnChatMessageOptions,
} from "@cloudflare/ai-chat";
import {
	convertToModelMessages,
	createGateway,
	createUIMessageStreamResponse,
	isStepCount,
	pruneMessages,
	streamText,
	toUIMessageStream,
	type GenerateTextOnEndCallback,
	type UIMessage,
} from "ai";
import { z } from "zod";
import { parseEnv, runtimeConfigSchema, type RuntimeConfig } from "../env.ts";
import { AppError, resolveOrThrowUnavailable } from "../errors.ts";
import { listRecentChatMessages, saveChatTurn } from "../repositories/chatRepository.ts";
import {
	assertSufficientCredits,
	computeStepMaxOutputTokens,
	deductCoinsOrThrow,
	extractStepCostUsd,
	getUserCoinBalance,
} from "../services/billingService.ts";
import {
	createGeneratedFileOffloadStream,
	deleteFilesFromR2,
	hydrateModelMessages,
	MAX_USER_TEXT_CHARS,
	offloadInlineUserFiles,
	rewriteMessageFileUrls,
} from "../services/chatMediaService.ts";
import {
	buildMessageRow,
	extractMessageText,
	hydrateUIMessagesFromRows,
} from "../services/chatMessageService.ts";
import { fetchAvailableModels, resolveChatModel } from "../services/modelCatalogService.ts";
import type { AuthUser } from "../types/auth.ts";
import type {
	BillingRetryPayload,
	ChatAgentProps,
	ChatAgentState,
	ChatRow,
	MessageRow,
	StoredFile,
} from "../types/chat.ts";
import { createChatTools } from "./tools.ts";

const TITLE_LENGTH = 48;
const PREVIEW_LENGTH = 200;
const MAX_AGENT_STEPS = 6;
const MAX_PERSISTED_MESSAGES = 200;
const MAX_CONTEXT_MESSAGES = 40;
const CONTEXT_STORAGE_KEY = "chat_context";

const AGENT_INSTRUCTIONS = "You are a concise, helpful AI assistant. You have tools to inspect chat files, create downloadable text files, and check the current time. Use tools only when they genuinely help answer the user's request.";

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

const turnBodySchema = z.object({
	model: z.string().trim().min(1).max(128),
	reasoning: z.string().trim().min(1).max(64).optional(),
});

interface ChatContext {
	user: AuthUser;
	chatId: string;
}

interface ActiveTurn {
	context: ChatContext;
	modelId: string;
	startedAt: number;
	userMessage: UIMessage | null;
	userText: string;
	uploadedFiles: StoredFile[];
	uploadedFilesByPartIndex: Map<number, StoredFile>;
	generatedFiles: StoredFile[];
	completedStepsCostUsd: number;
	billingIdempotencyKey: string;
}

function deriveChatTitle(text: string): string {
	const firstLine = text
		.split("\n")
		.map((line) => line.trim())
		.find((line) => line.length > 0);
	return (firstLine || "New chat").slice(0, TITLE_LENGTH);
}

export class ChatAgent extends AIChatAgent<Env, ChatAgentState | null, ChatAgentProps> {
	override initialState: ChatAgentState | null = null;
	override maxPersistedMessages = MAX_PERSISTED_MESSAGES;
	private readonly activeTurns = new Map<string, ActiveTurn>();

	override async onStart(props?: ChatAgentProps): Promise<void> {
		if (!props) {
			return;
		}
		await this.ctx.storage.put<ChatContext>(CONTEXT_STORAGE_KEY, {
			user: props.user,
			chatId: props.chatId,
		});
		if (!this.state || (props.chat && !this.state.chat)) {
			this.setState({ chatId: props.chatId, chat: props.chat });
		}
		if (props.chat && this.messages.length === 0) {
			const rows = await listRecentChatMessages(this.env.DB, props.chatId, MAX_PERSISTED_MESSAGES);
			const hydratedMessages = await hydrateUIMessagesFromRows(rows);
			if (hydratedMessages.length > 0) {
				await this.persistMessages(hydratedMessages);
			}
		}
	}

	async retryBillingDeduction(payload: BillingRetryPayload): Promise<void> {
		const config = parseEnv(runtimeConfigSchema, this.env);
		await deductCoinsOrThrow(config, payload.uid, payload.gatewayCostUsd, payload.idempotencyKey);
	}

	override async onChatMessage(
		_onFinish: GenerateTextOnEndCallback,
		options?: OnChatMessageOptions,
	): Promise<Response> {
		const context = await this.ctx.storage.get<ChatContext>(CONTEXT_STORAGE_KEY);
		if (!context) {
			throw new AppError("unauthenticated", "missing chat session context");
		}

		const parsedBody = turnBodySchema.safeParse(options?.body);
		if (!parsedBody.success) {
			const formattedIssues = parsedBody.error.issues
				.map((issue) => {
					const path = issue.path.map(String).join(".");
					return path ? `${path}: ${issue.message}` : issue.message;
				})
				.join("; ");
			throw new AppError("invalid-argument", formattedIssues || "invalid chat payload");
		}

		const config = parseEnv(runtimeConfigSchema, this.env);
		const [availableModels, coinBalance] = await Promise.all([
			resolveOrThrowUnavailable(
				fetchAvailableModels({
					apiKey: config.AI_GATEWAY_API_KEY,
					logoBaseUrl: config.LOGO_BASE_URL,
					cacheTtlHours: config.MODEL_CACHE_TTL_HOURS,
					ctx: this.ctx,
				}),
				"models are temporarily unavailable",
			),
			resolveOrThrowUnavailable(
				getUserCoinBalance(config, context.user.uid),
				"balance is temporarily unavailable",
			),
		]);

		const model = resolveChatModel(
			availableModels,
			parsedBody.data.model,
			parsedBody.data.reasoning,
		);
		const rawRecentMessages = this.messages.slice(-MAX_CONTEXT_MESSAGES);
		const firstUserIndex = rawRecentMessages.findIndex((message) => message.role === "user");
		const recentMessages = firstUserIndex > 0 ? rawRecentMessages.slice(firstUserIndex) : rawRecentMessages;
		const availableBudgetUsd = assertSufficientCredits({
			coinBalance,
			usdPerCoin: config.USD_PER_COIN,
			pricing: model.pricing,
			messages: recentMessages,
		});

		const startedAt = Date.now();
		const latestUserMessage = [...recentMessages]
			.reverse()
			.find((message) => message.role === "user");
		const userText = latestUserMessage ? extractMessageText(latestUserMessage) : "";
		if (userText.length > MAX_USER_TEXT_CHARS) {
			throw new AppError("invalid-argument", "message text exceeds maximum length");
		}

		const offloaded = latestUserMessage
			? await offloadInlineUserFiles(latestUserMessage, {
					filesBucket: this.env.FILES_BUCKET,
					uid: context.user.uid,
					chatId: context.chatId,
				})
			: {
					uploadedFiles: [],
					uploadedBytesById: new Map<string, { mimeType: string; bytes: Uint8Array }>(),
					filesByPartIndex: new Map<number, StoredFile>(),
				};

		try {
			const messagesWithFileUrls = latestUserMessage
				? rewriteMessageFileUrls(recentMessages, latestUserMessage.id, offloaded.filesByPartIndex)
				: recentMessages;
			const storedUserMessage = latestUserMessage
				? (messagesWithFileUrls.find((message) => message.id === latestUserMessage.id) ??
					latestUserMessage)
				: null;
			const hydratedMessages = await hydrateModelMessages(messagesWithFileUrls, {
				db: this.env.DB,
				filesBucket: this.env.FILES_BUCKET,
				uid: context.user.uid,
				chatId: context.chatId,
				uploadedBytesById: offloaded.uploadedBytesById,
			});
			const activeTurn: ActiveTurn = {
				context,
				modelId: model.id,
				startedAt,
				userMessage: storedUserMessage,
				userText,
				uploadedFiles: offloaded.uploadedFiles,
				uploadedFilesByPartIndex: offloaded.filesByPartIndex,
				generatedFiles: [],
				completedStepsCostUsd: 0,
				billingIdempotencyKey: crypto.randomUUID(),
			};

			const tools = createChatTools({
				db: this.env.DB,
				filesBucket: this.env.FILES_BUCKET,
				uid: context.user.uid,
				chatId: context.chatId,
				uploadedFiles: activeTurn.uploadedFiles,
				generatedFiles: activeTurn.generatedFiles,
			});

			const modelMessages = pruneMessages({
				messages: await convertToModelMessages(hydratedMessages, { tools }),
				reasoning: "before-last-message",
				toolCalls: "before-last-2-messages",
			});

			const result = streamText({
				model: createGateway({ apiKey: config.AI_GATEWAY_API_KEY })(model.id),
				instructions: AGENT_INSTRUCTIONS,
				messages: modelMessages,
				tools,
				timeout: STREAM_TIMEOUT,
				stopWhen: [
					isStepCount(MAX_AGENT_STEPS),
					() => activeTurn.completedStepsCostUsd >= availableBudgetUsd,
				],
				prepareStep: () => ({
					maxOutputTokens: computeStepMaxOutputTokens(
						availableBudgetUsd - activeTurn.completedStepsCostUsd,
						model.pricing.output,
					),
				}),
				reasoning: parsedBody.data.reasoning as Parameters<typeof streamText>[0]["reasoning"],
				abortSignal: options?.abortSignal,
				providerOptions: {
					gateway: { user: context.user.uid, tags: ["chat", "agent"] },
				},
				onStepEnd: (step) => {
					activeTurn.completedStepsCostUsd += extractStepCostUsd(step, model.pricing);
				},
			});

			const stream = toUIMessageStream({
				stream: result.stream,
				tools,
				sendSources: true,
				onError: (error) => {
					console.error(
						JSON.stringify({
							event: "chat_stream_error",
							uid: context.user.uid,
							chatId: context.chatId,
							error: error instanceof Error ? error.message : String(error),
						}),
					);
					return "An error occurred while generating the response.";
				},
			}).pipeThrough(
				createGeneratedFileOffloadStream({
					filesBucket: this.env.FILES_BUCKET,
					uid: context.user.uid,
					chatId: context.chatId,
					generatedFiles: activeTurn.generatedFiles,
				}),
			);

			if (options?.requestId) {
				this.activeTurns.set(options.requestId, activeTurn);
			}
			return createUIMessageStreamResponse({ stream });
		} catch (error) {
			await deleteFilesFromR2(this.env.FILES_BUCKET, offloaded.uploadedFiles);
			throw error;
		}
	}

	protected override async onChatResponse(result: ChatResponseResult): Promise<void> {
		const turn = this.activeTurns.get(result.requestId);
		this.activeTurns.delete(result.requestId);
		if (!turn) {
			return;
		}

		if (turn.completedStepsCostUsd > 0) {
			const config = parseEnv(runtimeConfigSchema, this.env);
			try {
				await this.billOrEnqueue(config, {
					uid: turn.context.user.uid,
					gatewayCostUsd: turn.completedStepsCostUsd,
					idempotencyKey: turn.billingIdempotencyKey,
				});
			} catch (error) {
				console.error(
					JSON.stringify({
						event: "billing_queue_failed",
						uid: turn.context.user.uid,
						chatId: turn.context.chatId,
						error: error instanceof Error ? error.message : String(error),
					}),
				);
			}
		}

		const generatedFiles = turn.generatedFiles.map((file) => ({
			...file,
			messageId: result.message.id,
		}));
		const turnFiles = [...turn.uploadedFiles, ...generatedFiles];

		if (result.status !== "completed") {
			await deleteFilesFromR2(this.env.FILES_BUCKET, turnFiles);
			return;
		}

		const persistedAssistantMessage = this.messages.find(
			(message) => message.id === result.message.id,
		);
		const assistantMessage = persistedAssistantMessage ?? result.message;
		const completedAt = Math.max(Date.now(), turn.startedAt + 1);
		const responseText = extractMessageText(assistantMessage);
		const preview =
			responseText.slice(0, PREVIEW_LENGTH) || (generatedFiles.length > 0 ? "[file]" : "");

		const turnMessages: MessageRow[] = [];
		if (!result.continuation && turn.userMessage) {
			turnMessages.push(
				buildMessageRow({
					chatId: turn.context.chatId,
					message: turn.userMessage,
					createdAt: turn.startedAt,
					status: "DONE",
					files: turn.uploadedFiles,
				}),
			);
		}
		if (persistedAssistantMessage && persistedAssistantMessage.parts.length > 0) {
			turnMessages.push(
				buildMessageRow({
					chatId: turn.context.chatId,
					message: persistedAssistantMessage,
					createdAt: completedAt,
					status: "DONE",
					files: generatedFiles,
				}),
			);
		}

		let updatedChat: ChatRow | null;
		try {
			updatedChat = await saveChatTurn(this.env.DB, {
				user: turn.context.user,
				chatId: turn.context.chatId,
				title: deriveChatTitle(turn.userText),
				modelId: turn.modelId,
				preview,
				at: completedAt,
				messages: turnMessages,
				files: turnFiles,
			});
		} catch (error) {
			console.error(
				JSON.stringify({
					event: "chat_turn_save_failed",
					uid: turn.context.user.uid,
					chatId: turn.context.chatId,
					error: error instanceof Error ? error.message : String(error),
				}),
			);
			await deleteFilesFromR2(this.env.FILES_BUCKET, turnFiles);
			return;
		}

		if (updatedChat) {
			this.setState({ chatId: updatedChat.id, chat: updatedChat });
		}

		if (
			turn.userMessage &&
			turn.uploadedFilesByPartIndex.size > 0 &&
			(await this.waitUntilStable({ timeout: 5_000 }))
		) {
			await this.persistMessages(
				rewriteMessageFileUrls(this.messages, turn.userMessage.id, turn.uploadedFilesByPartIndex),
			);
		}
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
