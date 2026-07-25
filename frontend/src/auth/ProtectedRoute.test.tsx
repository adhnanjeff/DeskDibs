import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import App from '../App';
import { renderWithProviders } from '../test/renderWithProviders';

describe('ProtectedRoute', () => {
  it('redirects an unauthenticated visitor from / to /login', async () => {
    renderWithProviders(<App />, { route: '/' });

    expect(
      await screen.findByRole('heading', { name: /deskdibs/i }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: /seat map/i }),
    ).not.toBeInTheDocument();
  });

  it('redirects an unauthenticated visitor from /my-bookings to /login', async () => {
    renderWithProviders(<App />, { route: '/my-bookings' });

    expect(await screen.findByLabelText(/email/i)).toBeInTheDocument();
  });
});
