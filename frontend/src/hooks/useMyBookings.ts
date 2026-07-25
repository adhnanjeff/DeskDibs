import { useQuery } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { getErrorMessage } from '../api/errors';

/** GET /api/bookings/me — defaults to today through the booking horizon. */
export function useMyBookings() {
  return useQuery({
    queryKey: ['bookings', 'me'],
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/api/bookings/me', {
        params: { query: {} },
      });
      if (error || !data) {
        throw new Error(
          getErrorMessage(error, 'Could not load your bookings.'),
        );
      }
      return data;
    },
  });
}
