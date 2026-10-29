import { logger } from "firebase-functions";
import { onRequest } from "firebase-functions/v2/https";
import { createGateway } from "ai";
import { z } from "zod";
import { AI_GATEWAY_API_KEY } from "../config";

const CATALOG_URL = "https://ai-gateway.vercel.sh/v1/models";

export const gateway = createGateway({
  apiKey: AI_GATEWAY_API_KEY.value(),
});

const catalogSchema = z.object({
  data: z.array(
    z.object({
      id: z.string(),
      reasoning_options: z
        .array(z.object({ type: z.string(), values: z.array(z.string()).nullish() }))
        .nullish(),
    }),
  ),
});

export type Pricing = { input: number; output: number };
export type Reasoning = { levels: string[]; toggle: boolean; budgetTokens: boolean };
export type ModelInfo = {
  id: string;
  name: string;
  description: string | null;
  pricing: Pricing | null;
  reasoning: Reasoning;
};

async function fetchReasoning(): Promise<Map<string, Reasoning>> {
  const res = await fetch(CATALOG_URL);
  if (!res.ok) throw new Error(`catalog request failed: ${res.status}`);
  const parsed = catalogSchema.parse(await res.json());

  return new Map(
    parsed.data.map((entry) => {
      const options = entry.reasoning_options ?? [];
      const levels = options
        .filter((o) => o.type === "effort")
        .flatMap((o) => o.values ?? []);
      return [
        entry.id,
        {
          levels: [...new Set(levels)],
          toggle: options.some((o) => o.type === "toggle"),
          budgetTokens: options.some((o) => o.type === "budget_tokens"),
        },
      ];
    }),
  );
}

export async function fetchModels(): Promise<ModelInfo[]> {
  const [sdk, reasoning] = await Promise.all([
    gateway.getAvailableModels(),
    fetchReasoning(),
  ]);

  return sdk.models
    .filter((m) => (m.modelType ?? "language") === "language")
    .map((m) => {
      const input = Number(m.pricing?.input);
      const output = Number(m.pricing?.output);
      return {
        id: m.id,
        name: m.name,
        description: m.description ?? null,
        pricing:
          Number.isFinite(input) && Number.isFinite(output) ? { input, output } : null,
        reasoning: reasoning.get(m.id) ?? { levels: [], toggle: false, budgetTokens: false },
      };
    });
}

export const models = onRequest({ cors: true }, async (req, res) => {
  if (req.method !== "GET") {
    res.status(405).json({ error: "method not allowed" });
    return;
  }

  const list = await fetchModels().catch((error) => {
    logger.error("model list failed", error);
    return null;
  });
  if (!list) {
    res.status(502).json({ error: "could not load models" });
    return;
  }

  res.json({ models: list });
});
