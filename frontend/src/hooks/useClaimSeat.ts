import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { getErrorMessage } from '../api/errors';

/**
 * A failed claim. `conflict` is the one the UI treats specially: a 409 means
 * someone else won the seat in the race — the invariant doing its job, not a
 * bug — so the tile shakes and flips to occupied rather than showing an error.
 */
export class ClaimError extends Error {
  readonly status: number;
  readonly conflict: boolean;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ClaimError';
    this.status = status;
    this.conflict = status === 409;
  }
}

function idempotencyKey(): string {
  const c = globalThis.crypto;
  return c && 'randomUUID' in c
    ? c.randomUUID()
    : `claim-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/**
 * Claim a seat for `date`. Sends a fresh Idempotency-Key so a retry is safe,
 * and refetches the seat map on settle so the map reflects the true post-claim
 * state (mine, or whoever won it). The seat's optimistic PENDING state and the
 * win/lose motion are driven by the caller.
 */
export function useClaimSeat(date: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (seatId: number) => {
      const { data, error, response } = await apiClient.POST('/api/bookings', {
        body: { seatId, date },
        params: { header: { 'Idempotency-Key': idempotencyKey() } },
      });
      if (error || !data) {
        throw new ClaimError(
          getErrorMessage(error, 'Could not book that seat.'),
          response?.status ?? 0,
        );
      }
      return data;
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ['seatmap'] });
    },
  });
}
