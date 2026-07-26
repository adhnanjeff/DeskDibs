import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { getErrorMessage } from '../api/errors';

/**
 * The horizon's cache key.
 *
 * <p>Deliberately NOT nested under `['seatmap', …]`. The live-update hook folds each broadcast into
 * every query matching the `['seatmap']` prefix, and this endpoint returns an array of days rather
 * than a floor map — so sharing the prefix meant a single websocket message rewrote the strip's
 * data into the wrong shape and crashed it. Different resource, different key.
 */
export const SEAT_HORIZON_KEY = ['seat-horizon'] as const;

/** How often to re-read the horizon, which is also how the app notices the date rolling over. */
const REFRESH_INTERVAL_MS = 60_000;

/**
 * GET /api/seatmap/horizon — how full each bookable day is, for the date strip.
 *
 * <p>The whole horizon in one request. Fetching a seat map per day would be fourteen round trips
 * to render a row of bars, and would make the strip slower than the map it sits above.
 *
 * <p>Refreshed on a slow interval even in a background tab: its first entry is the office's own
 * "today", so re-reading it is how a tab left open overnight discovers that the day has changed
 * (PLAN.md §5 #10).
 */
export function useSeatMapHorizon() {
  return useQuery({
    queryKey: SEAT_HORIZON_KEY,
    refetchInterval: REFRESH_INTERVAL_MS,
    refetchIntervalInBackground: true,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/api/seatmap/horizon');
      if (error || !data) {
        throw new Error(getErrorMessage(error, 'Could not load the next two weeks.'));
      }
      return data;
    },
  });
}
