import { expect, type APIRequestContext, type Page } from '@playwright/test';

export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080';

export const PEOPLE = {
  employee: { email: 'employee@deskdibs.local', name: 'Dev Employee' },
  manager: { email: 'manager@deskdibs.local', name: 'Dev Manager' },
} as const;

/** The seeded development password. Local provider only — never a real credential. */
export const DEV_PASSWORD = process.env.E2E_PASSWORD ?? 'devpassword123';

export interface Session {
  accessToken: string;
  /** The server's own view of the caller — never a shape invented by the test. */
  user: unknown;
}

export async function signInViaApi(
  request: APIRequestContext,
  email: string,
): Promise<Session> {
  const response = await request.post(`${API_URL}/api/auth/login`, {
    data: { email, password: DEV_PASSWORD },
  });
  expect(
    response.ok(),
    `Could not sign in as ${email}. Is the backend running on ${API_URL} with DEV_SEED_USERS=true?`,
  ).toBeTruthy();

  const accessToken = (await response.json()).accessToken as string;

  const me = await request.get(`${API_URL}/api/auth/me`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  expect(me.ok(), 'Could not read the signed-in user').toBeTruthy();

  return { accessToken, user: await me.json() };
}

interface SeatSummary {
  seatId: number;
  seatLabel: string;
  state: string;
}

async function seatMap(request: APIRequestContext, token: string, date?: string) {
  const response = await request.get(
    `${API_URL}/api/seatmap${date ? `?date=${date}` : ''}`,
    { headers: { Authorization: `Bearer ${token}` } },
  );
  expect(response.ok(), 'Could not read the seat map').toBeTruthy();
  return response.json();
}

/** Today, as the office reckons it — never the test runner's clock. */
export async function officeToday(request: APIRequestContext, token: string): Promise<string> {
  return (await seatMap(request, token)).date as string;
}

/**
 * The next day the office is actually open.
 *
 * <p>Not "today": desks cannot be booked for a day the office is shut, so a suite that assumed
 * today would pass Monday to Friday and fail every weekend — the worst kind of flake, because it
 * looks like a product bug and only reproduces two days in seven. The server decides which days
 * are open; this just takes the first one it offers.
 */
export async function nextOpenDay(request: APIRequestContext, token: string): Promise<string> {
  const response = await request.get(`${API_URL}/api/seatmap/horizon`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok(), 'Could not read the booking horizon').toBeTruthy();

  const open = (await response.json()).find((day: { bookable?: boolean }) => day.bookable);
  expect(open, 'the office is closed on every day of the horizon').toBeTruthy();
  return open.date as string;
}

/** Every seat free on `date`, in map order. */
export async function availableSeats(
  request: APIRequestContext,
  token: string,
  date?: string,
): Promise<SeatSummary[]> {
  const map = await seatMap(request, token, date);
  const seats: SeatSummary[] = [];
  for (const floor of map.floors ?? []) {
    for (const zone of floor.zones ?? []) {
      for (const table of zone.tables ?? []) {
        for (const seat of table.seats ?? []) {
          if (seat.state === 'AVAILABLE') seats.push(seat);
        }
      }
    }
  }
  return seats;
}

/**
 * Give up whatever this person holds on `date`.
 *
 * <p>A person may hold only one seat per date, so a booking left behind by an earlier run would
 * make the next claim fail as "you already have a desk that day" — a 409, but the wrong one, and
 * the race test would pass for entirely the wrong reason.
 */
export async function releaseBookingOn(
  request: APIRequestContext,
  token: string,
  date: string,
): Promise<void> {
  const response = await request.get(`${API_URL}/api/bookings/me`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBeTruthy();

  for (const booking of await response.json()) {
    if (booking.status === 'ACTIVE' && booking.bookingDate === date) {
      const deleted = await request.delete(`${API_URL}/api/bookings/${booking.id}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(deleted.ok(), `Could not release booking ${booking.id}`).toBeTruthy();
    }
  }
}

/**
 * Put a signed-in session straight into the page and load the seat map.
 *
 * <p>Deliberately seeds the token rather than driving the login form: these tests are about the
 * seat race, and typing a password in each of them would make every one of them a login test too.
 * The login form has its own coverage in the Vitest suite.
 */
export async function openSeatMapAs(page: Page, session: Session): Promise<void> {
  await page.addInitScript((stored) => {
    sessionStorage.setItem('deskdibs.session', JSON.stringify(stored));
  }, session);
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /main floor/i })).toBeVisible();
}

/** The tile for one seat, whatever state it is in. */
export function seatTile(page: Page, seatLabel: string) {
  return page.getByRole('button', { name: new RegExp(`^Seat ${seatLabel}:`) });
}
