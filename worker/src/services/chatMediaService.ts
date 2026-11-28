import { Buffer } from "node:buffer";
import { isFileUIPart, type UIMessage, type UIMessageChunk } from "ai";
import { AppError } from "../errors.ts";
import { getChatFileById } from "../repositories/chatRepository.ts";
import type { StoredFile } from "../types/chat.ts";

export const MAX_USER_TEXT_CHARS = 32_000;
export const MAX_USER_ATTACHMENTS_PER_MESSAGE = 5;
export const MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;
export const MAX_GENERATED_FILES_PER_TURN = 10;
export const MAX_GENERATED_FILE_BYTES = 10 * 1024 * 1024;
export const MAX_READABLE_TEXT_CHARS = 24_000;
export const MAX_CONTEXT_MEDIA_TURNS = 2;
export const MAX_CONTEXT_MEDIA_FILES = 4;

const EXTENSION_BY_MIME_TYPE: Record<string, string> = {
	"text/plain": "txt",
	"text/markdown": "md",
	"application/json": "json",
	"application/pdf": "pdf",
	"image/jpeg": "jpg",
	"image/png": "png",
	"image/webp": "webp",
	"image/gif": "gif",
	"image/svg+xml": "svg",
};

const FILE_URL_PREFIX = "/files/";
const DATA_URL_PREFIX = "data:";
const BASE64_SUFFIX = ";base64";

export function normalizeMediaType(mediaType: string): string {
	return mediaType.split(";")[0].trim().toLowerCase();
}

export function sanitizeMimeType(mimeType: string, fallback = "application/octet-stream"): string {
	const trimmed = mimeType.trim();
	const slashIndex = trimmed.indexOf("/");
	if (slashIndex <= 0 || slashIndex >= trimmed.length - 1) {
		return fallback;
	}
	for (let index = 0; index < trimmed.length; index += 1) {
		const code = trimmed.charCodeAt(index);
		if (code < 0x20 || code > 0x7e) {
			return fallback;
		}
	}
	return trimmed;
}

export function isReadableTextMediaType(mediaType: string): boolean {
	const baseMimeType = normalizeMediaType(mediaType);
	return (
		baseMimeType.startsWith("text/") ||
		baseMimeType === "application/json" ||
		baseMimeType === "application/xml" ||
		baseMimeType === "application/javascript" ||
		baseMimeType === "image/svg+xml"
	);
}

export function buildSafeFileName(name: string, mimeType: string): string {
	const trimmedName = name.trim() || "file";
	if (trimmedName.includes(".")) {
		return trimmedName;
	}
	const baseMimeType = normalizeMediaType(mimeType);
	const extension =
		EXTENSION_BY_MIME_TYPE[baseMimeType] ?? (baseMimeType.split("/")[1]?.split("+")[0] || "bin");
	return `${trimmedName}.${extension}`;
}

export function fileUrlForId(fileId: string): string {
	return `${FILE_URL_PREFIX}${fileId}`;
}

export function extractFileIdFromUrl(url: string): string | null {
	if (!url.startsWith(FILE_URL_PREFIX)) {
		return null;
	}
	const fileId = url.slice(FILE_URL_PREFIX.length);
	if (fileId.length === 0 || fileId.includes("/") || fileId.includes("?") || fileId.includes("#")) {
		return null;
	}
	return fileId;
}

function decodeBase64ToBytes(encoded: string, maxBytes: number): Uint8Array {
	const trimmed = encoded.trim();
	if (trimmed.length === 0) {
		throw new AppError("invalid-argument", "empty attachment payload");
	}
	const maxEncodedChars = Math.ceil((maxBytes * 4) / 3) + Math.ceil(maxBytes / 32) + 4;
	if (trimmed.length > maxEncodedChars) {
		throw new AppError("invalid-argument", "file exceeds maximum byte size");
	}
	const bytes = Buffer.from(trimmed, "base64");
	if (bytes.byteLength === 0) {
		throw new AppError("invalid-argument", "invalid base64 payload");
	}
	if (bytes.byteLength > maxBytes) {
		throw new AppError("invalid-argument", "file exceeds maximum byte size");
	}
	return new Uint8Array(bytes.buffer, bytes.byteOffset, bytes.byteLength);
}

function parseDataUrl(
	dataUrl: string,
	maxBytes: number,
): { mimeType: string; bytes: Uint8Array } | null {
	if (!dataUrl.startsWith(DATA_URL_PREFIX)) {
		return null;
	}
	const commaIndex = dataUrl.indexOf(",");
	if (commaIndex === -1) {
		return null;
	}
	const header = dataUrl.slice(DATA_URL_PREFIX.length, commaIndex);
	if (!header.toLowerCase().endsWith(BASE64_SUFFIX)) {
		return null;
	}
	const rawMimeType = header.slice(0, header.length - BASE64_SUFFIX.length);
	const mimeType = sanitizeMimeType(rawMimeType);
	const payload = dataUrl.slice(commaIndex + 1);
	const bytes = decodeBase64ToBytes(payload, maxBytes);
	return { mimeType, bytes };
}

function toDataUrl(mimeType: string, bytes: Uint8Array): string {
	return `data:${mimeType};base64,${Buffer.from(bytes.buffer, bytes.byteOffset, bytes.byteLength).toString("base64")}`;
}

export async function storeFileInR2(input: {
	filesBucket: R2Bucket;
	uid: string;
	chatId: string;
	messageId: string | null;
	name: string;
	mimeType: string;
	bytes: Uint8Array;
}): Promise<StoredFile> {
	const safeMimeType = sanitizeMimeType(input.mimeType);
	const safeName = buildSafeFileName(input.name, safeMimeType);
	const fileId = crypto.randomUUID();
	const r2Key = `users/${input.uid}/chats/${input.chatId}/${fileId}`;
	await input.filesBucket.put(r2Key, input.bytes, {
		httpMetadata: { contentType: safeMimeType },
	});
	return {
		id: fileId,
		messageId: input.messageId,
		chatId: input.chatId,
		userId: input.uid,
		name: safeName,
		mimeType: safeMimeType,
		size: input.bytes.byteLength,
		r2Key,
		url: fileUrlForId(fileId),
		createdAt: Date.now(),
	};
}

async function loadFileBytesFromR2(
	filesBucket: R2Bucket,
	r2Key: string,
	maxBytes?: number,
): Promise<Uint8Array | null> {
	const object = await filesBucket.get(r2Key);
	if (!object) {
		return null;
	}
	if (maxBytes !== undefined && object.size > maxBytes) {
		await object.body.cancel();
		return null;
	}
	return new Uint8Array(await object.arrayBuffer());
}

export async function deleteFilesFromR2(
	filesBucket: R2Bucket,
	files: readonly StoredFile[],
): Promise<void> {
	if (files.length === 0) {
		return;
	}
	try {
		await filesBucket.delete(files.map((file) => file.r2Key));
	} catch (error) {
		console.error(
			JSON.stringify({
				event: "r2_cleanup_failed",
				count: files.length,
				error: error instanceof Error ? error.message : String(error),
			}),
		);
	}
}

export async function offloadInlineUserFiles(
	message: UIMessage,
	context: {
		filesBucket: R2Bucket;
		uid: string;
		chatId: string;
	},
): Promise<{
	uploadedFiles: StoredFile[];
	uploadedBytesById: Map<string, { mimeType: string; bytes: Uint8Array }>;
	filesByPartIndex: Map<number, StoredFile>;
}> {
	const uploadedFiles: StoredFile[] = [];
	const uploadedBytesById = new Map<string, { mimeType: string; bytes: Uint8Array }>();
	const filesByPartIndex = new Map<number, StoredFile>();
	let inlineFileCount = 0;

	try {
		for (let partIndex = 0; partIndex < message.parts.length; partIndex += 1) {
			const part = message.parts[partIndex];
			if (!isFileUIPart(part) || !part.url.startsWith(DATA_URL_PREFIX)) {
				continue;
			}
			inlineFileCount += 1;
			if (inlineFileCount > MAX_USER_ATTACHMENTS_PER_MESSAGE) {
				throw new AppError("invalid-argument", "too many attachments");
			}
			const parsed = parseDataUrl(part.url, MAX_ATTACHMENT_BYTES);
			if (!parsed) {
				throw new AppError("invalid-argument", "invalid file data url");
			}
			const mimeType = sanitizeMimeType(part.mediaType, parsed.mimeType);
			const stored = await storeFileInR2({
				filesBucket: context.filesBucket,
				uid: context.uid,
				chatId: context.chatId,
				messageId: message.id,
				name: part.filename ?? `attachment-${inlineFileCount}`,
				mimeType,
				bytes: parsed.bytes,
			});
			uploadedFiles.push(stored);
			uploadedBytesById.set(stored.id, { mimeType: stored.mimeType, bytes: parsed.bytes });
			filesByPartIndex.set(partIndex, stored);
		}
		return { uploadedFiles, uploadedBytesById, filesByPartIndex };
	} catch (error) {
		await deleteFilesFromR2(context.filesBucket, uploadedFiles);
		throw error;
	}
}

export function rewriteMessageFileUrls(
	messages: readonly UIMessage[],
	messageId: string,
	filesByPartIndex: ReadonlyMap<number, StoredFile>,
): UIMessage[] {
	if (filesByPartIndex.size === 0) {
		return [...messages];
	}
	return messages.map((message) => {
		if (message.id !== messageId) {
			return message;
		}
		return {
			...message,
			parts: message.parts.map((part, index) => {
				const stored = filesByPartIndex.get(index);
				if (!stored || !isFileUIPart(part)) {
					return part;
				}
				return {
					...part,
					filename: stored.name,
					mediaType: stored.mimeType,
					url: stored.url,
				};
			}),
		};
	});
}

export async function hydrateModelMessages(
	messages: readonly UIMessage[],
	context: {
		db: D1Database;
		filesBucket: R2Bucket;
		uid: string;
		chatId: string;
		uploadedBytesById: ReadonlyMap<string, { mimeType: string; bytes: Uint8Array }>;
	},
): Promise<UIMessage[]> {
	const mediaWindowStart = Math.max(0, messages.length - (MAX_CONTEXT_MEDIA_TURNS * 2 + 1));
	let remainingMediaBudget = MAX_CONTEXT_MEDIA_FILES;
	const prepared: UIMessage[] = [];

	for (let messageIndex = messages.length - 1; messageIndex >= 0; messageIndex -= 1) {
		const message = messages[messageIndex];
		const inMediaWindow = messageIndex >= mediaWindowStart;
		const parts: UIMessage["parts"] = [];

		for (let partIndex = message.parts.length - 1; partIndex >= 0; partIndex -= 1) {
			const part = message.parts[partIndex];
			if (!isFileUIPart(part)) {
				parts.push(part);
				continue;
			}

			const fileId = extractFileIdFromUrl(part.url);
			if (!fileId) {
				if (part.url.startsWith(DATA_URL_PREFIX)) {
					if (inMediaWindow && remainingMediaBudget > 0) {
						remainingMediaBudget -= 1;
						parts.push(part);
					} else {
						parts.push({
							type: "text",
							text: `[File: ${part.filename ?? "attachment"} (${part.mediaType})]`,
						});
					}
				} else {
					parts.push(part);
				}
				continue;
			}

			if (inMediaWindow && remainingMediaBudget > 0) {
				const uploaded = context.uploadedBytesById.get(fileId);
				if (uploaded) {
					remainingMediaBudget -= 1;
					parts.push({
						...part,
						mediaType: uploaded.mimeType,
						url: toDataUrl(uploaded.mimeType, uploaded.bytes),
					});
					continue;
				}

				const fileRow = await getChatFileById(context.db, context.uid, context.chatId, fileId);
				if (fileRow) {
					const bytes = await loadFileBytesFromR2(
						context.filesBucket,
						fileRow.r2Key,
						MAX_ATTACHMENT_BYTES,
					);
					if (bytes) {
						remainingMediaBudget -= 1;
						parts.push({
							...part,
							mediaType: fileRow.mimeType,
							url: toDataUrl(fileRow.mimeType, bytes),
						});
						continue;
					}
				}
			}

			parts.push({
				type: "text",
				text: `[File: ${part.filename ?? fileId} (${part.mediaType}), id: ${fileId}]`,
			});
		}

		parts.reverse();
		prepared.push({ ...message, parts });
	}

	prepared.reverse();
	return prepared;
}

export function createGeneratedFileOffloadStream(context: {
	filesBucket: R2Bucket;
	uid: string;
	chatId: string;
	generatedFiles: StoredFile[];
}): TransformStream<UIMessageChunk, UIMessageChunk> {
	return new TransformStream<UIMessageChunk, UIMessageChunk>({
		transform: async (chunk, controller) => {
			if (chunk.type === "reasoning-file" && chunk.url.startsWith(DATA_URL_PREFIX)) {
				return;
			}
			if (chunk.type === "file" && chunk.url.startsWith(DATA_URL_PREFIX)) {
				if (context.generatedFiles.length >= MAX_GENERATED_FILES_PER_TURN) {
					console.warn(
						JSON.stringify({
							event: "generated_file_limit_reached",
							uid: context.uid,
							chatId: context.chatId,
						}),
					);
					return;
				}
				try {
					const parsed = parseDataUrl(chunk.url, MAX_GENERATED_FILE_BYTES);
					if (!parsed) {
						return;
					}
					const stored = await storeFileInR2({
						filesBucket: context.filesBucket,
						uid: context.uid,
						chatId: context.chatId,
						messageId: null,
						name: `generated-${context.generatedFiles.length + 1}`,
						mimeType: parsed.mimeType,
						bytes: parsed.bytes,
					});
					context.generatedFiles.push(stored);
					controller.enqueue({
						...chunk,
						mediaType: stored.mimeType,
						url: stored.url,
					});
				} catch (error) {
					console.warn(
						JSON.stringify({
							event: "generated_file_offload_failed",
							uid: context.uid,
							chatId: context.chatId,
							error: error instanceof Error ? error.message : String(error),
						}),
					);
				}
				return;
			}
			controller.enqueue(chunk);
		},
	});
}
