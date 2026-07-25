import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { getErrorMessage } from '../api/errors';

/**
 * The two things you can do to a booking you already hold.
 *
 * <p>Check-in matters more than it looks: the office releases un-checked-in seats at the
 * no-show cut-off, so without a way to say "I'm here" every booking would be handed back to
 * the pool at 11:00. Cancelling is the polite version of the same outcome — it frees the seat
 * immediately instead of at the cut-off.
 *
 * Both invalidate the seat map as well as the bookings list, because either one changes what
 * the floor shows for that seat.
 */
function useInvalidateBookingViews() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['bookings'] });
    void queryClient.invalidateQueries({ queryKey: ['seatmap'] });
  };
}

/** POST /api/bookings/{id}/check-in — records that you turned up, before the cut-off. */
export function useCheckIn() {
  const invalidate = useInvalidateBookingViews();

  return useMutation({
    mutationFn: async (bookingId: number) => {
      const { data, error } = await apiClient.POST('/api/bookings/{id}/check-in', {
        params: { path: { id: bookingId } },
      });
      if (error || !data) {
        throw new Error(getErrorMessage(error, 'Could not check you in.'));
      }
      return data;
    },
    onSettled: invalidate,
  });
}

/** DELETE /api/bookings/{id} — gives the seat back so somebody else can take it. */
export function useCancelBooking() {
  const invalidate = useInvalidateBookingViews();

  return useMutation({
    mutationFn: async (bookingId: number) => {
      const { error } = await apiClient.DELETE('/api/bookings/{id}', {
        params: { path: { id: bookingId } },
      });
      if (error) {
        throw new Error(getErrorMessage(error, 'Could not cancel that booking.'));
      }
      return bookingId;
    },
    onSettled: invalidate,
  });
}
