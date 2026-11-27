import { tool } from "ai";
import { z } from "zod";
import { getChatFileById, getChatFilesMetadata } from "../repositories/chatRepository.ts";
import {
	fileUrlForId,
	isReadableTextMediaType,
	MAX_GENERATED_FILE_BYTES,
	MAX_GENERATED_FILES_PER_TURN,
	MAX_READABLE_TEXT_CHARS,
	sanitizeMimeType,
	storeFileInR2,
} from "../services/chatMediaService.ts";
import type { StoredFile } from "../types/chat.ts";

export function createChatTools(context: {
	db: D1Database;
	filesBucket: R2Bucket;
	uid: string;
	chatId: string;
	assistantMessageId: string;
	uploadedFiles: StoredFile[];
	generatedFiles: StoredFile[];
}) {
	return {
		listChatFiles: tool({
			description: "List files uploaded or generated in this chat session.",
			inputSchema: z.object({}),
			execute: async () => {
				const savedFiles = await getChatFilesMetadata(context.db, context.uid, context.chatId);
				const seenIds = new Set<string>();
				const files: Array<{
					id: string;
					name: string;
					mimeType: string;
					size: number;
					url: string;
					createdAt: number;
				}> = [];
				for (const file of [...context.generatedFiles, ...context.uploadedFiles, ...savedFiles]) {
					if (seenIds.has(file.id)) {
						continue;
					}
					seenIds.add(file.id);
					files.push({
						id: file.id,
						name: file.name,
						mimeType: file.mimeType,
						size: file.size,
						url: fileUrlForId(file.id),
						createdAt: file.createdAt,
					});
				}
				return { files };
			},
		}),

		readChatFile: tool({
			description: "Read the text contents of a text-based file stored in this chat session.",
			inputSchema: z.object({
				fileId: z.string().min(1),
			}),
			execute: async ({ fileId }) => {
				const file =
					context.generatedFiles.find((entry) => entry.id === fileId) ??
					context.uploadedFiles.find((entry) => entry.id === fileId) ??
					(await getChatFileById(context.db, context.uid, context.chatId, fileId));
				if (!file) {
					return { found: false, error: "file not found" };
				}
				if (!isReadableTextMediaType(file.mimeType)) {
					return {
						found: true,
						id: file.id,
						name: file.name,
						mimeType: file.mimeType,
						size: file.size,
						error: "binary file cannot be decoded as UTF-8 text",
					};
				}
				const object = await context.filesBucket.get(file.r2Key, {
					range: { offset: 0, length: MAX_READABLE_TEXT_CHARS * 4 },
				});
				if (!object) {
					return { found: false, error: "file missing from storage" };
				}
				const decoded = await object.text();
				const truncated =
					decoded.length > MAX_READABLE_TEXT_CHARS || file.size > MAX_READABLE_TEXT_CHARS * 4;
				return {
					found: true,
					id: file.id,
					name: file.name,
					mimeType: file.mimeType,
					truncated,
					content:
						decoded.length > MAX_READABLE_TEXT_CHARS
							? decoded.slice(0, MAX_READABLE_TEXT_CHARS)
							: decoded,
				};
			},
		}),

		createTextFile: tool({
			description:
				"Create a downloadable text, markdown, JSON, CSV, or code file to include in the assistant response.",
			inputSchema: z.object({
				name: z.string().min(1),
				mimeType: z.string().min(1).default("text/plain"),
				content: z.string(),
			}),
			execute: async ({ name, mimeType, content }) => {
				if (context.generatedFiles.length >= MAX_GENERATED_FILES_PER_TURN) {
					return { created: false, error: "maximum generated file limit reached" };
				}
				const safeMimeType = sanitizeMimeType(mimeType, "text/plain");
				const encoded = new TextEncoder().encode(content);
				if (encoded.byteLength > MAX_GENERATED_FILE_BYTES) {
					return { created: false, error: "file exceeds maximum byte size" };
				}

				const generatedFile = await storeFileInR2({
					filesBucket: context.filesBucket,
					uid: context.uid,
					chatId: context.chatId,
					messageId: context.assistantMessageId,
					name,
					mimeType: safeMimeType,
					bytes: encoded,
				});
				context.generatedFiles.push(generatedFile);
				return {
					created: true,
					id: generatedFile.id,
					name: generatedFile.name,
					mimeType: generatedFile.mimeType,
					size: generatedFile.size,
					url: generatedFile.url,
				};
			},
		}),

		getCurrentTime: tool({
			description: "Get the current UTC date and timestamp.",
			inputSchema: z.object({}),
			execute: async () => {
				const now = Date.now();
				return {
					iso: new Date(now).toISOString(),
					timestampMs: now,
				};
			},
		}),
	};
}
