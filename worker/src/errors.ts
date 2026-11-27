type ErrorCode =
	| "unauthenticated"
	| "invalid-argument"
	| "not-found"
	| "conflict"
	| "resource-exhausted"
	| "unavailable"
	| "internal";

const STATUS_BY_CODE: Record<ErrorCode, number> = {
	unauthenticated: 401,
	"invalid-argument": 400,
	"not-found": 404,
	conflict: 409,
	"resource-exhausted": 402,
	unavailable: 503,
	internal: 500,
};

export class AppError extends Error {
	readonly code: ErrorCode;
	readonly status: number;

	constructor(code: ErrorCode, message: string) {
		super(message);
		this.name = "AppError";
		this.code = code;
		this.status = STATUS_BY_CODE[code];
	}
}

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
