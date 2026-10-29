import { HttpsError, onCall } from "firebase-functions/v2/https";
import { requireAuth } from "../auth";
import { REVENUECAT_SECRET_API_KEY } from "../config";
import { getBalances } from "../revenuecat";

interface VirtualCurrencyBalancePayload {
  code: string;
  name: string;
  balance: number;
}

export const getUsage = onCall(
  { secrets: [REVENUECAT_SECRET_API_KEY] },
  async (request) => {
    const uid = requireAuth(request);
    try {
      const snapshot = await getBalances(uid);
      if (!snapshot) {
        return { synced: false, balance: 0, balances: [] as VirtualCurrencyBalancePayload[] };
      }
      return {
        synced: true,
        balance: snapshot.coin?.balance ?? 0,
        balances: snapshot.balances.map((entry) => ({
          code: entry.code,
          name: entry.name,
          balance: entry.balance,
        })),
      };
    } catch (error) {
      console.error("getUsage failed", error);
      throw new HttpsError("unavailable", "Balance is temporarily unavailable.");
    }
  },
);
