import { tool } from "ai";
import { z } from "zod";
import {
	buildFileR2Key,
	extensionForMediaType,
	MAX_GENERATED_FILE_BYTES,
	MAX_GENERATED_FILES,
} from "../config/constants.ts";
import { getChatFilesMetadata, getFileById } from "../repositories/chatRepository.ts";
import type { ChatToolContext, StoredFile } from "../types/chat.ts";

const MAX_READABLE_TEXT_CHARS = 24_000;

export function createChatTools(context: ChatToolContext) {
	return {
		listChatFiles: tool({
			description: "List files previously generated in this chat session.",
			inputSchema: z.object({}),
			execute: async () => {
				const savedFiles = await getChatFilesMetadata(context.db, context.uid, context.chatId);
				const files = [...context.turnFiles, ...savedFiles];
				return {
					files: files.map(({ id, name, mimeType, size, createdAt }) => ({
						id,
						name,
						mimeType,
						size,
						createdAt,
					})),
				};
			},
		}),

		readChatFile: tool({
			description: "Read the text contents of a text-based file stored in this chat session.",
			inputSchema: z.object({
				fileId: z.string().min(1),
			}),
			execute: async ({ fileId }) => {
				const file =
					context.turnFiles.find((entry) => entry.id === fileId) ??
					(await getFileById(context.db, context.uid, fileId));
				if (!file) {
					return { found: false, error: "file not found" };
				}
				const baseMimeType = file.mimeType.split(";")[0].trim().toLowerCase();
				const readable =
					baseMimeType.startsWith("text/") ||
					baseMimeType === "application/json" ||
					baseMimeType === "application/xml" ||
					baseMimeType === "application/javascript" ||
					baseMimeType === "image/svg+xml";
				if (!readable) {
					return {
						found: true,
						id: file.id,
						name: file.name,
						mimeType: file.mimeType,
						size: file.size,
						error: "binary file cannot be decoded as UTF-8 text",
					};
				}
				if (!file.r2Key) {
					return { found: false, error: "file missing from storage" };
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
				if (context.generatedFilesCount() >= MAX_GENERATED_FILES) {
					return { created: false, error: "maximum generated file limit reached" };
				}
				const encoded = new TextEncoder().encode(content);
				if (encoded.byteLength > MAX_GENERATED_FILE_BYTES) {
					return { created: false, error: "file exceeds maximum byte size" };
				}
				const trimmedName = name.trim();
				const fileName = trimmedName.includes(".")
					? trimmedName
					: `${trimmedName}.${extensionForMediaType(mimeType)}`;
				const fileId = crypto.randomUUID();
				const r2Key = buildFileR2Key(context.uid, context.chatId, fileId, fileName);
				await context.filesBucket.put(r2Key, encoded, {
					httpMetadata: { contentType: mimeType },
				});
				const generatedFile: StoredFile = {
					id: fileId,
					name: fileName,
					mimeType,
					size: encoded.byteLength,
					r2Key,
					createdAt: Date.now(),
				};
				context.onGeneratedFile(generatedFile);
				return {
					created: true,
					id: generatedFile.id,
					name: generatedFile.name,
					mimeType: generatedFile.mimeType,
					size: generatedFile.size,
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
