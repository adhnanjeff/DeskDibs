import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
import App from '../App';
import { renderWithProviders } from '../test/renderWithProviders';
import { saveSession } from './tokenStorage';
import { EMPLOYEE_USER } from '../test/mocks/data';

describe('AuthContext — 401 handling', () => {
  it('clears a stale session and redirects to /login when the server rejects it', async () => {
    // A token the mock backend no longer recognises (e.g. the dev server
    // restarted and rotated its signing key, per docs/postman/README.md).
    saveSession({ accessToken: 'stale-token', user: EMPLOYEE_USER });

    renderWithProviders(<App />, { route: '/' });

    expect(await screen.findByLabelText(/email/i)).toBeInTheDocument();
    expect(sessionStorage.getItem('deskdibs.session')).toBeNull();
  });
});
