import { defineSecret, defineString } from "firebase-functions/params";

export const AI_GATEWAY_API_KEY = defineSecret("AI_GATEWAY_API_KEY");

export const LOGO_BASE_URL = defineString("LOGO_BASE_URL", {
  default: "",
});

export const REVENUECAT_SECRET_API_KEY = defineSecret("REVENUECAT_SECRET_API_KEY");

export const REVENUECAT_PROJECT_ID = defineString("REVENUECAT_PROJECT_ID", {
  default: "",
});

export const REVENUECAT_COIN_CODE = defineString("REVENUECAT_COIN_CODE", {
  default: "COIN",
});

export const TURSO_DATABASE_URL = defineSecret("TURSO_DATABASE_URL");

export const TURSO_AUTH_TOKEN = defineSecret("TURSO_AUTH_TOKEN");
