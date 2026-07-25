import type { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from './AuthContext';
import type { components } from '../api/schema';

type Role = components['schemas']['CurrentUserResponse']['role'];

interface RequireRoleProps {
  roles: Role[];
  children: ReactNode;
}

/**
 * Gates a route by the signed-in user's role. The role always comes from
 * the already-authenticated user object held in AuthContext (itself sourced
 * from the server's /api/auth/me and /api/auth/login responses) — never
 * re-derived from a client-editable token claim or query param.
 *
 * This is a UX convenience only. The backend enforces authorization
 * independently on every mutation; this component's job is to not show a
 * dead end in the nav, not to be the security boundary.
 */
export function RequireRole({ roles, children }: RequireRoleProps) {
  const { user } = useAuth();

  if (!user || !roles.includes(user.role)) {
    return <Navigate to="/" replace />;
  }

  return <>{children}</>;
}
