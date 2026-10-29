import OpenAI from "openai";
import type { ChatCompletionChunk, ChatCompletionMessageParam } from "openai/resources/chat/completions";
import type { Stream } from "openai/streaming";
import { AI_GATEWAY_API_KEY } from "./config";

export const GATEWAY_BASE_URL = "https://ai-gateway.vercel.sh/v1";

export interface GatewayModelPricing {
  input?: string;
  output?: string;
}

export interface GatewayModel {
  id: string;
  object: string;
  created?: number;
  owned_by?: string;
  name?: string;
  description?: string;
  context_window?: number;
  max_tokens?: number;
  type?: string;
  modalities?: { input?: string[]; output?: string[] };
  pricing?: GatewayModelPricing;
}

interface GatewayModelListResponse {
  object: string;
  data: GatewayModel[];
}

interface ModelCatalog {
  models: GatewayModel[];
  expiresAt: number;
}

const MODEL_CATALOG_TTL_MS = 10 * 60 * 1000;

let modelCatalog: ModelCatalog | null = null;

async function getGatewayModels(): Promise<GatewayModel[]> {
  if (modelCatalog && modelCatalog.expiresAt > Date.now()) {
    return modelCatalog.models;
  }
  const client = getGatewayClient();
  const response = await client.get<unknown, GatewayModelListResponse>("/models");
  modelCatalog = { models: response.data ?? [], expiresAt: Date.now() + MODEL_CATALOG_TTL_MS };
  return modelCatalog.models;
}

export async function fetchGatewayModels(): Promise<GatewayModel[]> {
  return getGatewayModels();
}

export async function fetchGatewayModelIds(): Promise<string[]> {
  return (await getGatewayModels()).map((model) => model.id);
}

export const TOKEN_WEIGHT = 1;

export function tokenWeightForModel(_model: string): number {
  return TOKEN_WEIGHT;
}

let gatewayClient: OpenAI | null = null;

let gatewayKey = "";

export function getGatewayClient(): OpenAI {
  const apiKey = AI_GATEWAY_API_KEY.value();
  if (!gatewayClient || gatewayKey !== apiKey) {
    gatewayClient = new OpenAI({
      apiKey,
      baseURL: GATEWAY_BASE_URL,
      timeout: 30000,
      maxRetries: 1,
    });
    gatewayKey = apiKey;
  }
  return gatewayClient;
}

export function resetGatewayClientForTests(): void {
  gatewayClient = null;
  gatewayKey = "";
  modelCatalog = null;
}

export interface StreamChatParams {
  model: string;
  messages: ChatCompletionMessageParam[];
  temperature?: number;
  maxTokens?: number;
  signal?: AbortSignal;
}

export async function streamChatCompletion(
  params: StreamChatParams,
): Promise<Stream<ChatCompletionChunk>> {
  const client = getGatewayClient();
  return client.chat.completions.create(
    {
      model: params.model,
      messages: params.messages,
      temperature: params.temperature,
      max_tokens: params.maxTokens,
      stream: true,
      stream_options: { include_usage: true },
    },
    params.signal ? { signal: params.signal } : undefined,
  );
}
