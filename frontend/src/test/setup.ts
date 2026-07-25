import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { server } from './mocks/server';

/**
 * jsdom still ships no ResizeObserver, but the floor map's pan/zoom needs one to
 * re-fit the canvas when its container changes size. Without this stub the map
 * throws on mount and every test sees the error boundary instead of the office.
 * A no-op is enough: nothing in jsdom ever actually resizes.
 */
if (!('ResizeObserver' in globalThis)) {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => {
  server.resetHandlers();
  sessionStorage.clear();
});
afterAll(() => server.close());
