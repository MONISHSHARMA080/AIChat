import { z } from "zod";

export const coinBalancesSchema = z.object({
	items: z.array(
		z.object({
			currency_code: z.string(),
			balance: z.number(),
		}),
	),
});

export const gatewayCostSchema = z.object({
	gateway: z.object({
		cost: z.union([z.string(), z.number()]).transform(Number).pipe(z.number().nonnegative()),
	}),
});

export const billingSchema = z.object({
	charged: z.number(),
	balance: z.number(),
});

export type BillingSummary = z.infer<typeof billingSchema>;
