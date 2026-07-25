import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { getErrorMessage } from '../api/errors';

/**
 * GET /api/seatmap for a date (defaults to the office's "today" when
 * omitted — the client never supplies its own notion of today for this
 * call, per PLAN.md §4's "never trust the client clock").
 */
export function useSeatMap(date?: string) {
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
  });
}
