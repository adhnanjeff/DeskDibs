import createClient from 'openapi-fetch';
import type { paths } from './schema';
import { loadSession, clearSession } from '../auth/tokenStorage';
import { emitUnauthorized } from '../auth/authEvents';

/** Defaults to the local backend; override per environment via .env. */
export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

const LOGIN_SCHEMA_PATH = '/api/auth/login';

export const apiClient = createClient<paths>({
  baseUrl: API_BASE_URL,
  // openapi-fetch resolves its `fetch` option once, as a default parameter,
  // at client-creation time. Wrapping it in a closure makes every call read
  // `globalThis.fetch` fresh instead — otherwise a fetch-patching test
  // library (MSW) that installs its patch after this module has already
  // been evaluated would silently be bypassed, and requests would hit the
  // real network instead of the mock.
  fetch: (...args: Parameters<typeof fetch>) => globalThis.fetch(...args),
});

apiClient.use({
  onRequest({ request }) {
    const session = loadSession();
    if (session?.accessToken) {
      request.headers.set('Authorization', `Bearer ${session.accessToken}`);
    }
    return request;
  },
  onResponse({ schemaPath, response }) {
    // A 401 on the login call itself just means "wrong credentials" — there
    // is no session yet to clear, and treating it as a dead session would
    // redirect straight back to the page the user is already on.
    if (response.status === 401 && schemaPath !== LOGIN_SCHEMA_PATH) {
      const hadSession = loadSession() !== null;
      clearSession();
      if (hadSession) {
        emitUnauthorized();
      }
    }
    return response;
  },
});
