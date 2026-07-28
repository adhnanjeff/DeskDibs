import { Client } from '@stomp/stompjs';
import { API_BASE_URL } from '../api/client';

/** The topic a subscriber joins to hear seat changes for one office date. */
export function seatMapTopic(date: string): string {
  return `/topic/seatmap/${date}`;
}

/**
 * Live API telemetry for the admin view. Administrators only — the server refuses the SUBSCRIBE
 * frame for anyone else, so calling this from a non-admin screen fails closed rather than leaking.
 */
export function adminTelemetryTopic(): string {
  return '/topic/admin/telemetry';
}

/**
 * A STOMP client for the backend's raw-WebSocket endpoint (`/ws`, no SockJS).
 * The bearer token rides the STOMP CONNECT frame — the backend authenticates
 * there, not on the WS handshake, because browsers can't attach headers to a
 * WebSocket upgrade. Reconnects automatically so a dropped socket heals itself.
 */
export function createStompClient(token: string): Client {
  const brokerURL = `${API_BASE_URL.replace(/^http/, 'ws')}/ws`;
  return new Client({
    brokerURL,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 4000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
  });
}
