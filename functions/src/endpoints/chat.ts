import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { logger } from "firebase-functions";
import { onCall, HttpsError } from "firebase-functions/https";
import { streamText } from "ai";
import { z } from "zod";
import { AI_GATEWAY_API_KEY, REVENUECAT_COIN_CODE, REVENUECAT_SECRET_API_KEY } from "../config";
import { fetchModels, getGateway } from "./listModels";

if (!getApps().length) initializeApp();

// TODO: use firestore to cache the model and their proces for a day or so if they are >3 days old then fetch the models etc again and can also use it in the listModels 


const REVENUECAT_BASE_URL = "https://api.revenuecat.com/v2";

const bodySchema = z.object({
  model: z.string().min(1),
  reasoning: z.string().min(1).optional(),
  messages: z.array(
    z.union([
      z.object({ role: z.literal("system"), content: z.string().min(1) }),
      z
        .object({
          role: z.literal("user"),
          content: z.union([
            z.string(),
            z
              .array(
                z.union([
                  z.object({ type: z.literal("text"), text: z.string().min(1) }),
                  z.object({
                    type: z.literal("file"),
                    mediaType: z.string().min(1),
                    filename: z.string().min(1).optional(),
                    data: z.string().min(1),
                  }),
                ]),
              )
              .min(1)
              .max(20),
          ]),
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
        .refine(
          (message) => {
            const hasText =
              typeof message.content === "string"
                ? message.content.trim().length > 0
                : message.content.length > 0;
            return hasText || (message.attachments ?? []).length > 0;
          },
          { message: "user message needs text or attachments" },
        ),
      z.object({ role: z.literal("assistant"), content: z.string().min(1) }),
    ]),
  )
    .min(1)
    .max(100),
});

type Body = z.infer<typeof bodySchema>;

// Used twice (balance lookup + deduction response).
const coinBalancesSchema = z.object({
  items: z.array(z.object({ currency_code: z.string(), balance: z.number() })),
});

// Used twice (balance lookup + deduction response).
function parseCoinBalance(payload: unknown, errorMessage: string): number {
  const parsedBalances = coinBalancesSchema.safeParse(payload);
  if (!parsedBalances.success) throw new HttpsError("unavailable", errorMessage);
  const coinBalance = parsedBalances.data.items.find(
    (item) => item.currency_code === REVENUECAT_COIN_CODE.value(),
  );
  return coinBalance?.balance ?? 0;
}

// Used twice (before the request, and when the charge is 0 coins).
async function getUserCoinBalance(uid: string, revenueCatProjectId: string): Promise<number> {
  const res = await fetch(
    `${REVENUECAT_BASE_URL}/projects/${revenueCatProjectId}/customers/${encodeURIComponent(uid)}/virtual_currencies`,
    { headers: { Authorization: `Bearer ${REVENUECAT_SECRET_API_KEY.value()}` } },
  );
  if (res.status === 404) return 0;
  if (!res.ok) {
    logger.error("revenuecat balance lookup failed", res.status, await res.text());
    throw new HttpsError("unavailable", "balance lookup failed");
  }
  return parseCoinBalance(await res.json(), "unexpected balance response");
}

function toModelMessages(body: Body) {
  return body.messages.map((message) => {
    if (message.role !== "user") return message;
    const attachments = message.attachments ?? [];
    if (!attachments.length) return { role: message.role, content: message.content };
    const parts = [
      ...(typeof message.content === "string"
        ? message.content.trim().length > 0
          ? [{ type: "text" as const, text: message.content }]
          : []
        : message.content),
      ...attachments.map((attachment) => ({
        type: "file" as const,
        mediaType: attachment.mimeType,
        filename: attachment.name,
        data: attachment.data,
      })),
    ];
    return { role: message.role, content: parts };
  });
}

export const chat = onCall(
  {
    timeoutSeconds: 300,
    secrets: [AI_GATEWAY_API_KEY, REVENUECAT_SECRET_API_KEY],
  },
  async (request, response) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "sign in required");
    const uid = request.auth.uid;
    logger.info(`chat request `, { auth: request.auth, data: request.data });

    // --- env ---
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
      throw new HttpsError("invalid-argument", issueSummary);
    }
    const body = parsedBody.data;

    // --- model pricing + user balance (parallel) ---
    const [availableModels, userCoinBalance] = await Promise.all([
      fetchModels(),
      getUserCoinBalance(uid, revenueCatProjectId),
    ]);
    const model = availableModels.find((m) => m.id === body.model);
    if (!model) throw new HttpsError("invalid-argument", "unknown model");
    if (!model.pricing) throw new HttpsError("invalid-argument", "model has no pricing");
    if (body.reasoning && !model.reasoning.levels.includes(body.reasoning)) {
      const allowedLevels = model.reasoning.levels.join(", ") || "none";
      throw new HttpsError("invalid-argument", `reasoning must be one of: ${allowedLevels}`);
    }
    const modelPricing = model.pricing;
    // --- low credit check ---
    const totalChars = body.messages.reduce((total, message) => {
      const contentChars =
        typeof message.content === "string"
          ? message.content.length
          : message.content.reduce(
            (sum, part) => sum + (part.type === "text" ? part.text.length : part.data.length),
            0,
          );
      const attachmentChars =
        message.role === "user"
          ? (message.attachments ?? []).reduce((sum, attachment) => sum + attachment.data.length, 0)
          : 0;
      return total + contentChars + attachmentChars;
    }, 0);
    const estimatedInputTokens = Math.ceil(totalChars / 3.2);
    const estimatedInputUsd = estimatedInputTokens * modelPricing.input;
    logger.info(`low credit check , estimatedPriceUsd:${estimatedInputUsd},  modelPricing:   ${JSON.stringify(modelPricing)}, userCoinBalance:${userCoinBalance}, estimatedInputUsd:${estimatedInputUsd}`);
    if (userCoinBalance * usdPerCoin < estimatedInputUsd) {
      throw new HttpsError("resource-exhausted", "insufficient credits");
    }

    // --- stream the model response ---
    // Streaming clients (SDK `.stream()`) get chunks via sendChunk and the final
    // result is just billing info. Non-streaming clients (SDK `.call()`) get the
    // full text in the callable result instead, since sendChunk would be a no-op.
    const clientStreams = request.acceptsStreaming;
    const streamErrors: unknown[] = [];
    const result = streamText({
      model: getGateway()(body.model),
      messages: toModelMessages(body),
      reasoning: body.reasoning as Parameters<typeof streamText>[0]["reasoning"],
      providerOptions: { gateway: { user: uid, tags: ["chat"] } },
      onError: ({ error }) => {
        streamErrors.push(error);
      },
    });

    let fullText = "";
    for await (const text of result.textStream) {
      if (clientStreams) {
        response?.sendChunk({ type: "text", text });
      } else {
        fullText += text;
      }
    }

    if (streamErrors.length) {
      logger.error("model stream failed", streamErrors[0]);
      throw new HttpsError("unavailable", "model request failed");
    }

    const parsedCost = z
      .object({
        gateway: z.object({
          cost: z
            .union([z.string(), z.number()])
            .transform(Number)
            .pipe(z.number().nonnegative()),
        }),
      })
      .safeParse(await result.providerMetadata);
    if (!parsedCost.success) {
      logger.error("gateway reported no cost", parsedCost.error.issues);
      throw new HttpsError("unavailable", "could not determine cost");
    }
    const gatewayCostUsd = parsedCost.data.gateway.cost;

    // --- charge coins (failure here must not fail the chat) ---
    const coinsToCharge = Math.ceil(Number((gatewayCostUsd / usdPerCoin).toFixed(6)));
    let chargedCoins = 0;
    let balanceAfterCharge = userCoinBalance;

    try {
      if (coinsToCharge <= 0) {
        balanceAfterCharge = await getUserCoinBalance(uid, revenueCatProjectId);
      } else {
        const deductionRes = await fetch(
          `${REVENUECAT_BASE_URL}/projects/${revenueCatProjectId}/customers/${encodeURIComponent(uid)}/virtual_currencies/transactions`,
          {
            method: "POST",
            headers: {
              Authorization: `Bearer ${REVENUECAT_SECRET_API_KEY.value()}`,
              "Content-Type": "application/json",
              "Idempotency-Key": randomUUID(),
            },
            body: JSON.stringify({
              adjustments: { [REVENUECAT_COIN_CODE.value()]: -coinsToCharge },
            }),
          },
        );
        if (!deductionRes.ok) {
          logger.error("revenuecat deduction failed", deductionRes.status, await deductionRes.text());
          throw new HttpsError("unavailable", "deduction failed");
        }
        balanceAfterCharge = parseCoinBalance(
          await deductionRes.json(),
          "unexpected deduction response",
        );
        chargedCoins = coinsToCharge;
      }
    } catch (error) {
      logger.error("deduction failed after successful chat", {
        uid,
        coinsToCharge,
        gatewayCostUsd,
        error,
      });
      chargedCoins = 0;
      balanceAfterCharge = userCoinBalance;
    }

    // Streaming: text already delivered as chunks, so return billing only.
    // Non-streaming: include the full text so `.call()` clients get the reply.
    return clientStreams
      ? { charged: chargedCoins, balance: balanceAfterCharge }
      : { text: fullText, charged: chargedCoins, balance: balanceAfterCharge };
  },
);
