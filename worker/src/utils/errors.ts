import { AppError } from "../types/errors.ts";

export async function resolveOrThrowUnavailable<T>(
	operation: Promise<T>,
	unavailableMessage: string,
): Promise<T> {
	return operation.catch((error) => {
		if (error instanceof AppError) {
			throw error;
		}
		console.error(
			JSON.stringify({
				event: "dependency_unavailable",
				message: unavailableMessage,
				error: error instanceof Error ? error.message : String(error),
			}),
		);
		throw new AppError("unavailable", unavailableMessage);
	});
}
