import type { z } from "zod";
import type { modelCatalogConfigSchema, runtimeConfigSchema } from "../config/constants.ts";

export type ModelCatalogConfig = z.infer<typeof modelCatalogConfigSchema>;
export type RuntimeConfig = z.infer<typeof runtimeConfigSchema>;
