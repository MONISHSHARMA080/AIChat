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
	isTextUIPart,
	pruneMessages,
	streamText,
	toUIMessageStream,
	type GenerateTextOnFinishCallback,
	type ToolSet,
} from "ai";
import { z } from "zod";
import { parseEnv, runtimeConfigSchema, type RuntimeConfig } from "../env.ts";
import { AppError, resolveOrThrowUnavailable } from "../errors.ts";
import { saveChatSummary } from "../repositories/chatRepository.ts";
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
import { fetchAvailableModels, resolveChatModel } from "../services/modelCatalogService.ts";
import type { AuthUser } from "../types/auth.ts";
import type { BillingRetryPayload, ChatAgentProps, ChatRow, StoredFile } from "../types/chat.ts";
import { createChatTools } from "./tools.ts";

const TITLE_LENGTH = 48;
const PREVIEW_LENGTH = 200;
const MAX_AGENT_STEPS = 6;
const MAX_CONTEXT_MESSAGES = 40;
const CONTEXT_STORAGE_KEY = "chat_context";

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
	userMessageId: string | null;
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

export class ChatAgent extends AIChatAgent<Env, ChatRow | null, ChatAgentProps> {
	override initialState: ChatRow | null = null;
	private readonly activeTurns = new Map<string, ActiveTurn>();

	override async onStart(props?: ChatAgentProps): Promise<void> {
		if (!props) {
			return;
		}
		await this.ctx.storage.put<ChatContext>(CONTEXT_STORAGE_KEY, {
			user: props.user,
			chatId: props.chatId,
		});
		if (props.chat && !this.state) {
			this.setState(props.chat);
		}
	}

	async retryBillingDeduction(payload: BillingRetryPayload): Promise<void> {
		const config = parseEnv(runtimeConfigSchema, this.env);
		await deductCoinsOrThrow(config, payload.uid, payload.gatewayCostUsd, payload.idempotencyKey);
	}

	override async onChatMessage(
		_onFinish: GenerateTextOnFinishCallback<ToolSet>,
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
		const recentMessages = this.messages.slice(-MAX_CONTEXT_MESSAGES);
		const availableBudgetUsd = assertSufficientCredits({
			coinBalance,
			usdPerCoin: config.USD_PER_COIN,
			pricing: model.pricing,
			messages: recentMessages,
		});

		const latestUserMessage = [...recentMessages]
			.reverse()
			.find((message) => message.role === "user");
		const userText = latestUserMessage
			? latestUserMessage.parts
					.filter(isTextUIPart)
					.map((part) => part.text)
					.join("")
			: "";
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
			const hydratedMessages = await hydrateModelMessages(messagesWithFileUrls, {
				db: this.env.DB,
				filesBucket: this.env.FILES_BUCKET,
				uid: context.user.uid,
				chatId: context.chatId,
				uploadedBytesById: offloaded.uploadedBytesById,
			});
			const modelMessages = pruneMessages({
				messages: await convertToModelMessages(hydratedMessages),
				reasoning: "all",
				toolCalls: "before-last-4-messages",
				emptyMessages: "remove",
			});

			const activeTurn: ActiveTurn = {
				context,
				modelId: model.id,
				userMessageId: latestUserMessage?.id ?? null,
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
				sendReasoning: true,
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

		const responseText = result.message.parts
			.filter(isTextUIPart)
			.map((part) => part.text)
			.join("");
		const preview =
			responseText.slice(0, PREVIEW_LENGTH) || (generatedFiles.length > 0 ? "[file]" : "");

		let updatedChat: ChatRow | null;
		try {
			updatedChat = await saveChatSummary(this.env.DB, {
				user: turn.context.user,
				chatId: turn.context.chatId,
				title: deriveChatTitle(turn.userText),
				modelId: turn.modelId,
				preview,
				at: Date.now(),
				files: turnFiles,
			});
		} catch (error) {
			console.error(
				JSON.stringify({
					event: "chat_summary_save_failed",
					uid: turn.context.user.uid,
					chatId: turn.context.chatId,
					error: error instanceof Error ? error.message : String(error),
				}),
			);
			await deleteFilesFromR2(this.env.FILES_BUCKET, turnFiles);
			return;
		}

		if (updatedChat) {
			this.setState(updatedChat);
		}

		if (turn.userMessageId && turn.uploadedFilesByPartIndex.size > 0) {
			await this.persistMessages(
				rewriteMessageFileUrls(this.messages, turn.userMessageId, turn.uploadedFilesByPartIndex),
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
