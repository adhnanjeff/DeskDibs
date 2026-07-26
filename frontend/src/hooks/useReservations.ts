import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { getErrorMessage } from '../api/errors';
import type { components } from '../api/schema';
import { SEAT_HORIZON_KEY } from './useSeatMapHorizon';

type CreateReservationRequest = components['schemas']['CreateReservationRequest'];

/** GET /api/reservations/teams — the teams this manager may hold seats for. */
export function useReservationTeams() {
  return useQuery({
    queryKey: ['reservations', 'teams'],
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/api/reservations/teams');
      if (error || !data) {
        throw new Error(getErrorMessage(error, 'Could not load your teams.'));
      }
      return data;
    },
  });
}

/** GET /api/reservations — live and upcoming holds this caller can release. */
export function useReservations() {
  return useQuery({
    queryKey: ['reservations', 'list'],
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/api/reservations');
      if (error || !data) {
        throw new Error(getErrorMessage(error, 'Could not load team holds.'));
      }
      return data;
    },
  });
}

function useInvalidateReservations() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['reservations'] });
    // Holding or releasing a desk changes what the floor shows for it.
    void queryClient.invalidateQueries({ queryKey: ['seatmap'] });
    void queryClient.invalidateQueries({ queryKey: SEAT_HORIZON_KEY });
  };
}

/**
 * POST /api/reservations.
 *
 * A partial success is a 200, not an error: seats somebody already booked come back in the
 * report's `unavailable` list rather than failing the request, because the system never
 * force-cancels a booking to make room for a hold.
 */
export function useCreateReservation() {
  const invalidate = useInvalidateReservations();

  return useMutation({
    mutationFn: async (request: CreateReservationRequest) => {
      const { data, error } = await apiClient.POST('/api/reservations', { body: request });
      if (error || !data) {
        throw new Error(getErrorMessage(error, 'Could not hold those seats.'));
      }
      return data;
    },
    onSettled: invalidate,
  });
}

/** DELETE /api/reservations/{id} — release a hold early. */
export function useReleaseReservation() {
  const invalidate = useInvalidateReservations();

  return useMutation({
    mutationFn: async (reservationId: number) => {
      const { error } = await apiClient.DELETE('/api/reservations/{id}', {
        params: { path: { id: reservationId } },
      });
      if (error) {
        throw new Error(getErrorMessage(error, 'Could not release that hold.'));
      }
      return reservationId;
    },
    onSettled: invalidate,
  });
}
