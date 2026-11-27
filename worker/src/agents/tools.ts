import { tool } from "ai";
import { z } from "zod";
import { getChatFilesMetadata, getFileById } from "../repositories/chatRepository.ts";

const MAX_GENERATED_FILES = 10;
const MAX_GENERATED_FILE_BYTES = 8 * 1024 * 1024;
const MAX_READABLE_TEXT_CHARS = 24_000;

const EXTENSION_BY_MIME_TYPE: Record<string, string> = {
	"text/plain": "txt",
	"text/markdown": "md",
	"application/json": "json",
	"image/svg+xml": "svg",
};

export interface StoredFile {
	id: string;
	name: string;
	mimeType: string;
	size: number;
	r2Key: string;
	createdAt: number;
}

function normalizeMediaType(mediaType: string): string {
	return mediaType.split(";")[0].trim().toLowerCase();
}

export function createChatTools(context: {
	db: D1Database;
	filesBucket: R2Bucket;
	uid: string;
	chatId: string;
	turnFiles: StoredFile[];
}) {
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
				const baseMimeType = normalizeMediaType(file.mimeType);
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
				if (context.turnFiles.length >= MAX_GENERATED_FILES) {
					return { created: false, error: "maximum generated file limit reached" };
				}
				const trimmedMimeType = mimeType.trim();
				const slashIndex = trimmedMimeType.indexOf("/");
				let validMimeType = slashIndex > 0 && slashIndex < trimmedMimeType.length - 1;
				for (let index = 0; validMimeType && index < trimmedMimeType.length; index += 1) {
					const code = trimmedMimeType.charCodeAt(index);
					if (code < 0x20 || code > 0x7e) {
						validMimeType = false;
					}
				}
				const safeMimeType = validMimeType ? trimmedMimeType : "text/plain";
				const encoded = new TextEncoder().encode(content);
				if (encoded.byteLength > MAX_GENERATED_FILE_BYTES) {
					return { created: false, error: "file exceeds maximum byte size" };
				}

				const baseMimeType = normalizeMediaType(safeMimeType);
				const extension =
					EXTENSION_BY_MIME_TYPE[baseMimeType] ??
					(baseMimeType.split("/")[1]?.split("+")[0] || "bin");
				const trimmedName = name.trim();
				const fileName = trimmedName.includes(".") ? trimmedName : `${trimmedName}.${extension}`;

				const fileId = crypto.randomUUID();
				const r2Key = `users/${context.uid}/chats/${context.chatId}/${fileId}`;
				await context.filesBucket.put(r2Key, encoded, {
					httpMetadata: { contentType: safeMimeType },
				});
				const generatedFile: StoredFile = {
					id: fileId,
					name: fileName,
					mimeType: safeMimeType,
					size: encoded.byteLength,
					r2Key,
					createdAt: Date.now(),
				};
				context.turnFiles.push(generatedFile);
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
