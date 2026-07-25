import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import App from '../App';
import { renderWithProviders } from '../test/renderWithProviders';
import { saveSession } from '../auth/tokenStorage';
import {
  EMPLOYEE_TOKEN,
  EMPLOYEE_USER,
  MANAGER_TOKEN,
  MANAGER_USER,
} from '../test/mocks/data';

describe('Role-gated navigation', () => {
  it('hides the Reservations link for an EMPLOYEE', async () => {
    saveSession({ accessToken: EMPLOYEE_TOKEN, user: EMPLOYEE_USER });

    renderWithProviders(<App />, { route: '/' });

    await waitFor(() => {
      expect(
        screen.getByRole('heading', { name: /seat map/i }),
      ).toBeInTheDocument();
    });

    expect(
      screen.queryByRole('link', { name: /reservations/i }),
    ).not.toBeInTheDocument();
  });

  it('shows the Reservations link for a MANAGER', async () => {
    saveSession({ accessToken: MANAGER_TOKEN, user: MANAGER_USER });

    renderWithProviders(<App />, { route: '/' });

    expect(
      await screen.findByRole('link', { name: /reservations/i }),
    ).toBeInTheDocument();
  });
});
