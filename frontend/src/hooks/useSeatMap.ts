import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { getErrorMessage } from '../api/errors';

/** How often to re-fetch the map when live updates are not arriving (PLAN.md §6). */
export const POLL_INTERVAL_MS = 15_000;

interface SeatMapOptions {
  /**
   * Whether live broadcasts are arriving. When they are not, the query falls back to a poll —
   * slower, never wrong (PLAN.md §5 #9). A map that has silently stopped updating is the worst
   * failure mode here: it is indistinguishable from an office where nobody is booking anything.
   */
  live?: boolean;
}

/**
 * GET /api/seatmap for a date (defaults to the office's "today" when
 * omitted — the client never supplies its own notion of today for this
 * call, per PLAN.md §4's "never trust the client clock").
 */
export function useSeatMap(date?: string, options: SeatMapOptions = {}) {
  const live = options.live ?? true;

  return useQuery({
    queryKey: ['seatmap', date ?? 'today'],
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/api/seatmap', {
        params: { query: date ? { date } : {} },
      });
      if (error || !data) {
        throw new Error(getErrorMessage(error, 'Could not load the seat map.'));
      }
      return data;
    },
    refetchInterval: live ? false : POLL_INTERVAL_MS,
    // Keep polling a backgrounded tab that has lost its socket: somebody returning to it should
    // find the current floor, not a snapshot from whenever they last looked.
    refetchIntervalInBackground: !live,
  });
}
