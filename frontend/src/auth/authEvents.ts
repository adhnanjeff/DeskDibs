/**
 * A tiny event bus so the API client (which has no access to React context
 * or the router) can announce "the server just told us this session is
 * dead" and the AuthProvider (which has both) can react to it — clearing
 * state and redirecting to /login.
 */
export const authEvents = new EventTarget();

export const UNAUTHORIZED_EVENT = 'deskdibs:unauthorized';

export function emitUnauthorized(): void {
  authEvents.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
}
