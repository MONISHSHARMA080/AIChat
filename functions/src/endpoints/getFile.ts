import { HttpsError, onCall } from "firebase-functions/https";
import { logger } from "firebase-functions";
import { z } from "zod";
import { TURSO_AUTH_TOKEN, TURSO_DATABASE_URL } from "../config";
import { getFileRow } from "../db/turso";

const bodySchema = z.object({ fileId: z.string().min(1) });

export const getFile = onCall(
  { secrets: [TURSO_AUTH_TOKEN, TURSO_DATABASE_URL] },
  async (request) => {
    if (!request.auth) throw new HttpsError("unauthenticated", "sign in required");
    const parsed = bodySchema.safeParse(request.data);
    if (!parsed.success) throw new HttpsError("invalid-argument", "fileId required");

    const file = await getFileRow(request.auth.uid, parsed.data.fileId).catch((error) => {
      logger.error("file lookup failed", error);
      throw new HttpsError("unavailable", "file lookup failed");
    });
    if (!file) throw new HttpsError("not-found", "file not found");

    return { id: file.id, name: file.name, mimeType: file.mimeType, size: file.size, data: file.data };
  },
);
