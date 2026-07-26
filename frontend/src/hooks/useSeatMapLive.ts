import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { components } from '../api/schema';
import { loadSession } from '../auth/tokenStorage';
import { createStompClient, seatMapTopic } from '../realtime/stompClient';
import { mergeSeatUpdate } from '../realtime/seatMapCache';

type SeatMapResponse = components['schemas']['SeatMapResponse'];
type SeatMapSeat = components['schemas']['SeatMapSeat'];

interface SeatStatusChanged {
  bookingDate: string;
  seat: SeatMapSeat;
}

interface LiveOptions {
  /** Called for each broadcast after the cache is updated (announce / animate). */
  onSeatChange?: (seat: SeatMapSeat) => void;
}

export interface LiveStatus {
  /**
   * True while broadcasts are actually arriving. PLAN.md §5 #9: when the socket drops the UI
   * degrades to polling — slower, never wrong — so the caller needs to know which mode it is in.
   */
  connected: boolean;
}

/**
 * Keeps the cached seat map live for one date: opens an authenticated STOMP
 * connection, subscribes to that date's topic, and folds every broadcast seat
 * into the React Query cache with {@link mergeSeatUpdate}. A change someone else
 * makes appears on this map without a refetch.
 */
export function useSeatMapLive(date: string | undefined, options: LiveOptions = {}): LiveStatus {
  const queryClient = useQueryClient();
  const onSeatChange = options.onSeatChange;
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const token = loadSession()?.accessToken;
    if (!token || !date) return;

    const client = createStompClient(token);

    // Every path that ends with "we are no longer receiving broadcasts" has to flip this, or the
    // caller's poll fallback never engages and the map goes quietly stale — the worst outcome,
    // because it looks exactly like an office where nobody is booking anything.
    client.onWebSocketClose = () => setConnected(false);
    client.onWebSocketError = () => setConnected(false);
    client.onStompError = () => setConnected(false);
    client.onDisconnect = () => setConnected(false);

    client.onConnect = () => {
      setConnected(true);
      client.subscribe(seatMapTopic(date), (message) => {
        let payload: SeatStatusChanged;
        try {
          payload = JSON.parse(message.body) as SeatStatusChanged;
        } catch {
          return; // ignore a malformed frame rather than crash the socket
        }
        const updated = payload.seat;
        if (!updated?.seatId) return;

        queryClient.setQueriesData<SeatMapResponse>({ queryKey: ['seatmap'] }, (prev) =>
          prev ? mergeSeatUpdate(prev, updated) : prev,
        );
        onSeatChange?.(updated);
      });
    };
    client.activate();

    return () => {
      setConnected(false);
      void client.deactivate();
    };
  }, [date, queryClient, onSeatChange]);

  return { connected };
}
