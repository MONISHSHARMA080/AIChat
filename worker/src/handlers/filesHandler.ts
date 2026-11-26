import { getFileById } from "../repositories/chatRepository.ts";
import type { FirebaseIdToken } from "../types/auth.ts";
import { AppError } from "../types/errors.ts";

export async function handleGetFileRequest(
	env: Env,
	user: FirebaseIdToken,
	fileId: string,
): Promise<Response> {
	const fileRow = await getFileById(env.DB, user.uid, fileId);
	if (!fileRow) {
		throw new AppError("not-found", "file not found");
	}

	const object = await env.FILES_BUCKET.get(fileRow.r2Key);
	if (!object) {
		throw new AppError("not-found", "file not found");
	}

	let asciiBuilder = "";
	let utf8Builder = "";
	for (let index = 0; index < fileRow.name.length; index += 1) {
		const code = fileRow.name.charCodeAt(index);
		const isPrintableAscii = code >= 0x20 && code <= 0x7e;
		const isAsciiForbidden = code === 0x22 || code === 0x5c || code === 0x2f || code === 0x3b;
		asciiBuilder += isPrintableAscii && !isAsciiForbidden ? fileRow.name[index] : "_";

		const isUtf8Stripped = code < 0x20 || code === 0x7f || code === 0x2f || code === 0x5c;
		if (!isUtf8Stripped) {
			utf8Builder += fileRow.name[index];
		}
	}

	const asciiName = asciiBuilder.trim().length > 0 ? asciiBuilder : "file";
	const utf8Source = utf8Builder.trim().length > 0 ? utf8Builder : "file";
	const utf8Encoded = encodeURIComponent(utf8Source)
		.replaceAll("'", "%27")
		.replaceAll("(", "%28")
		.replaceAll(")", "%29")
		.replaceAll("*", "%2A");
	const contentDisposition = `attachment; filename="${asciiName}"; filename*=UTF-8''${utf8Encoded}`;

	const headers = new Headers({
		"Content-Type": fileRow.mimeType,
		"Content-Disposition": contentDisposition,
		"Cache-Control": "private, no-store",
		"X-Content-Type-Options": "nosniff",
		"Content-Length": String(object.size),
		ETag: object.httpEtag,
	});

	return new Response(object.body, { status: 200, headers });
}
