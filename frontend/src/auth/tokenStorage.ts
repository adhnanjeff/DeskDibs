import type { components } from '../api/schema';

export type CurrentUser = components['schemas']['CurrentUserResponse'];

export interface StoredSession {
  accessToken: string;
  user: CurrentUser;
}

const STORAGE_KEY = 'deskdibs.session';

/**
 * The access token lives in sessionStorage, not localStorage: it clears the
 * instant the tab closes, which shrinks the window an XSS payload could
 * exfiltrate a live token. This is a deliberate interim choice — the real
 * fix is an httpOnly cookie + refresh-token flow the server sets, so the
 * token is never reachable from JavaScript at all. That flow is future
 * work; it is not implemented in Phase 5, and the backend does not yet
 * expose a refresh endpoint to build it against.
 */
export function loadSession(): StoredSession | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (
      typeof parsed === 'object' &&
      parsed !== null &&
      'accessToken' in parsed &&
      'user' in parsed &&
      typeof (parsed as Record<string, unknown>).accessToken === 'string'
    ) {
      return parsed as StoredSession;
    }
    return null;
  } catch {
    return null;
  }
}

export function saveSession(session: StoredSession): void {
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  sessionStorage.removeItem(STORAGE_KEY);
}
