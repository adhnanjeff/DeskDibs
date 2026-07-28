import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { SeatMapPage } from './SeatMapPage';
import { renderWithProviders } from '../test/renderWithProviders';
import { server } from '../test/mocks/server';
import { API_BASE_URL } from '../api/client';

function url(path: string): string {
  return `${API_BASE_URL}${path}`;
}

/** The exact refusal the backend sends when you already hold a desk that day. */
function alreadyBookedThatDay() {
  return HttpResponse.json(
    {
      code: 'ALREADY_BOOKED_THAT_DAY',
      message: 'You already have a seat booked for that date.',
      path: '/api/bookings',
      timestamp: new Date().toISOString(),
      details: {
        bookingDate: '2026-08-10',
        existingBookingId: 41,
        existingSeatId: 12,
        existingSeatLabel: 'R2-A3',
      },
    },
    { status: 409 },
  );
}

/**
 * PLAN.md §5 #3 — "409 + *You have R2-A3 that day. Move here instead?* (atomic cancel+book)".
 *
 * <p>The backend has always been able to do this; what was missing was any way to ask for it. These
 * tests pin the two halves that make the offer trustworthy: that a clash is told apart from a lost
 * race, and that accepting it goes to the atomic move endpoint rather than cancelling first from
 * the browser.
 */
describe('moving instead of booking', () => {
  it('offers the move when you already hold a desk that day, instead of claiming somebody took it', async () => {
    server.use(http.post(url('/api/bookings'), () => alreadyBookedThatDay()));

    const user = userEvent.setup();
    renderWithProviders(<SeatMapPage />);

    await user.click(await screen.findByRole('button', { name: /seat L1-A1: available/i }));
    await user.click(screen.getByRole('button', { name: /^book now$/i }));

    // Names the desk in the way, and offers the swap.
    const offer = await screen.findByRole('button', { name: /move to L1-A1/i });
    expect(offer).toBeInTheDocument();
    const sidebar = screen.getByRole('complementary');
    expect(sidebar).toHaveTextContent(/you already have/i);
    expect(sidebar).toHaveTextContent('R2-A3');

    // Announced as well as shown. The offer is the useful half of this refusal, and somebody on a
    // screen reader has no other way to discover that a new button just appeared.
    expect(screen.getByRole('status')).toHaveTextContent(
      /you already have seat R2-A3 that day\. you can move to L1-A1 instead\./i,
    );

    // The bug this pins: every 409 used to be reported as a lost race, so somebody who already
    // had a desk was told the empty seat in front of them had just been taken.
    expect(screen.queryByText(/just taken by someone else/i)).not.toBeInTheDocument();
  });

  it('accepting the offer calls the atomic move endpoint, not cancel-then-claim', async () => {
    const requests: Array<{ path: string; body: unknown }> = [];

    server.use(
      http.post(url('/api/bookings'), async ({ request }) => {
        requests.push({ path: '/api/bookings', body: await request.json() });
        return alreadyBookedThatDay();
      }),
      http.delete(url('/api/bookings/:id'), ({ params }) => {
        requests.push({ path: `DELETE /api/bookings/${params.id}`, body: null });
        return new HttpResponse(null, { status: 204 });
      }),
      http.post(url('/api/bookings/move'), async ({ request }) => {
        requests.push({ path: '/api/bookings/move', body: await request.json() });
        return HttpResponse.json({
          id: 41,
          seatId: 101,
          seatLabel: 'L1-A1',
          userId: 1,
          userDisplayName: 'Dev Employee',
          bookingDate: '2026-08-10',
          status: 'ACTIVE',
          checkedInAt: null,
        });
      }),
    );

    const user = userEvent.setup();
    renderWithProviders(<SeatMapPage />);

    await user.click(await screen.findByRole('button', { name: /seat L1-A1: available/i }));
    await user.click(screen.getByRole('button', { name: /^book now$/i }));
    await user.click(await screen.findByRole('button', { name: /move to L1-A1/i }));

    await waitFor(() => {
      expect(requests.map((r) => r.path)).toContain('/api/bookings/move');
    });

    // The whole reason the move is one request: cancelling from the browser first would give up
    // the desk you have, and a target seat somebody else took in the meantime would leave you
    // with neither.
    expect(requests.some((r) => r.path.startsWith('DELETE'))).toBe(false);
    expect(requests.find((r) => r.path === '/api/bookings/move')?.body).toMatchObject({
      seatId: 101,
    });

    // Once it lands the offer is spent — leaving it on screen would invite a second move.
    await waitFor(() => {
      expect(screen.queryByRole('button', { name: /move to L1-A1/i })).not.toBeInTheDocument();
    });
  });
});
