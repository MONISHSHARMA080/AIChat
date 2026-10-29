import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getAuth, type DecodedIdToken } from "firebase-admin/auth";
import { logger } from "firebase-functions";
import { onRequest, type Request } from "firebase-functions/v2/https";
import type { Response } from "express";
import { streamText } from "ai";
import { z } from "zod";
import { REVENUECAT_SECRET_API_KEY } from "../config";
import { fetchModels, gateway, type Pricing } from "./listModels";

if (!getApps().length) initializeApp();

const REVENUECAT_BASE = "https://api.revenuecat.com/v2";

const envSchema = z.object({
  USD_PER_COIN: z.coerce.number().positive(),
  REVENUECAT_PROJECT_ID: z.string().min(1),
  REVENUECAT_CURRENCY_CODE: z.string().min(1),
});

const bodySchema = z.object({
  model: z.string().min(1),
  reasoning: z.string().min(1).optional(),
  messages: z
    .array(
      z.object({
        role: z.enum(["system", "user", "assistant"]),
        content: z.string().min(1),
      }),
    )
    .min(1)
    .max(100),
});

const balancesSchema = z.object({
  items: z.array(z.object({ currency_code: z.string(), balance: z.number() })),
});

const costSchema = z.object({
  gateway: z.object({
    cost: z
      .union([z.string(), z.number()])
      .transform(Number)
      .pipe(z.number().nonnegative()),
  }),
});

type Env = z.infer<typeof envSchema>;
type Body = z.infer<typeof bodySchema>;
type Message = Body["messages"][number];
type ReasoningLevel = NonNullable<Parameters<typeof streamText>[0]["reasoning"]>;
type Result<T> = [T, null] | [null, ApiError];

class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

const attempt = <T>(work: Promise<T>): Promise<Result<T>> =>
  work.then(
    (value): Result<T> => [value, null],
    (error): Result<T> => {
      if (error instanceof ApiError) return [null, error];
      logger.error("unexpected error", error);
      return [null, new ApiError(500, "internal error")];
    },
  );

const sse = (res: Response, event: object) => {
  if (!res.writable) return;
  res.write(`data: ${JSON.stringify(event)}\n\n`);
};

const fail = (res: Response, err: ApiError) => {
  if (!res.headersSent) {
    res.status(err.status).json({ error: err.message });
    return;
  }
  sse(res, { type: "error", message: err.message });
  res.end();
};

async function readEnv(): Promise<Env> {
  const parsed = envSchema.safeParse(process.env);
  if (!parsed.success) {
    logger.error("invalid environment", parsed.error.issues);
    throw new ApiError(500, "server misconfigured");
  }
  return parsed.data;
}

async function authenticate(req: Request): Promise<DecodedIdToken> {
  const header = req.header("authorization") ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token) throw new ApiError(401, "missing bearer token");
  return getAuth()
    .verifyIdToken(token, true)
    .catch(() => {
      throw new ApiError(401, "invalid token");
    });
}

async function parseBody(req: Request): Promise<Body> {
  const parsed = bodySchema.safeParse(req.body);
  if (!parsed.success) {
    const detail = parsed.error.issues
      .map((issue) => `${issue.path.join(".")}: ${issue.message}`)
      .join("; ");
    throw new ApiError(400, detail);
  }
  return parsed.data;
}

async function resolvePricing(body: Body): Promise<Pricing> {
  const models = await fetchModels();
  const model = models.find((m) => m.id === body.model);
  if (!model) throw new ApiError(400, "unknown model");
  if (!model.pricing) throw new ApiError(400, "model has no pricing");
  if (body.reasoning && !model.reasoning.levels.includes(body.reasoning)) {
    const allowed = model.reasoning.levels.join(", ") || "none";
    throw new ApiError(400, `reasoning must be one of: ${allowed}`);
  }
  return model.pricing;
}

async function getBalance(uid: string, env: Env): Promise<number> {
  const res = await fetch(
    `${REVENUECAT_BASE}/projects/${env.REVENUECAT_PROJECT_ID}/customers/${encodeURIComponent(uid)}/virtual_currencies`,
    { headers: { Authorization: `Bearer ${REVENUECAT_SECRET_API_KEY}` } },
  );
  if (res.status === 404) return 0;
  if (!res.ok) {
    logger.error("revenuecat balance lookup failed", res.status, await res.text());
    throw new ApiError(502, "balance lookup failed");
  }
  const parsed = balancesSchema.safeParse(await res.json());
  if (!parsed.success) throw new ApiError(502, "unexpected balance response");
  const item = parsed.data.items.find(
    (i) => i.currency_code === env.REVENUECAT_CURRENCY_CODE,
  );
  return item?.balance ?? 0;
}

function checkLowCredit(input: {
  balance: number;
  pricing: Pricing;
  messages: Message[];
  usdPerCoin: number;
}): ApiError | null {
  const chars = input.messages.reduce((n, m) => n + m.content.length, 0);
  const inputTokens = Math.ceil(chars / 3);
  const inputUsd = inputTokens * input.pricing.input;
  if (input.balance * input.usdPerCoin < inputUsd) {
    return new ApiError(402, "insufficient credits");
  }
  return null;
}

async function streamChat(res: Response, body: Body, uid: string): Promise<number> {
  const errors: unknown[] = [];
  const result = streamText({
    model: gateway(body.model),
    messages: body.messages,
    reasoning: body.reasoning as ReasoningLevel | undefined,
    providerOptions: { gateway: { user: uid, tags: ["chat"] } },
    onError: ({ error }) => {
      errors.push(error);
    },
  });

  for await (const text of result.textStream) {
    sse(res, { type: "text", text });
  }

  if (errors.length) {
    logger.error("model stream failed", errors[0]);
    throw new ApiError(502, "model request failed");
  }

  const parsed = costSchema.safeParse(await result.providerMetadata);
  if (!parsed.success) {
    logger.error("gateway reported no cost", parsed.error.issues);
    throw new ApiError(502, "could not determine cost");
  }
  return parsed.data.gateway.cost;
}

async function deductCoins(
  uid: string,
  coins: number,
  env: Env,
  key: string,
): Promise<{ charged: number; balance: number }> {
  if (coins <= 0) return { charged: 0, balance: await getBalance(uid, env) };

  const res = await fetch(
    `${REVENUECAT_BASE}/projects/${env.REVENUECAT_PROJECT_ID}/customers/${encodeURIComponent(uid)}/virtual_currencies/transactions`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${REVENUECAT_SECRET_API_KEY}`,
        "Content-Type": "application/json",
        "Idempotency-Key": key,
      },
      body: JSON.stringify({
        adjustments: { [env.REVENUECAT_CURRENCY_CODE]: -coins },
      }),
    },
  );

  if (!res.ok) {
    logger.error("revenuecat deduction failed", res.status, await res.text());
    throw new ApiError(502, "deduction failed");
  }

  const parsed = balancesSchema.safeParse(await res.json());
  if (!parsed.success) throw new ApiError(502, "unexpected deduction response");
  const item = parsed.data.items.find(
    (i) => i.currency_code === env.REVENUECAT_CURRENCY_CODE,
  );
  return { charged: coins, balance: item?.balance ?? 0 };
}

export const chat = onRequest({ cors: true, timeoutSeconds: 300 }, async (req, res) => {
  if (req.method !== "POST") return fail(res, new ApiError(405, "method not allowed"));

  const [env, envErr] = await attempt(readEnv());
  if (envErr) return fail(res, envErr);

  const [user, authErr] = await attempt(authenticate(req));
  if (authErr) return fail(res, authErr);
  logger.info("chat request", { uid: user.uid, email: user.email });

  const [body, bodyErr] = await attempt(parseBody(req));
  if (bodyErr) return fail(res, bodyErr);

  const [prep, prepErr] = await attempt(
    Promise.all([resolvePricing(body), getBalance(user.uid, env)]),
  );
  if (prepErr) return fail(res, prepErr);
  const [pricing, balance] = prep;

  const denied = checkLowCredit({
    balance,
    pricing,
    messages: body.messages,
    usdPerCoin: env.USD_PER_COIN,
  });
  if (denied) return fail(res, denied);

  res.writeHead(200, {
    "Content-Type": "text/event-stream",
    "Cache-Control": "no-cache, no-transform",
    Connection: "keep-alive",
    "X-Accel-Buffering": "no",
  });
  res.flushHeaders();

  const [costUsd, streamErr] = await attempt(streamChat(res, body, user.uid));
  if (streamErr) return fail(res, streamErr);

  const coins = Math.ceil(Number((costUsd / env.USD_PER_COIN).toFixed(6)));
  const [receipt, deductErr] = await attempt(
    deductCoins(user.uid, coins, env, randomUUID()),
  );
  if (deductErr) {
    logger.error("deduction failed after successful chat", {
      uid: user.uid,
      coins,
      costUsd,
    });
    sse(res, { type: "done", charged: 0, balance });
    res.end();
    return;
  }

  sse(res, { type: "done", charged: receipt.charged, balance: receipt.balance });
  res.end();
});
