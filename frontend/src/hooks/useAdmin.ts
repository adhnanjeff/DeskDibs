import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { getErrorMessage } from '../api/errors';
import { SEAT_HORIZON_KEY } from './useSeatMapHorizon';
import type { components } from '../api/schema';

export type AdminUser = components['schemas']['AdminUserView'];
export type SeatStatus = components['schemas']['UpdateSeatStatusRequest']['status'];
export type UserActivationReport = components['schemas']['UserActivationReport'];
export type SeatStatusChangeReport = components['schemas']['SeatStatusChangeReport'];

export type DayOccupancyReport = components['schemas']['DayOccupancyReport'];

const ADMIN_USERS_KEY = ['admin', 'users'] as const;

/**
 * GET /api/admin/reports/occupancy — who sat where on one day.
 *
 * <p>Disabled until a date is chosen, so opening the screen does not silently report on today
 * before anybody asked it to. `staleTime: Infinity` because a past day cannot change: refetching
 * the 4th on every window focus is work that can only ever return the same rows.
 */
export function useOccupancyReport(date: string | null) {
  return useQuery({
    queryKey: ['admin', 'occupancy', date],
    enabled: date != null && date !== '',
    staleTime: Number.POSITIVE_INFINITY,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/api/admin/reports/occupancy', {
        params: { query: { date: date as string } },
      });
      if (error || !data) {
        throw new Error(getErrorMessage(error, 'Could not build that report.'));
      }
      return data;
    },
  });
}

/**
 * Everything an administrative change can invalidate.
 *
 * <p>Both mutations release other people's bookings, so the seat map, the date strip and the
 * caller's own bookings can all be wrong afterwards — a live socket only carries the seats that
 * changed, and only for the date each client is looking at.
 */
function useInvalidateAfterAdminChange() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ADMIN_USERS_KEY });
    void queryClient.invalidateQueries({ queryKey: ['seatmap'] });
    void queryClient.invalidateQueries({ queryKey: SEAT_HORIZON_KEY });
    void queryClient.invalidateQueries({ queryKey: ['bookings'] });
  };
}

/** GET /api/admin/users — everyone on the system, ordered by name. */
export function useAdminUsers() {
  return useQuery({
    queryKey: ADMIN_USERS_KEY,
    queryFn: async () => {
      const { data, error } = await apiClient.GET('/api/admin/users');
      if (error || !data) {
        throw new Error(getErrorMessage(error, 'Could not load the people list.'));
      }
      return data;
    },
  });
}

/**
 * PATCH /api/admin/users/{id}/active — close or reopen an account.
 *
 * <p>Deactivating also hands back every desk the account still holds; the report says which, and
 * the caller is expected to show that rather than a bare confirmation.
 */
export function useSetUserActive() {
  const invalidate = useInvalidateAfterAdminChange();

  return useMutation({
    mutationFn: async ({ userId, active }: { userId: number; active: boolean }) => {
      const { data, error } = await apiClient.PATCH('/api/admin/users/{id}/active', {
        params: { path: { id: userId } },
        body: { active },
      });
      if (error || !data) {
        throw new Error(getErrorMessage(error, 'Could not change that account.'));
      }
      return data;
    },
    onSettled: invalidate,
  });
}

/** PATCH /api/admin/seats/{id}/status — take a desk out of the pool, or put it back. */
export function useSetSeatStatus() {
  const invalidate = useInvalidateAfterAdminChange();

  return useMutation({
    mutationFn: async ({ seatId, status }: { seatId: number; status: SeatStatus }) => {
      const { data, error } = await apiClient.PATCH('/api/admin/seats/{id}/status', {
        params: { path: { id: seatId } },
        body: { status },
      });
      if (error || !data) {
        throw new Error(getErrorMessage(error, 'Could not change that desk.'));
      }
      return data;
    },
    onSettled: invalidate,
  });
}
