import { bindings, defineConfig, defineWorker, exports } from "cf/config";
import * as entrypoint from "./src/index.ts" with { type: "cf-worker" };

const chatWorkerBase = defineWorker({
	name: "aichat-worker",
	compatibilityDate: "2026-11-23",
	compatibilityFlags: ["nodejs_compat"],
	entrypoint,
	observability: {
		enabled: true,
		traces: {
			enabled: true,
		},
	},
	exports: {
		ChatAgent: exports.durableObject({ storage: "sqlite" }),
	},
});

export default defineConfig({
	worker: {
		...chatWorkerBase,
		env: {
			DB: bindings.d1({ name: "aichat-db" }),
			FILES_BUCKET: bindings.r2({ name: "aichat-files" }),
			CHAT_AGENT: bindings.durableObject({
				worker: chatWorkerBase,
				exportName: "ChatAgent",
			}),
			FIREBASE_PROJECT_ID: bindings.text("aichat-8d4f3"),
			REVENUECAT_PROJECT_ID: bindings.text("proj_replace_me"),
			REVENUECAT_COIN_CODE: bindings.text("COIN"),
			USD_PER_COIN: bindings.text("0.001"),
			MODEL_CACHE_TTL_HOURS: bindings.text("6"),
			LOGO_BASE_URL: bindings.text(""),
			AI_GATEWAY_API_KEY: bindings.secret(),
			REVENUECAT_SECRET_API_KEY: bindings.secret(),
		},
	},
});
