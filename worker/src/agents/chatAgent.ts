import { AIChatAgent, type OnChatMessageOptions } from "@cloudflare/ai-chat";
import {
	convertToModelMessages,
	createGateway,
	createUIMessageStreamResponse,
	isStepCount,
	pruneMessages,
	streamText,
	toUIMessageStream,
	type GenerateTextOnEndCallback,
	type ToolSet,
	type UIMessage,
} from "ai";
import {
	MAX_AGENT_STEPS,
	MAX_OUTPUT_TOKENS_CAP,
	MIN_OUTPUT_RESERVE_TOKENS,
	parseEnv,
	PREVIEW_LENGTH,
	runtimeConfigSchema,
	TITLE_LENGTH,
} from "../config/constants.ts";
import { saveChatSummary } from "../repositories/chatRepository.ts";
import {
	deductCoinsForCost,
	deductCoinsOrThrow,
	getUserCoinBalance,
} from "../services/billingService.ts";
import { fetchAvailableModels } from "../services/modelCatalogService.ts";
import { gatewayCostSchema } from "../types/billing.ts";
import {
	chatTurnBodySchema,
	type BillingRetryPayload,
	type ChatAgentProps,
	type StoredFile,
} from "../types/chat.ts";
import type { RuntimeConfig } from "../types/config.ts";
import { AppError } from "../types/errors.ts";
import { createChatTools } from "./tools.ts";

const AGENT_INSTRUCTIONS =
	"You are a concise, helpful AI assistant. You have tools to inspect chat files, create downloadable text files, and check the current time. Use tools only when they genuinely help answer the user's request.";

const BILLING_RETRY_OPTIONS = {
	maxAttempts: 5,
	baseDelayMs: 500,
	maxDelayMs: 3000,
};

const SESSION_STORAGE_KEY = "chat_session";

function computeStepMaxOutputTokens(remainingUsd: number, outputPrice: number): number {
	if (outputPrice <= 0) {
		return MAX_OUTPUT_TOKENS_CAP;
	}
	const affordable = Math.floor(Math.max(0, remainingUsd) / outputPrice);
	return Math.max(MIN_OUTPUT_RESERVE_TOKENS, Math.min(MAX_OUTPUT_TOKENS_CAP, affordable));
}

function extractChatTitle(messages: UIMessage[]): string {
	const firstUserMessage = messages.find((message) => message.role === "user");
	const firstText = firstUserMessage?.parts
		.filter((part): part is Extract<typeof part, { type: "text" }> => part.type === "text")
		.map((part) => part.text)
		.join("\n");
	const firstLine = firstText
		?.split("\n")
		.map((line) => line.trim())
		.find((line) => line.length > 0);
	return (firstLine || "New chat").slice(0, TITLE_LENGTH);
}

export class ChatAgent extends AIChatAgent<Env, unknown, ChatAgentProps> {
	override async onStart(props?: ChatAgentProps): Promise<void> {
		if (props?.user && props?.chatId) {
			await this.ctx.storage.put(SESSION_STORAGE_KEY, props);
		}
	}

	async retryBillingDeduction(payload: BillingRetryPayload): Promise<void> {
		const config = parseEnv(runtimeConfigSchema, this.env);
		await deductCoinsOrThrow(
			config,
			payload.uid,
			payload.userCoinBalance,
			payload.gatewayCostUsd,
			payload.idempotencyKey,
		);
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

		const pricing = model.pricing;
		const availableBudgetUsd = userCoinBalance * config.USD_PER_COIN;
		const billingIdempotencyKey = crypto.randomUUID();
		const turnFiles: StoredFile[] = [];
		let completedStepsCostUsd = 0;
		let completedStepsCount = 0;

		const tools = createChatTools({
			db: this.env.DB,
			filesBucket: this.env.FILES_BUCKET,
			uid: session.user.uid,
			chatId: session.chatId,
			turnFiles,
			onGeneratedFile: (file) => {
				turnFiles.push(file);
			},
			generatedFilesCount: () => turnFiles.length,
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
			maxOutputTokens: computeStepMaxOutputTokens(availableBudgetUsd, pricing.output),
			stopWhen: [isStepCount(MAX_AGENT_STEPS), () => completedStepsCostUsd >= availableBudgetUsd],
			prepareStep: () => ({
				maxOutputTokens: computeStepMaxOutputTokens(
					availableBudgetUsd - completedStepsCostUsd,
					pricing.output,
				),
			}),
			reasoning: reasoning as Parameters<typeof streamText>[0]["reasoning"],
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
				completedStepsCount += 1;
			},
			onEnd: async (event) => {
				let totalCostUsd = completedStepsCostUsd;
				if (completedStepsCount === 0) {
					const parsed = gatewayCostSchema.safeParse(event.finalStep.providerMetadata);
					if (parsed.success) {
						totalCostUsd = parsed.data.gateway.cost;
					}
				}
				await this.billOrEnqueue(config, {
					uid: session.user.uid,
					userCoinBalance,
					gatewayCostUsd: totalCostUsd,
					idempotencyKey: billingIdempotencyKey,
				});
				const now = Date.now();
				await saveChatSummary(this.env.DB, {
					user: session.user,
					chatId: session.chatId,
					title: extractChatTitle(this.messages),
					modelId: model.id,
					preview: event.text.slice(0, PREVIEW_LENGTH) || (turnFiles.length > 0 ? "[file]" : ""),
					at: now,
					files: turnFiles.map((file) => ({
						id: file.id,
						messageId: options?.requestId ?? null,
						chatId: session.chatId,
						userId: session.user.uid,
						name: file.name,
						mimeType: file.mimeType,
						size: file.size,
						r2Key: file.r2Key,
						createdAt: file.createdAt,
					})),
				});
			},
			onAbort: async () => {
				if (completedStepsCostUsd > 0) {
					await this.billOrEnqueue(config, {
						uid: session.user.uid,
						userCoinBalance,
						gatewayCostUsd: completedStepsCostUsd,
						idempotencyKey: billingIdempotencyKey,
					});
				}
				if (turnFiles.length > 0) {
					await this.env.FILES_BUCKET.delete(turnFiles.map((file) => file.r2Key));
				}
			},
		});

		return createUIMessageStreamResponse({
			stream: toUIMessageStream({ stream: result.stream, tools }),
		});
	}

	private async billOrEnqueue(config: RuntimeConfig, payload: BillingRetryPayload): Promise<void> {
		const result = await deductCoinsForCost(
			config,
			payload.uid,
			payload.userCoinBalance,
			payload.gatewayCostUsd,
			payload.idempotencyKey,
		);
		if (result.failed) {
			await this.queue("retryBillingDeduction", payload, {
				id: payload.idempotencyKey,
				retry: BILLING_RETRY_OPTIONS,
			});
		}
	}
}
