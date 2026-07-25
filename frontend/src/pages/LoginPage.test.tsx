import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from '../App';
import { renderWithProviders } from '../test/renderWithProviders';
import { EMPLOYEE_USER } from '../test/mocks/data';

describe('LoginPage', () => {
  it('shows validation errors when submitted empty', async () => {
    const user = userEvent.setup();
    renderWithProviders(<App />, { route: '/login' });

    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(
      await screen.findByText('Enter your email address.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Enter your password.')).toBeInTheDocument();
  });

  it('stores the token and redirects to the app on a successful login', async () => {
    const user = userEvent.setup();
    renderWithProviders(<App />, { route: '/login' });

    await user.type(screen.getByLabelText(/email/i), EMPLOYEE_USER.email);
    await user.type(screen.getByLabelText(/password/i), 'password');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    // Redirected past the login form into the protected shell.
    expect(
      await screen.findByRole('heading', { name: /seat map/i }),
    ).toBeInTheDocument();

    const stored = sessionStorage.getItem('deskdibs.session');
    expect(stored).not.toBeNull();
    expect(JSON.parse(stored ?? '{}').user.email).toBe(EMPLOYEE_USER.email);
  });

  it("shows the server's error message on a failed login", async () => {
    const user = userEvent.setup();
    renderWithProviders(<App />, { route: '/login' });

    await user.type(screen.getByLabelText(/email/i), EMPLOYEE_USER.email);
    await user.type(screen.getByLabelText(/password/i), 'wrong-password');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(
      await screen.findByText('Incorrect email or password.'),
    ).toBeInTheDocument();
    // Still on the login form, not redirected.
    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();

    await waitFor(() => {
      expect(sessionStorage.getItem('deskdibs.session')).toBeNull();
    });
  });
});
