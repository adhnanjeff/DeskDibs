import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

/**
 * PLAN.md §9: 150 virtual users against the real HTTP stack.
 *
 * The invariant itself is already proven by ConcurrentSeatClaimTest, which races 150 threads
 * against the database in-process. What that test cannot see is everything between the socket and
 * the service: Tomcat's thread pool, the Hikari pool, the security filter chain, JSON
 * serialisation. This measures those, and re-asserts the invariant through them.
 */

const API = __ENV.API_URL || 'http://localhost:8080';
const PASSWORD = __ENV.DEV_PASSWORD || 'devpassword123';
const EMAIL = __ENV.DEV_EMAIL || 'employee@deskdibs.local';

const winners = new Counter('claim_winners');
const conflicts = new Counter('claim_conflicts');
const throttled = new Counter('claim_throttled');
const serverErrors = new Counter('claim_server_errors');

export const options = {
  scenarios: {
    // The headline: everybody lunges for the same desk at the same moment.
    stampede: {
      executor: 'per-vu-iterations',
      vus: 150,
      iterations: 1,
      maxDuration: '1m',
      exec: 'claimTheSameSeat',
    },
    // The ordinary load the office actually generates: people looking at the floor.
    browse: {
      executor: 'constant-vus',
      vus: 150,
      duration: '30s',
      startTime: '1m',
      exec: 'readTheSeatMap',
    },
  },
  thresholds: {
    // Not one 5xx, in either scenario. A lost race is a 409 by design; a 500 is a bug.
    claim_server_errors: ['count == 0'],
    'http_req_failed{scenario:browse}': ['rate == 0'],
    'http_req_duration{scenario:browse}': ['p(95) < 800'],
    // Exactly one winner. `count` cannot express "exactly 1" as a range, so both bounds are given.
    claim_winners: ['count >= 1', 'count <= 1'],
  },
};

function signIn() {
  const response = http.post(
    `${API}/api/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  );
  if (response.status !== 200) {
    fail(`login failed (${response.status}) — is the backend running on ${API}?`);
  }
  return response.json('accessToken');
}

/**
 * One shared target seat for every VU, chosen once in setup so all 150 race the same row rather
 * than 150 different ones.
 */
export function setup() {
  const token = signIn();
  const auth = { headers: { Authorization: `Bearer ${token}` } };

  const map = http.get(`${API}/api/seatmap`, auth).json();
  const free = [];
  for (const floor of map.floors) {
    for (const zone of floor.zones) {
      for (const table of zone.tables) {
        for (const seat of table.seats) {
          if (seat.state === 'AVAILABLE') free.push(seat.seatId);
        }
      }
    }
  }
  if (free.length === 0) fail('no free desks to race for — release some bookings first');

  return { token, date: map.date, seatId: free[0] };
}

export function claimTheSameSeat(data) {
  const response = http.post(
    `${API}/api/bookings`,
    JSON.stringify({ seatId: data.seatId, date: data.date }),
    {
      headers: {
        Authorization: `Bearer ${data.token}`,
        'Content-Type': 'application/json',
        // A fresh key per VU: reusing one would make every request a replay of the first, and the
        // idempotency layer would answer them all identically without ever racing.
        'Idempotency-Key': `k6-${exec.scenario.iterationInTest}-${exec.vu.idInTest}`,
      },
    },
  );

  if (response.status === 200 || response.status === 201) winners.add(1);
  else if (response.status === 409) conflicts.add(1);
  else if (response.status === 429) throttled.add(1);
  else if (response.status >= 500) serverErrors.add(1);

  check(response, {
    'claim settled without a server error': (r) => r.status < 500,
    'claim was either won or cleanly refused': (r) => [200, 201, 409, 429].includes(r.status),
  });
}

export function readTheSeatMap(data) {
  const response = http.get(`${API}/api/seatmap`, {
    headers: { Authorization: `Bearer ${data.token}` },
  });
  check(response, { 'seat map read': (r) => r.status === 200 });
}

export function handleSummary(data) {
  const won = data.metrics.claim_winners?.values?.count ?? 0;
  const lost = data.metrics.claim_conflicts?.values?.count ?? 0;
  const limited = data.metrics.claim_throttled?.values?.count ?? 0;
  const failed = data.metrics.claim_server_errors?.values?.count ?? 0;

  const verdict =
    won === 1 && failed === 0
      ? 'PASS — exactly one booking won the seat, no server errors'
      : `FAIL — ${won} winner(s), ${failed} server error(s)`;

  return {
    stdout: [
      '',
      '  Seat stampede (150 VUs, one seat)',
      `    won:       ${won}`,
      `    409 lost:  ${lost}`,
      `    429 throttled: ${limited}${limited > 0 ? '  ← run with RATE_LIMIT_ENABLED=false' : ''}`,
      `    5xx:       ${failed}`,
      `    ${verdict}`,
      '',
    ].join('\n'),
  };
}
