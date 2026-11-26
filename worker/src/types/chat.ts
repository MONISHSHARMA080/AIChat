import { z } from "zod";
import type { TouchUserInput } from "./database.ts";

export const chatTurnBodySchema = z.object({
	model: z.string().min(1),
	reasoning: z.string().min(1).optional(),
});

export interface ChatAgentProps {
	user: TouchUserInput;
	chatId: string;
}

export interface FileMetadata {
	id: string;
	name: string;
	mimeType: string;
	size: number;
}

export interface StoredFile extends FileMetadata {
	r2Key: string;
	createdAt: number;
}

export interface ChatToolContext {
	db: D1Database;
	filesBucket: R2Bucket;
	uid: string;
	chatId: string;
	turnFiles: StoredFile[];
	onGeneratedFile: (file: StoredFile) => void;
	generatedFilesCount: () => number;
}

export interface BillingRetryPayload {
	uid: string;
	userCoinBalance: number;
	gatewayCostUsd: number;
	idempotencyKey: string;
}
