import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import {
  API_URL,
  PEOPLE,
  availableSeats,
  nextOpenDay,
  openSeatMapAs,
  releaseBookingOn,
  seatTile,
  signInViaApi,
  type Session,
} from './support/office';

/**
 * The guarantee the whole system is built around, exercised through two real browsers.
 *
 * <p>PLAN.md §9: two contexts click one seat; exactly one wins, and the loser is told who took it
 * rather than shown an error. The backend already proves the invariant with 150 racing threads —
 * what only a browser can prove is that the *interface* handles losing gracefully, because a lost
 * race is a normal outcome here, not a failure.
 *
 * <p>Assertions are on what each tab ends up <em>showing</em>, not on the transient announcement
 * that flashes through the live region. The announcement is overwritten by the websocket broadcast
 * within milliseconds and the region is re-rendered underneath any observer watching it — so
 * catching it is a race in its own right. The rendered seat is the durable evidence, and it is
 * also the thing a user actually sees.
 */
test.describe('two people, one seat', () => {
  let alice: Session;
  let bob: Session;
  let seatLabel: string;
  let seatId: number;
  let bookingDay: string;

  test.beforeEach(async ({ request }) => {
    alice = await signInViaApi(request, PEOPLE.employee.email);
    bob = await signInViaApi(request, PEOPLE.manager.email);

    // The next day the office is open — not necessarily today, and a desk cannot be claimed for a
    // day the office is shut.
    bookingDay = await nextOpenDay(request, alice.accessToken);

    // A person may hold only one desk per day, so anything left over from a previous run would
    // make the second claim fail as "you already have a desk that day" — a 409, but the wrong one.
    await releaseBookingOn(request, alice.accessToken, bookingDay);
    await releaseBookingOn(request, bob.accessToken, bookingDay);

    const free = await availableSeats(request, alice.accessToken, bookingDay);
    expect(free.length, 'the office needs at least one free desk to race for').toBeGreaterThan(0);
    seatLabel = free[free.length - 1].seatLabel;
    seatId = free[free.length - 1].seatId;
  });

  test('exactly one claim wins, and the loser is shown who took it', async ({
    browser,
    request,
  }) => {
    const alicesTab = await browser.newPage();
    const bobsTab = await browser.newPage();

    await openSeatMapAs(alicesTab, alice);
    await openSeatMapAs(bobsTab, bob);
    await pickDay(alicesTab, bookingDay);
    await pickDay(bobsTab, bookingDay);

    // Both pick the same desk, and neither has pressed Book yet.
    await seatTile(alicesTab, seatLabel).click();
    await seatTile(bobsTab, seatLabel).click();
    // Scoped to the sidebar: the label also appears in the hover card that follows the click.
    await expect(selectedSeatPanel(alicesTab)).toContainText(seatLabel);
    await expect(selectedSeatPanel(bobsTab)).toContainText(seatLabel);

    // The race. `Promise.all` dispatches both clicks without awaiting the first response, so the
    // two requests are in flight together and the database decides the order.
    await Promise.all([bookNow(alicesTab), bookNow(bobsTab)]);

    // The invariant, straight from the source of truth: one desk, one booking, whoever won.
    //
    // Polled rather than read once. `bookNow` dispatches a click and returns — which is exactly
    // what makes the two requests race — so `Promise.all` above resolves when both clicks have
    // been *sent*, not when either claim has committed. Reading the map immediately after can
    // therefore catch the moment before the winner's row exists.
    const holder = await waitForHolderOf(request, alice.accessToken, bookingDay, seatId);

    const winnerName = holder.name;
    const loserName = winnerName === PEOPLE.employee.name ? PEOPLE.manager.name : PEOPLE.employee.name;
    const losingTab = winnerName === PEOPLE.employee.name ? bobsTab : alicesTab;
    const winningTab = winnerName === PEOPLE.employee.name ? alicesTab : bobsTab;

    // The loser's map names the person who got it — not a generic error, and not a stale
    // "available" tile they could click again.
    await expect(seatTile(losingTab, seatLabel)).toHaveAccessibleName(
      new RegExp(`Seat ${seatLabel}:.*${winnerName}`),
    );
    await expect(seatTile(losingTab, seatLabel)).not.toHaveAccessibleName(/available/i);

    // And the winner's map shows it as theirs.
    await expect(seatTile(winningTab, seatLabel)).toHaveAccessibleName(
      new RegExp(`Seat ${seatLabel}:\\s*(yours|checked in)`, 'i'),
    );

    expect(loserName).not.toEqual(winnerName);

    await alicesTab.close();
    await bobsTab.close();
  });

  test('losing a race leaves the map usable — the loser can still take another desk', async ({
    browser,
    request,
  }) => {
    const tab = await browser.newPage();
    await openSeatMapAs(tab, alice);
    await pickDay(tab, bookingDay);

    await seatTile(tab, seatLabel).click();
    await bookNow(tab);

    // The tile flips to hers without a reload: the claim's own response, then the broadcast.
    await expect(seatTile(tab, seatLabel)).toHaveAccessibleName(
      new RegExp(`Seat ${seatLabel}:\\s*(yours|checked in)`, 'i'),
    );

    const holder = await holderOf(request, alice.accessToken, bookingDay, seatId);
    expect(holder?.name).toBe(PEOPLE.employee.name);

    await releaseBookingOn(request, alice.accessToken, bookingDay);
    await tab.close();
  });
});

/**
 * Waits until somebody holds the seat, then reports who.
 *
 * <p>Deliberately not a bare read: the race is dispatched without awaiting either response, so
 * "has a winner emerged yet" is a question that needs asking more than once. Failing here means no
 * booking appeared at all within the timeout, which is a real defect — the two claims are supposed
 * to produce exactly one.
 */
async function waitForHolderOf(
  request: APIRequestContext,
  token: string,
  date: string,
  seatId: number,
): Promise<{ name: string }> {
  const deadline = Date.now() + 10_000;
  while (Date.now() < deadline) {
    const holder = await holderOf(request, token, date, seatId);
    if (holder) return holder;
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  throw new Error(`Nobody held seat ${seatId} on ${date} after the race — expected exactly one winner`);
}

/**
 * Who actually holds a seat on a day, read from the API rather than inferred from the UI. This is
 * the arbiter the two tabs are then checked against.
 */
async function holderOf(
  request: APIRequestContext,
  token: string,
  date: string,
  seatId: number,
): Promise<{ name: string } | null> {
  const response = await request.get(`${API_URL}/api/seatmap?date=${date}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBeTruthy();

  const map = await response.json();
  for (const floor of map.floors ?? []) {
    for (const zone of floor.zones ?? []) {
      for (const table of zone.tables ?? []) {
        for (const seat of table.seats ?? []) {
          if (seat.seatId === seatId && seat.occupantDisplayName) {
            return { name: seat.occupantDisplayName as string };
          }
        }
      }
    }
  }
  return null;
}

/**
 * Move the map onto a specific day via the date strip, so both tabs are racing the same date.
 * Matched by the strip's accessible name, which carries the full date.
 */
async function pickDay(page: Page, iso: string): Promise<void> {
  const long = new Date(`${iso}T00:00:00`).toLocaleDateString('en-US', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });
  const day = page.getByRole('button', { name: new RegExp(`^${long}`) });
  await day.click();
  await expect(day).toHaveAttribute('aria-pressed', 'true');
  // The map re-queries for the new date; wait for it to finish before touching a seat.
  await expect(page.getByTestId('seatmap-skeleton')).toHaveCount(0);
}

/** The sidebar panel that names whichever seat is currently picked. */
function selectedSeatPanel(page: Page) {
  return page.getByRole('complementary');
}

async function bookNow(page: Page): Promise<void> {
  await page.getByRole('button', { name: /^book now$/i }).click();
}
