import { http, HttpResponse } from 'msw';
import { API_BASE_URL } from '../../api/client';
import {
  EMPLOYEE_TOKEN,
  EMPLOYEE_USER,
  MANAGER_TOKEN,
  MANAGER_USER,
  MOCK_BOOKINGS,
  MOCK_SEATMAP,
} from './data';

function url(path: string): string {
  return `${API_BASE_URL}${path}`;
}

export const handlers = [
  http.post(url('/api/auth/login'), async ({ request }) => {
    const body = (await request.json()) as {
      email: string;
      password: string;
    };

    if (body.password !== 'password') {
      return HttpResponse.json(
        {
          code: 'INVALID_CREDENTIALS',
          message: 'Incorrect email or password.',
          path: '/api/auth/login',
          timestamp: new Date().toISOString(),
        },
        { status: 401 },
      );
    }

    if (body.email === MANAGER_USER.email) {
      return HttpResponse.json({
        accessToken: MANAGER_TOKEN,
        tokenType: 'Bearer',
        expiresInSeconds: 3600,
        user: MANAGER_USER,
      });
    }

    if (body.email === EMPLOYEE_USER.email) {
      return HttpResponse.json({
        accessToken: EMPLOYEE_TOKEN,
        tokenType: 'Bearer',
        expiresInSeconds: 3600,
        user: EMPLOYEE_USER,
      });
    }

    return HttpResponse.json(
      {
        code: 'INVALID_CREDENTIALS',
        message: 'Incorrect email or password.',
        path: '/api/auth/login',
        timestamp: new Date().toISOString(),
      },
      { status: 401 },
    );
  }),

  http.get(url('/api/auth/me'), ({ request }) => {
    const auth = request.headers.get('Authorization');
    if (auth === `Bearer ${EMPLOYEE_TOKEN}`) {
      return HttpResponse.json(EMPLOYEE_USER);
    }
    if (auth === `Bearer ${MANAGER_TOKEN}`) {
      return HttpResponse.json(MANAGER_USER);
    }
    return HttpResponse.json(
      {
        code: 'UNAUTHORIZED',
        message: 'Session expired.',
        path: '/api/auth/me',
        timestamp: new Date().toISOString(),
      },
      { status: 401 },
    );
  }),

  http.get(url('/api/seatmap'), () => HttpResponse.json(MOCK_SEATMAP)),

  http.get(url('/api/bookings/me'), () => HttpResponse.json(MOCK_BOOKINGS)),
];
