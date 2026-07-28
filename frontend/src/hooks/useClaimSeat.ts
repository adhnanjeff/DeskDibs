import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '../api/client';
import { getErrorMessage, isApiErrorBody } from '../api/errors';
import { SEAT_HORIZON_KEY } from './useSeatMapHorizon';

/** The desk you already hold on the day you just tried to book, as named by a 409. */
export interface ClashingBooking {
  bookingId: number;
  seatId: number;
  seatLabel: string;
}

/**
 * A failed claim. `conflict` is the one the UI treats specially: a 409 means
 * someone else won the seat in the race — the invariant doing its job, not a
 * bug — so the tile shakes and flips to occupied rather than showing an error.
 */
export class ClaimError extends Error {
  readonly status: number;
  readonly conflict: boolean;
  /** The backend's machine-readable code, e.g. `SEAT_ALREADY_BOOKED`. */
  readonly code: string | null;
  /**
   * Set only for `ALREADY_BOOKED_THAT_DAY`: the desk you are already holding that day.
   *
   * <p>This is what makes PLAN.md §5 #3 possible without a second round trip. The backend already
   * looks the existing booking up in order to explain the refusal, so the answer to
   * <em>"move here instead?"</em> arrives inside the failure itself.
   */
  readonly clashesWith: ClashingBooking | null;

  constructor(
    message: string,
    status: number,
    code: string | null = null,
    clashesWith: ClashingBooking | null = null,
  ) {
    super(message);
    this.name = 'ClaimError';
    this.status = status;
    this.conflict = status === 409;
    this.code = code;
    this.clashesWith = clashesWith;
  }
}

/** Reads the `details` the backend attaches to an `ALREADY_BOOKED_THAT_DAY` refusal. */
function clashFrom(error: unknown): ClashingBooking | null {
  if (!isApiErrorBody(error) || error.code !== 'ALREADY_BOOKED_THAT_DAY') return null;

  const details = error.details ?? {};
  const bookingId = details.existingBookingId;
  const seatId = details.existingSeatId;
  const seatLabel = details.existingSeatLabel;

  // All three or nothing: offering "move here instead" without knowing which booking to move
  // would produce a button that cannot work.
  if (typeof bookingId !== 'number' || typeof seatId !== 'number' || typeof seatLabel !== 'string') {
    return null;
  }
  return { bookingId, seatId, seatLabel };
}

function toClaimError(error: unknown, status: number, fallback: string): ClaimError {
  return new ClaimError(
    getErrorMessage(error, fallback),
    status,
    isApiErrorBody(error) ? error.code : null,
    clashFrom(error),
  );
}

function idempotencyKey(): string {
  const c = globalThis.crypto;
  return c && 'randomUUID' in c
    ? c.randomUUID()
    : `claim-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

/** Claiming and moving change the same three views, and neither is worth a partial update. */
function useInvalidateSeatViews() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: ['seatmap'] });
    // The horizon lives under its own key now, so it needs naming explicitly — the day's
    // fill bar and "you have a desk here" tick both just changed.
    void queryClient.invalidateQueries({ queryKey: SEAT_HORIZON_KEY });
    void queryClient.invalidateQueries({ queryKey: ['bookings'] });
  };
}

/**
 * Claim a seat for `date`. Sends a fresh Idempotency-Key so a retry is safe,
 * and refetches the seat map on settle so the map reflects the true post-claim
 * state (mine, or whoever won it). The seat's optimistic PENDING state and the
 * win/lose motion are driven by the caller.
 */
export function useClaimSeat(date: string) {
  const invalidate = useInvalidateSeatViews();

  return useMutation({
    mutationFn: async (seatId: number) => {
      const { data, error, response } = await apiClient.POST('/api/bookings', {
        body: { seatId, date },
        params: { header: { 'Idempotency-Key': idempotencyKey() } },
      });
      if (error || !data) {
        throw toClaimError(error, response?.status ?? 0, 'Could not book that seat.');
      }
      return data;
    },
    onSettled: invalidate,
  });
}

/**
 * Move the desk you already hold on `date` to another one (PLAN.md §5 #3).
 *
 * <p>One request, not cancel-then-claim from the browser. The backend does both halves in a single
 * transaction, so a target seat that somebody else takes in the meantime leaves the original
 * booking untouched and ACTIVE — whereas a client-side cancel followed by a failed claim would
 * give up the old desk to buy nothing.
 */
export function useMoveSeat(date: string) {
  const invalidate = useInvalidateSeatViews();

  return useMutation({
    mutationFn: async (seatId: number) => {
      const { data, error, response } = await apiClient.POST('/api/bookings/move', {
        body: { seatId, date },
        params: { header: { 'Idempotency-Key': idempotencyKey() } },
      });
      if (error || !data) {
        throw toClaimError(error, response?.status ?? 0, 'Could not move you to that seat.');
      }
      return data;
    },
    onSettled: invalidate,
  });
}
