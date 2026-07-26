import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';

/**
 * Throws away a stale day when the office rolls over into a new one (PLAN.md §5 #10).
 *
 * <p>A tab left open overnight is showing yesterday. Every seat on it is wrong, every booking on it
 * has already been and gone, and nothing would ever correct it on its own: the websocket only
 * carries changes for the date it subscribed to, and a query with no interval never refetches.
 *
 * <p>The date compared here is the server's, carried on the horizon's first entry — never a
 * `new Date()`, which is the client clock the whole system refuses to trust. The horizon already
 * refreshes on a slow interval, so this costs nothing extra and fires on the one minute a day the
 * date actually changes.
 */
export function useOfficeDateRollover(serverToday: string | undefined): void {
  const queryClient = useQueryClient();
  const known = useRef<string | undefined>(undefined);

  useEffect(() => {
    if (!serverToday) return;

    if (known.current === undefined) {
      known.current = serverToday;
      return;
    }
    if (known.current === serverToday) return;

    // A new day. Everything cached is about the old one.
    known.current = serverToday;
    void queryClient.invalidateQueries();
  }, [serverToday, queryClient]);
}
