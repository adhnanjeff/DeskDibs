import { useEffect, useRef, useState } from 'react';
import { loadSession } from '../auth/tokenStorage';
import { createStompClient, adminTelemetryTopic } from '../realtime/stompClient';

/** One finished server-side call, as broadcast by the backend's telemetry interceptor. */
export interface ApiCallEvent {
  id: string;
  at: string;
  method: string;
  route: string;
  status: number;
  durationMs: number;
  outcome: 'OK' | 'CLIENT_ERROR' | 'SERVER_ERROR';
  lane: 'AUTH' | 'SEATMAP' | 'BOOKING' | 'RESERVATION' | 'ADMIN' | 'OTHER';
  actor: string;
}

/**
 * How many calls to keep. The feed is a window on live traffic, not a log — an admin tab left open
 * all afternoon must not grow without bound, and nothing here is the system of record.
 */
const MAX_EVENTS = 60;

/** Requests older than this stop counting toward the rate and latency figures. */
const ROLLING_WINDOW_MS = 10_000;

export interface TelemetryStats {
  /** Calls per second over the rolling window. */
  ratePerSecond: number;
  /** Median and 95th-percentile server time, in ms, over the window. */
  medianMs: number;
  p95Ms: number;
  /** Share of windowed calls that failed, 0–1. */
  errorRate: number;
  /** How many calls the window currently holds. */
  sampleSize: number;
}

export interface ApiTelemetry {
  /** Most recent first. */
  events: ApiCallEvent[];
  stats: TelemetryStats;
  connected: boolean;
  /** Set when the socket was refused — almost always "not an administrator". */
  error: string | null;
}

function percentile(sorted: number[], fraction: number): number {
  if (sorted.length === 0) return 0;
  const index = Math.min(sorted.length - 1, Math.floor(sorted.length * fraction));
  return sorted[index] ?? 0;
}

function isApiCallEvent(value: unknown): value is ApiCallEvent {
  if (typeof value !== 'object' || value === null) return false;
  const candidate = value as Record<string, unknown>;
  return (
    typeof candidate.id === 'string' &&
    typeof candidate.route === 'string' &&
    typeof candidate.method === 'string' &&
    typeof candidate.status === 'number'
  );
}

const IDLE_STATS: TelemetryStats = {
  ratePerSecond: 0,
  medianMs: 0,
  p95Ms: 0,
  errorRate: 0,
  sampleSize: 0,
};

/**
 * The last ten seconds of traffic, recomputed on a timer rather than when events arrive.
 *
 * <p>Deriving these during render would be impure — the answer depends on the wall clock — and,
 * worse, wrong in the case that matters most: when traffic stops, no new event arrives to trigger
 * a recompute, so a burst that ended a minute ago would sit on screen reading 20 req/s forever.
 * A tick makes the window actually decay to zero.
 */
function useRollingStats(events: ApiCallEvent[]): TelemetryStats {
  const [stats, setStats] = useState<TelemetryStats>(IDLE_STATS);
  // The ticker reads the newest events without being torn down and rebuilt on every batch, so the
  // 500ms cadence stays steady no matter how fast frames arrive.
  const latest = useRef<ApiCallEvent[]>(events);
  useEffect(() => {
    latest.current = events;
  }, [events]);

  useEffect(() => {
    const recompute = () => {
      const cutoff = Date.now() - ROLLING_WINDOW_MS;
      const windowed = latest.current.filter((event) => {
        const at = Date.parse(event.at);
        return Number.isFinite(at) && at >= cutoff;
      });
      if (windowed.length === 0) {
        setStats((prev) => (prev.sampleSize === 0 ? prev : IDLE_STATS));
        return;
      }
      const durations = windowed.map((event) => event.durationMs).sort((a, b) => a - b);
      const failures = windowed.filter((event) => event.outcome !== 'OK').length;
      setStats({
        ratePerSecond: windowed.length / (ROLLING_WINDOW_MS / 1000),
        medianMs: percentile(durations, 0.5),
        p95Ms: percentile(durations, 0.95),
        errorRate: failures / windowed.length,
        sampleSize: windowed.length,
      });
    };

    recompute();
    const ticker = window.setInterval(recompute, 500);
    return () => window.clearInterval(ticker);
  }, []);

  return stats;
}

/**
 * Subscribes to `/topic/admin/telemetry` and keeps a rolling window of live API calls.
 *
 * <p>The topic is admin-only, enforced on the server when the SUBSCRIBE frame arrives — this hook
 * asking for it is not what makes it allowed. A non-admin session gets a STOMP error frame back
 * and {@link ApiTelemetry.error} is set.
 *
 * <p>Events arrive far faster than a human reads, so state is committed on an interval rather than
 * on every frame: a burst of 150 concurrent claims would otherwise queue 150 React renders.
 */
export function useApiTelemetry(enabled = true): ApiTelemetry {
  const [events, setEvents] = useState<ApiCallEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Frames land here and are drained on a timer, so socket throughput never drives render count.
  const pending = useRef<ApiCallEvent[]>([]);

  useEffect(() => {
    if (!enabled) return;
    const token = loadSession()?.accessToken;
    if (!token) return;

    const client = createStompClient(token);

    client.onWebSocketClose = () => setConnected(false);
    client.onWebSocketError = () => setConnected(false);
    client.onDisconnect = () => setConnected(false);
    client.onStompError = (frame) => {
      setConnected(false);
      setError(frame.headers.message ?? 'The telemetry stream was refused.');
    };

    client.onConnect = () => {
      setConnected(true);
      setError(null);
      client.subscribe(adminTelemetryTopic(), (message) => {
        let payload: unknown;
        try {
          payload = JSON.parse(message.body);
        } catch {
          return; // a malformed frame costs one dot, not the socket
        }
        if (isApiCallEvent(payload)) {
          pending.current.push(payload);
        }
      });
    };
    client.activate();

    const drain = window.setInterval(() => {
      if (pending.current.length === 0) return;
      const batch = pending.current;
      pending.current = [];
      setEvents((prev) => [...batch.reverse(), ...prev].slice(0, MAX_EVENTS));
    }, 250);

    return () => {
      window.clearInterval(drain);
      setConnected(false);
      void client.deactivate();
    };
  }, [enabled]);

  const stats = useRollingStats(events);

  return { events, stats, connected, error };
}
