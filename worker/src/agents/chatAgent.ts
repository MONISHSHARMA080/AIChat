import { AIChatAgent, type OnChatMessageOptions } from "@cloudflare/ai-chat";
import {
	convertToModelMessages,
	createGateway,
	createUIMessageStreamResponse,
	isStepCount,
	isTextUIPart,
	pruneMessages,
	streamText,
	toUIMessageStream,
	type GenerateTextOnEndCallback,
	type ToolSet,
} from "ai";
import { z } from "zod";
import { parseEnv, runtimeConfigSchema, type RuntimeConfig } from "../env.ts";
import { AppError } from "../errors.ts";
import type { AuthUser } from "../middleware/firebaseAuth.ts";
import { saveChatSummary, type ChatRow } from "../repositories/chatRepository.ts";
import { deductCoinsOrThrow, getUserCoinBalance } from "../services/billingService.ts";
import { fetchAvailableModels } from "../services/modelCatalogService.ts";
import { createChatTools, type StoredFile } from "./tools.ts";

const TITLE_LENGTH = 48;
const PREVIEW_LENGTH = 200;
const MAX_AGENT_STEPS = 6;
const MIN_OUTPUT_RESERVE_TOKENS = 1024;
const MAX_OUTPUT_TOKENS_CAP = 8192;

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

const SESSION_STORAGE_KEY = "chat_session";

const chatTurnBodySchema = z.object({
	model: z.string().min(1),
	reasoning: z
		.enum(["provider-default", "none", "minimal", "low", "medium", "high", "xhigh", "max"])
		.optional(),
});

const gatewayCostSchema = z.object({
	gateway: z.object({
		cost: z
			.union([z.string().trim().min(1), z.number()])
			.transform(Number)
			.pipe(z.number().nonnegative()),
	}),
});

export interface ChatAgentProps {
	user: AuthUser;
	chat: ChatRow;
}

interface BillingRetryPayload {
	uid: string;
	gatewayCostUsd: number;
	idempotencyKey: string;
}

export class ChatAgent extends AIChatAgent<Env, ChatRow | null, ChatAgentProps> {
	override initialState: ChatRow | null = null;

	override async onStart(props?: ChatAgentProps): Promise<void> {
		if (props?.user && props?.chat) {
			await this.ctx.storage.put(SESSION_STORAGE_KEY, props);
			if (!this.state) {
				this.setState(props.chat);
			}
		}
	}

	async retryBillingDeduction(payload: BillingRetryPayload): Promise<void> {
		const config = parseEnv(runtimeConfigSchema, this.env);
		await deductCoinsOrThrow(config, payload.uid, payload.gatewayCostUsd, payload.idempotencyKey);
	}

	override async onChatMessage(
		_onFinish: GenerateTextOnEndCallback<ToolSet>,
		options?: OnChatMessageOptions,
	): Promise<Response | undefined> {
		const session = await this.ctx.storage.get<ChatAgentProps>(SESSION_STORAGE_KEY);
		if (!session) {
			throw new AppError("unauthenticated", "missing chat session");
		}

		const parsedBody = chatTurnBodySchema.safeParse(options?.body);
		if (!parsedBody.success) {
			const issues = parsedBody.error.issues
				.map((issue) => `${issue.path.join(".")}: ${issue.message}`)
				.join("; ");
			throw new AppError("invalid-argument", issues);
		}

		const { model: modelId, reasoning } = parsedBody.data;
		const config = parseEnv(runtimeConfigSchema, this.env);
		const [availableModels, userCoinBalance] = await Promise.all([
			fetchAvailableModels({
				apiKey: config.AI_GATEWAY_API_KEY,
				logoBaseUrl: config.LOGO_BASE_URL,
				cacheTtlHours: config.MODEL_CACHE_TTL_HOURS,
				ctx: this.ctx,
			}),
			getUserCoinBalance(config, session.user.uid),
		]);

		const model = availableModels.find((entry) => entry.id === modelId);
		if (!model) {
			throw new AppError("invalid-argument", "unknown model");
		}
		if (!model.pricing) {
			throw new AppError("invalid-argument", "model has no pricing");
		}
		if (reasoning && !model.reasoning.levels.includes(reasoning)) {
			const allowed = model.reasoning.levels.join(", ") || "none";
			throw new AppError("invalid-argument", `reasoning must be one of: ${allowed}`);
		}
		if (userCoinBalance <= 0) {
			throw new AppError("resource-exhausted", "insufficient credits");
		}

		const chatId = session.chat.id;
		const pricing = model.pricing;
		const availableBudgetUsd = userCoinBalance * config.USD_PER_COIN;
		const billingIdempotencyKey = crypto.randomUUID();
		const turnFiles: StoredFile[] = [];
		let completedStepsCostUsd = 0;

		const tools = createChatTools({
			db: this.env.DB,
			filesBucket: this.env.FILES_BUCKET,
			uid: session.user.uid,
			chatId,
			turnFiles,
		});

		const modelMessages = pruneMessages({
			messages: await convertToModelMessages(this.messages),
			reasoning: "before-last-message",
			toolCalls: "before-last-2-messages",
		});

		const result = streamText({
			model: createGateway({ apiKey: config.AI_GATEWAY_API_KEY })(model.id),
			instructions: AGENT_INSTRUCTIONS,
			messages: modelMessages,
			tools,
			timeout: STREAM_TIMEOUT,
			stopWhen: [isStepCount(MAX_AGENT_STEPS), () => completedStepsCostUsd >= availableBudgetUsd],
			prepareStep: () => {
				const remainingUsd = availableBudgetUsd - completedStepsCostUsd;
				const maxOutputTokens =
					pricing.output <= 0
						? MAX_OUTPUT_TOKENS_CAP
						: Math.max(
								MIN_OUTPUT_RESERVE_TOKENS,
								Math.min(
									MAX_OUTPUT_TOKENS_CAP,
									Math.floor(Math.max(0, remainingUsd) / pricing.output),
								),
							);
				return { maxOutputTokens };
			},
			reasoning,
			abortSignal: options?.abortSignal,
			providerOptions: {
				gateway: { user: session.user.uid, tags: ["chat", "agent"] },
			},
			onStepEnd: (step) => {
				const parsed = gatewayCostSchema.safeParse(step.providerMetadata);
				completedStepsCostUsd += parsed.success
					? parsed.data.gateway.cost
					: (step.usage.inputTokens ?? 0) * pricing.input +
						(step.usage.outputTokens ?? 0) * pricing.output;
			},
			onEnd: async (event) => {
				try {
					await this.billOrEnqueue(config, {
						uid: session.user.uid,
						gatewayCostUsd: completedStepsCostUsd,
						idempotencyKey: billingIdempotencyKey,
					});
				} catch (error) {
					console.error(
						JSON.stringify({
							event: "billing_queue_failed",
							uid: session.user.uid,
							chatId,
							error: error instanceof Error ? error.message : String(error),
						}),
					);
				}

				const firstUserMessage = this.messages.find((message) => message.role === "user");
				const firstLine = firstUserMessage?.parts
					.filter(isTextUIPart)
					.map((part) => part.text)
					.join("\n")
					.split("\n")
					.map((line) => line.trim())
					.find((line) => line.length > 0);
				const title = (firstLine || "New chat").slice(0, TITLE_LENGTH);
				const now = Date.now();

				try {
					const updatedChat = await saveChatSummary(this.env.DB, {
						user: session.user,
						chatId,
						title,
						modelId: model.id,
						preview: event.text.slice(0, PREVIEW_LENGTH) || (turnFiles.length > 0 ? "[file]" : ""),
						at: now,
						files: turnFiles.map((file) => ({
							id: file.id,
							messageId: options?.requestId ?? null,
							chatId,
							userId: session.user.uid,
							name: file.name,
							mimeType: file.mimeType,
							size: file.size,
							r2Key: file.r2Key,
							createdAt: file.createdAt,
						})),
					});
					if (updatedChat) {
						this.setState(updatedChat);
					}
				} catch (error) {
					console.error(
						JSON.stringify({
							event: "chat_summary_save_failed",
							uid: session.user.uid,
							chatId,
							error: error instanceof Error ? error.message : String(error),
						}),
					);
					if (turnFiles.length > 0) {
						await this.env.FILES_BUCKET.delete(turnFiles.map((file) => file.r2Key));
					}
				}
			},
			onAbort: async () => {
				try {
					if (completedStepsCostUsd > 0) {
						await this.billOrEnqueue(config, {
							uid: session.user.uid,
							gatewayCostUsd: completedStepsCostUsd,
							idempotencyKey: billingIdempotencyKey,
						});
					}
				} catch (error) {
					console.error(
						JSON.stringify({
							event: "abort_billing_failed",
							uid: session.user.uid,
							chatId,
							error: error instanceof Error ? error.message : String(error),
						}),
					);
				} finally {
					if (turnFiles.length > 0) {
						await this.env.FILES_BUCKET.delete(turnFiles.map((file) => file.r2Key));
					}
				}
			},
		});

		return createUIMessageStreamResponse({
			stream: toUIMessageStream({ stream: result.stream, tools }),
		});
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
