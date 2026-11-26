export type ErrorCode =
	| "unauthenticated"
	| "invalid-argument"
	| "not-found"
	| "resource-exhausted"
	| "unavailable"
	| "internal";

const STATUS_BY_CODE: Record<ErrorCode, number> = {
	unauthenticated: 401,
	"invalid-argument": 400,
	"not-found": 404,
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
