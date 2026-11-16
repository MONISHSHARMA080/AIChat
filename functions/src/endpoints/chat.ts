import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { logger } from "firebase-functions";
import { onCall, HttpsError } from "firebase-functions/https";
import { streamText, type ModelMessage } from "ai";
import { z } from "zod";
import { AI_GATEWAY_API_KEY, REVENUECAT_COIN_CODE, REVENUECAT_SECRET_API_KEY, TURSO_AUTH_TOKEN, TURSO_DATABASE_URL } from "../config";
import { fetchModels, getGateway } from "./listModels";
import { getChatWithMessages, saveChatTurn, type FileRow } from "../db/turso";

if (!getApps().length) initializeApp();

const REVENUECAT_BASE_URL = "https://api.revenuecat.com/v2";
const TITLE_LENGTH = 48;
const PREVIEW_LENGTH = 200;
const MAX_HISTORY_MESSAGES = 100;
const MAX_GENERATED_FILES = 10;
const MAX_GENERATED_FILE_BYTES = 8 * 1024 * 1024;

const EXTENSIONS: Record<string, string> = {
  "text/plain": "txt",
  "text/markdown": "md",
  "application/json": "json",
  "application/pdf": "pdf",
  "image/jpeg": "jpg",
  "image/svg+xml": "svg",
};

function extensionFor(mediaType: string): string {
  const base = mediaType.split(";")[0].trim().toLowerCase();
  return EXTENSIONS[base] ?? (base.split("/")[1]?.split("+")[0] || "bin");
}

const fileMetadataSchema = z.object({
  id: z.string(),
  name: z.string(),
  mimeType: z.string(),
  size: z.number(),
});

const generatedFileSchema = fileMetadataSchema.extend({
  data: z.string(),
});

const textChunkSchema = z.object({
  type: z.literal("text"),
  text: z.string(),
});

const fileChunkSchema = generatedFileSchema.extend({
  type: z.literal("file"),
});

export const chatStreamChunkSchema = z.discriminatedUnion("type", [textChunkSchema, fileChunkSchema]);

const savedTurnSchema = z.object({
  chat: z.object({ id: z.string(), title: z.string() }),
  userMessage: z.object({ id: z.string(), createdAt: z.number() }),
  assistantMessage: z.object({
    id: z.string(),
    createdAt: z.number(),
    files: z.array(fileMetadataSchema),
  }),
});

const billingSchema = z.object({
  charged: z.number(),
  balance: z.number(),
});

export const chatStreamResultSchema = billingSchema.extend({
  savedTurn: savedTurnSchema.nullable(),
});

export const chatFullResultSchema = chatStreamResultSchema.extend({
  text: z.string(),
  files: z.array(generatedFileSchema),
});

export const chatResultSchema = z.union([chatFullResultSchema, chatStreamResultSchema]);

export type FileMetadata = z.infer<typeof fileMetadataSchema>;
export type GeneratedFile = z.infer<typeof generatedFileSchema>;
export type ChatStreamChunk = z.infer<typeof chatStreamChunkSchema>;
export type SavedTurn = z.infer<typeof savedTurnSchema>;
export type Billing = z.infer<typeof billingSchema>;
export type ChatStreamResult = z.infer<typeof chatStreamResultSchema>;
export type ChatFullResult = z.infer<typeof chatFullResultSchema>;
export type ChatResult = z.infer<typeof chatResultSchema>;

const bodySchema = z.object({
  chatId: z.string().nullable().optional(),
  model: z.string().min(1),
  reasoning: z.string().min(1).optional(),
  message: z
    .object({
      content: z.string(),
      attachments: z
        .array(
          z.object({
            name: z.string().min(1),
            mimeType: z.string().min(1),
            data: z.string().min(1),
          }),
        )
        .max(5)
        .optional(),
    })
    .refine((message) => message.content.trim().length > 0 || (message.attachments ?? []).length > 0, {
      message: "message needs text or attachments",
    }),
});

const coinBalancesSchema = z.object({
  items: z.array(z.object({ currency_code: z.string(), balance: z.number() })),
});

const gatewayCostSchema = z.object({
  gateway: z.object({
    cost: z.union([z.string(), z.number()]).transform(Number).pipe(z.number().nonnegative()),
  }),
});

function revenueCat(path: string, init: Omit<RequestInit, "headers"> & { headers?: Record<string, string> } = {}) {
  return fetch(`${REVENUECAT_BASE_URL}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${REVENUECAT_SECRET_API_KEY.value()}`, ...init.headers },
  });
}

function parseCoinBalance(payload: unknown): number | null {
  const parsed = coinBalancesSchema.safeParse(payload);
  if (!parsed.success) return null;
  return parsed.data.items.find((item) => item.currency_code === REVENUECAT_COIN_CODE.value())?.balance ?? 0;
}

async function getUserCoinBalance(uid: string, projectId: string): Promise<number> {
  const res = await revenueCat(`/projects/${projectId}/customers/${encodeURIComponent(uid)}/virtual_currencies`);
  if (res.status === 404) return 0;
  if (!res.ok) {
    logger.error("revenuecat balance lookup failed", res.status, await res.text());
    throw new HttpsError("unavailable", "balance lookup failed");
  }
  const balance = parseCoinBalance(await res.json());
  if (balance === null) throw new HttpsError("unavailable", "unexpected balance response");
  return balance;
}

async function orUnavailable<T>(promise: Promise<T>, message: string): Promise<T> {
  return promise.catch((error) => {
    if (error instanceof HttpsError) throw error;
    logger.error(message, error);
    throw new HttpsError("unavailable", message);
  });
}

export const chat = onCall(
  {
    timeoutSeconds: 300,
    secrets: [AI_GATEWAY_API_KEY, REVENUECAT_COIN_CODE, REVENUECAT_SECRET_API_KEY, TURSO_AUTH_TOKEN, TURSO_DATABASE_URL],
  },
  async (request, response): Promise<ChatResult> => {
    if (!request.auth) throw new HttpsError("unauthenticated", "sign in required");
    const uid = request.auth.uid;
    const startedAt = Date.now();

    const parsedEnv = z
      .object({
        USD_PER_COIN: z.coerce.number().positive(),
        REVENUECAT_PROJECT_ID: z.string().min(1),
      })
      .safeParse(process.env);
    if (!parsedEnv.success) {
      logger.error("invalid environment", parsedEnv.error.issues);
      throw new HttpsError("internal", "server misconfigured");
    }
    const { USD_PER_COIN: usdPerCoin, REVENUECAT_PROJECT_ID: revenueCatProjectId } = parsedEnv.data;

    const parsedBody = bodySchema.safeParse(request.data);
    if (!parsedBody.success) {
      const issueSummary = parsedBody.error.issues
        .map((issue) => `${issue.path.join(".")}: ${issue.message}`)
        .join("; ");
      logger.warn("invalid chat body", issueSummary);
      throw new HttpsError("invalid-argument", issueSummary);
    }
    const body = parsedBody.data;
    const { chatId: requestedChatId, message } = body;
    const attachments = message.attachments ?? [];
    logger.info("chat request", { uid, model: body.model, chatId: requestedChatId ?? null, attachments: attachments.length });

    const [availableModels, userCoinBalance, existingChatAndMessages] = await Promise.all([
      orUnavailable(fetchModels(), "could not load models"),
      orUnavailable(getUserCoinBalance(uid, revenueCatProjectId), "balance lookup failed"),
      requestedChatId ? orUnavailable(getChatWithMessages(uid, requestedChatId), "chat lookup failed") : null,
    ]);
    if (requestedChatId && !existingChatAndMessages) throw new HttpsError("not-found", "chat not found");
    const existingChat = existingChatAndMessages?.chat ?? null;

    const model = availableModels.find((m) => m.id === body.model);
    if (!model) throw new HttpsError("invalid-argument", "unknown model");
    if (!model.pricing) throw new HttpsError("invalid-argument", "model has no pricing");
    if (body.reasoning && !model.reasoning.levels.includes(body.reasoning)) {
      const allowedLevels = model.reasoning.levels.join(", ") || "none";
      throw new HttpsError("invalid-argument", `reasoning must be one of: ${allowedLevels}`);
    }

    const history = (existingChatAndMessages?.messages ?? []).slice(-MAX_HISTORY_MESSAGES);
    const modelMessages: ModelMessage[] = [
      ...history.map((row) => ({
        role: row.role as "user" | "assistant",
        content: row.content || "[no text]",
      })),
      {
        role: "user",
        content: attachments.length
          ? [
            ...(message.content.trim() ? [{ type: "text" as const, text: message.content }] : []),
            ...attachments.map((attachment) => ({
              type: "file" as const,
              mediaType: attachment.mimeType,
              filename: attachment.name,
              data: attachment.data,
            })),
          ]
          : message.content,
      },
    ];

    const totalChars =
      history.reduce((sum, row) => sum + row.content.length, 0) +
      message.content.length +
      attachments.reduce((sum, attachment) => sum + attachment.data.length, 0);
    const estimatedInputUsd = Math.ceil(totalChars / 3.2) * model.pricing.input;
    logger.info("low credit check", { estimatedInputUsd, userCoinBalance, usdPerCoin });
    if (userCoinBalance * usdPerCoin < estimatedInputUsd) {
      throw new HttpsError("resource-exhausted", "insufficient credits");
    }

    const clientStreams = request.acceptsStreaming;
    const sendChunk = (chunk: ChatStreamChunk): void => {
      if (clientStreams) response?.sendChunk(chunk);
    };

    const streamErrors: unknown[] = [];
    const result = streamText({
      model: getGateway()(body.model),
      messages: modelMessages,
      reasoning: body.reasoning as Parameters<typeof streamText>[0]["reasoning"],
      providerOptions: { gateway: { user: uid, tags: ["chat"] } },
      onError: ({ error }) => {
        streamErrors.push(error);
      },
    });

    let fullText = "";
    const generatedFiles: GeneratedFile[] = [];
    for await (const part of result.fullStream) {
      if (part.type === "text-delta") {
        fullText += part.text;
        sendChunk({ type: "text", text: part.text });
        continue;
      }
      if (part.type === "file") {
        if (generatedFiles.length >= MAX_GENERATED_FILES) {
          logger.warn("generated file skipped: too many files", { uid, mediaType: part.file.mediaType });
          continue;
        }
        const size = part.file.uint8Array.length;
        if (size > MAX_GENERATED_FILE_BYTES) {
          logger.warn("generated file skipped: too large", { uid, mediaType: part.file.mediaType, size });
          continue;
        }
        const generatedFile: GeneratedFile = {
          id: randomUUID(),
          name: `generated-${generatedFiles.length + 1}.${extensionFor(part.file.mediaType)}`,
          mimeType: part.file.mediaType,
          size,
          data: part.file.base64,
        };
        generatedFiles.push(generatedFile);
        sendChunk({ type: "file", ...generatedFile });
      }
    }
    if (streamErrors.length) {
      logger.error("model stream failed", streamErrors[0]);
      throw new HttpsError("unavailable", "model request failed");
    }

    const parsedCost = gatewayCostSchema.safeParse(await result.providerMetadata);
    if (!parsedCost.success) logger.error("gateway reported no cost", parsedCost.error.issues);
    const gatewayCostUsd = parsedCost.success ? parsedCost.data.gateway.cost : 0;
    const coinsToCharge = Math.ceil(Number((gatewayCostUsd / usdPerCoin).toFixed(7)));

    let chargedCoins = 0;
    let balanceAfterCharge = userCoinBalance;
    if (coinsToCharge > 0) {
      try {
        const deductionRes = await revenueCat(
          `/projects/${revenueCatProjectId}/customers/${encodeURIComponent(uid)}/virtual_currencies/transactions`,
          {
            method: "POST",
            headers: { "Content-Type": "application/json", "Idempotency-Key": randomUUID() },
            body: JSON.stringify({ adjustments: { [REVENUECAT_COIN_CODE.value()]: -coinsToCharge } }),
          },
        );
        if (!deductionRes.ok) throw new Error(`deduction ${deductionRes.status}: ${await deductionRes.text()}`);
        chargedCoins = coinsToCharge;
        balanceAfterCharge = parseCoinBalance(await deductionRes.json()) ?? userCoinBalance - coinsToCharge;
      } catch (error) {
        logger.error("deduction failed after successful chat", { uid, coinsToCharge, gatewayCostUsd, error });
      }
    }

    let savedTurn: SavedTurn | null = null;
    try {
      const now = Date.now();
      const chatId = existingChat?.id ?? randomUUID();
      const firstLine = message.content
        .split("\n")
        .map((line) => line.trim())
        .find((line) => line.length > 0);
      const title = existingChat?.title ?? (firstLine || "New chat").slice(0, TITLE_LENGTH);
      const userMessageId = randomUUID();
      const assistantMessageId = randomUUID();
      const assistantCreatedAt = Math.max(now, startedAt + 1);
      const fileMetadata: FileMetadata[] = generatedFiles.map(({ id, name, mimeType, size }) => ({ id, name, mimeType, size }));
      const fileRows: FileRow[] = generatedFiles.map((file) => ({
        id: file.id,
        messageId: assistantMessageId,
        chatId,
        userId: uid,
        name: file.name,
        mimeType: file.mimeType,
        size: file.size,
        data: file.data,
        createdAt: assistantCreatedAt,
      }));
      await saveChatTurn({
        userId: uid,
        chatId,
        newChat: existingChat ? null : { title, modelId: body.model },
        messages: [
          {
            id: userMessageId,
            chatId,
            role: "user",
            content: message.content,
            createdAt: startedAt,
            status: "DONE",
            attachments: JSON.stringify(attachments.map(({ name, mimeType }) => ({ name, mimeType }))),
          },
          {
            id: assistantMessageId,
            chatId,
            role: "assistant",
            content: fullText,
            createdAt: assistantCreatedAt,
            status: "DONE",
            attachments: JSON.stringify(fileMetadata),
          },
        ],
        files: fileRows,
        preview: fullText.slice(0, PREVIEW_LENGTH) || (fileRows.length ? "[file]" : ""),
        at: assistantCreatedAt,
      });
      savedTurn = {
        chat: { id: chatId, title },
        userMessage: { id: userMessageId, createdAt: startedAt },
        assistantMessage: { id: assistantMessageId, createdAt: assistantCreatedAt, files: fileMetadata },
      };
    } catch (error) {
      logger.error("chat persistence failed", { uid, chatId: existingChat?.id ?? null, error });
    }

    const billing: Billing = { charged: chargedCoins, balance: balanceAfterCharge };

    if (clientStreams) {
      const streamedResponse: ChatStreamResult = { ...billing, savedTurn };
      return streamedResponse;
    }

    const fullResponse: ChatFullResult = { ...billing, savedTurn, text: fullText, files: generatedFiles };
    return fullResponse;
  },
);
