/**
 * The real DeskDibs error body (see docs/postman/README.md §6): every 4xx/5xx
 * response from the backend's single @RestControllerAdvice shares this
 * shape. The OpenAPI contract types some error responses loosely (springdoc
 * reuses the success schema on a few endpoints), so we validate the shape at
 * runtime instead of trusting the generated type for error branches.
 */
export interface ApiErrorBody {
  code: string;
  message: string;
  path?: string;
  timestamp?: string;
  details?: Record<string, unknown>;
}

export function isApiErrorBody(value: unknown): value is ApiErrorBody {
  if (typeof value !== 'object' || value === null) return false;
  const record = value as Record<string, unknown>;
  return typeof record.code === 'string' && typeof record.message === 'string';
}

const FALLBACK_MESSAGE = 'Something went wrong. Please try again.';

export function getErrorMessage(
  error: unknown,
  fallback: string = FALLBACK_MESSAGE,
): string {
  if (isApiErrorBody(error)) return error.message;
  return fallback;
}
