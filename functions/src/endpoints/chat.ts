import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { logger } from "firebase-functions";
import { onCall, HttpsError } from "firebase-functions/https";
import { streamText, type ModelMessage } from "ai";
import { z } from "zod";
import { AI_GATEWAY_API_KEY, REVENUECAT_COIN_CODE, REVENUECAT_SECRET_API_KEY, TURSO_AUTH_TOKEN, TURSO_DATABASE_URL } from "../config";
import { fetchModels, getGateway } from "./listModels";
import { getChatWithMessages, saveChatTurn } from "../db/turso";

if (!getApps().length) initializeApp();

const REVENUECAT_BASE_URL = "https://api.revenuecat.com/v2";
const TITLE_LENGTH = 48;
const PREVIEW_LENGTH = 200;
const MAX_HISTORY_MESSAGES = 100;

// The client sends ONE new message. History lives in the DB and is loaded here.
const bodySchema = z.object({
  chatId: z.string().nullable().optional(),
  title: z.string().nullable().optional(),
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

// Used for both the balance lookup and the deduction.
function revenueCat(path: string, init: Omit<RequestInit, "headers"> & { headers?: Record<string, string> } = {}) {
  return fetch(`${REVENUECAT_BASE_URL}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${REVENUECAT_SECRET_API_KEY.value()}`, ...init.headers },
  });
}

// Returns null instead of throwing so a bad response shape can't undo a successful charge.
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

// Turns any unexpected failure (network, DB) into a clean "unavailable" error for the client.
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
    secrets: [AI_GATEWAY_API_KEY, REVENUECAT_SECRET_API_KEY, TURSO_AUTH_TOKEN, TURSO_DATABASE_URL],
  },
  async (request, response) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "sign in required");
    const uid = request.auth.uid;
    const startedAt = Date.now();
    console.log(`starting`);


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

    console.log(`starting part 2`);
    const parsedBody = bodySchema.safeParse(request.data);
    if (!parsedBody.success) {
      const issueSummary = parsedBody.error.issues
        .map((issue) => `${issue.path.join(".")}: ${issue.message}`)
        .join("; ");

      console.log(`error wile parsing ${parsedBody.error}`);
      throw new HttpsError("invalid-argument", issueSummary);
    }
    const body = parsedBody.data;
    const { chatId: requestedChatId, message } = body;
    const attachments = message.attachments ?? [];
    logger.info("chat request", { uid, model: body.model, chatId: requestedChatId ?? null, attachments: attachments.length });

    // Everything we need before spending money, fetched in parallel. The chat and its messages
    // come back in one DB round trip, and only if the chat belongs to this user.
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

    // History from the DB (text only; earlier attachments' bytes were never stored), then the new message.
    // The `||` placeholder avoids sending an empty message, which some providers reject.
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

    // ---- stream ----
    const clientStreams = request.acceptsStreaming;
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
    for await (const text of result.textStream) {
      fullText += text;
      if (clientStreams) response?.sendChunk({ type: "text", text });
    }
    if (streamErrors.length) {
      logger.error("model stream failed", streamErrors[0]);
      throw new HttpsError("unavailable", "model request failed");
    }

    // The user already has their answer, so nothing below may throw. Failures are logged and the
    // response still goes out.
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
        // From here on the user HAS been charged, so report it even if the balance can't be parsed.
        chargedCoins = coinsToCharge;
        balanceAfterCharge = parseCoinBalance(await deductionRes.json()) ?? userCoinBalance - coinsToCharge;
      } catch (error) {
        logger.error("deduction failed after successful chat", { uid, coinsToCharge, gatewayCostUsd, error });
      }
    }
    // ---- save ----
    // One atomic DB call: (new chat row | chat preview update) + the user message + the reply.
    let savedChatId: string | null = existingChat?.id ?? null;
    let saved = false;
    try {
      const now = Date.now();
      const chatId = existingChat?.id ?? randomUUID();
      const explicitTitle = body.title?.trim();
      const firstLine = message.content
        .split("\n")
        .map((line) => line.trim())
        .find((line) => line.length > 0);
      await saveChatTurn({
        userId: uid,
        chatId,
        newChat: existingChat
          ? null
          : { title: (explicitTitle || firstLine || "New chat").slice(0, TITLE_LENGTH), modelId: body.model },
        messages: [
          {
            id: randomUUID(),
            chatId,
            role: "user",
            content: message.content,
            createdAt: startedAt,
            status: "DONE",
            // Only names and types are stored; file bytes are not.
            attachments: JSON.stringify(attachments.map(({ name, mimeType }) => ({ name, mimeType }))),
          },
          {
            id: randomUUID(),
            chatId,
            role: "assistant",
            content: fullText,
            createdAt: Math.max(now, startedAt + 1),
            status: "DONE",
            attachments: "[]",
          },
        ],
        preview: fullText.slice(0, PREVIEW_LENGTH),
        at: now,
      });
      savedChatId = chatId;
      saved = true;
    } catch (error) {
      logger.error("chat persistence failed", { uid, chatId: savedChatId, error });
    }

    // chatId is null only when a NEW chat failed to save. `saved` covers both cases.
    return clientStreams
      ? { charged: chargedCoins, balance: balanceAfterCharge, chatId: savedChatId, saved }
      : { text: fullText, charged: chargedCoins, balance: balanceAfterCharge, chatId: savedChatId, saved };
  },
);
