import { describe, expect, it } from 'vitest';
import { apiClient } from './client';
import { saveSession } from '../auth/tokenStorage';
import { EMPLOYEE_TOKEN, EMPLOYEE_USER } from '../test/mocks/data';

describe('apiClient', () => {
  it('makes an unauthenticated request without a bearer token', async () => {
    const { data, error } = await apiClient.GET('/api/auth/me');
    // No session saved yet — the mock /me handler rejects a missing token.
    expect(data).toBeUndefined();
    expect(error).toBeDefined();
  });

  it('attaches the bearer token automatically once a session is stored', async () => {
    saveSession({ accessToken: EMPLOYEE_TOKEN, user: EMPLOYEE_USER });

    const { data, error } = await apiClient.GET('/api/auth/me');

    expect(error).toBeUndefined();
    expect(data?.email).toBe(EMPLOYEE_USER.email);
  });
});
