import { lazy } from 'react';

/**
 * three.js + fiber + drei are by far the heaviest thing this app ships, and most
 * sessions never open the 3D view — so the scene is its own chunk, split out of
 * the main bundle.
 *
 * Splitting alone would just move the wait to the click, so the chunk is also
 * *prefetched the moment a seat is picked* — the only way to reach the 3D view.
 * The download then overlaps the seconds the user spends reading the sidebar,
 * and the click opens an already-parsed module. The browser dedupes: calling
 * `preloadFloor3D` repeatedly costs one request.
 */
const importScene = () => import('./Floor3DScene');

export const LazyFloor3DScene = lazy(() =>
  importScene().then((m) => ({ default: m.Floor3DScene })),
);

/** Warm the chunk ahead of the click. Safe to call often; failures are ignored. */
export function preloadFloor3D(): void {
  void importScene().catch(() => {
    // A failed prefetch is not an error the user should see — if they actually
    // open the view, Suspense retries and the error boundary handles it there.
  });
}
