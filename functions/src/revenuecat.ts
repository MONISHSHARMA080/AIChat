import { REVENUECAT_COIN_CODE, REVENUECAT_PROJECT_ID, REVENUECAT_SECRET_API_KEY } from "./config";

const BASE_URL = "https://api.revenuecat.com/v2";

const REQUEST_TIMEOUT_MS = 15000;

export interface VirtualCurrencyBalance {
  code: string;
  name: string;
  balance: number;
}

export interface BalanceSnapshot {
  balances: VirtualCurrencyBalance[];
  coin: VirtualCurrencyBalance | null;
}

export type AdjustFailureReason = "customerNotFound" | "insufficient" | "rateLimited" | "error";

export interface AdjustResult {
  ok: boolean;
  reason: AdjustFailureReason | null;
  message: string | null;
  balance: number | null;
}

interface RevenueCatResponse {
  status: number;
  json: unknown;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function coinCode(): string {
  return REVENUECAT_COIN_CODE.value() || "COIN";
}

function safeMessage(json: unknown): string | null {
  if (isRecord(json) && typeof json.message === "string") {
    return json.message;
  }
  return null;
}

async function callRevenueCat(
  path: string,
  init: { method: "GET" | "POST"; idempotencyKey?: string; body?: unknown },
): Promise<RevenueCatResponse> {
  const apiKey = REVENUECAT_SECRET_API_KEY.value();
  if (!apiKey) {
    throw new Error("REVENUECAT_SECRET_API_KEY is not configured.");
  }
  const projectId = REVENUECAT_PROJECT_ID.value();
  if (!projectId) {
    throw new Error("REVENUECAT_PROJECT_ID is not configured.");
  }
  const headers: Record<string, string> = {
    Authorization: `Bearer ${apiKey}`,
    Accept: "application/json",
  };
  if (init.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (init.idempotencyKey) {
    headers["Idempotency-Key"] = init.idempotencyKey;
  }
  let response: Response;
  try {
    response = await fetch(`${BASE_URL}/projects/${encodeURIComponent(projectId)}${path}`, {
      method: init.method,
      headers,
      body: init.body === undefined ? undefined : JSON.stringify(init.body),
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (error) {
    throw new Error(`RevenueCat request failed: ${error instanceof Error ? error.message : String(error)}`);
  }
  let json: unknown = null;
  try {
    json = await response.json();
  } catch {
    json = null;
  }
  return { status: response.status, json };
}

function parseBalances(json: unknown): BalanceSnapshot {
  const code = coinCode();
  if (!isRecord(json) || !Array.isArray(json.items)) {
    return { balances: [], coin: null };
  }
  const balances: VirtualCurrencyBalance[] = [];
  for (const item of json.items) {
    if (!isRecord(item)) {
      continue;
    }
    const currencyCode = typeof item.currency_code === "string" ? item.currency_code : null;
    const balance =
      typeof item.balance === "number" && Number.isFinite(item.balance) ? Math.max(0, Math.floor(item.balance)) : null;
    if (!currencyCode || balance === null) {
      continue;
    }
    balances.push({
      code: currencyCode,
      name: typeof item.name === "string" ? item.name : currencyCode,
      balance,
    });
  }
  return { balances, coin: balances.find((entry) => entry.code === code) ?? null };
}

export async function getBalances(uid: string): Promise<BalanceSnapshot | null> {
  const { status, json } = await callRevenueCat(
    `/customers/${encodeURIComponent(uid)}/virtual_currencies?include_empty_balances=true`,
    { method: "GET" },
  );
  if (status === 404) {
    return null;
  }
  if (status >= 400) {
    console.error("revenuecat balances failed", status, safeMessage(json));
    throw new Error("RevenueCat balance lookup failed.");
  }
  return parseBalances(json);
}

export async function adjustBalance(
  uid: string,
  delta: number,
  idempotencyKey: string,
  reference: string,
): Promise<AdjustResult> {
  try {
    const { status, json } = await callRevenueCat(
      `/customers/${encodeURIComponent(uid)}/virtual_currencies/transactions?include_empty_balances=true`,
      {
        method: "POST",
        idempotencyKey,
        body: { adjustments: { [coinCode()]: delta }, reference },
      },
    );
    if (status >= 200 && status < 300) {
      return { ok: true, reason: null, message: null, balance: parseBalances(json).coin?.balance ?? null };
    }
    if (status === 404) {
      return { ok: false, reason: "customerNotFound", message: safeMessage(json), balance: null };
    }
    if (status === 409 || status === 422) {
      return { ok: false, reason: "insufficient", message: safeMessage(json), balance: null };
    }
    if (status === 429) {
      return { ok: false, reason: "rateLimited", message: safeMessage(json), balance: null };
    }
    console.error("revenuecat adjust failed", status, safeMessage(json));
    return { ok: false, reason: "error", message: safeMessage(json), balance: null };
  } catch (error) {
    console.error("revenuecat adjust failed", error);
    return {
      ok: false,
      reason: "error",
      message: error instanceof Error ? error.message : String(error),
      balance: null,
    };
  }
}
