import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '../api/client';
import { getErrorMessage } from '../api/errors';
import { authEvents, UNAUTHORIZED_EVENT } from './authEvents';
import {
  clearSession,
  loadSession,
  saveSession,
  type CurrentUser,
} from './tokenStorage';

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated';

interface AuthContextValue {
  user: CurrentUser | null;
  accessToken: string | null;
  status: AuthStatus;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const initialSession = loadSession();
  const [accessToken, setAccessToken] = useState<string | null>(
    initialSession?.accessToken ?? null,
  );
  const [user, setUser] = useState<CurrentUser | null>(
    initialSession?.user ?? null,
  );
  const [status, setStatus] = useState<AuthStatus>(
    initialSession ? 'loading' : 'unauthenticated',
  );
  const navigate = useNavigate();

  // Re-validate a rehydrated session against the backend once on boot. This
  // catches the (dev-only) case where the server restarted and rotated its
  // signing key, so a token that looks fine locally no longer verifies.
  useEffect(() => {
    if (!initialSession) return;
    let cancelled = false;
    void apiClient.GET('/api/auth/me').then(({ data, error }) => {
      if (cancelled) return;
      if (data) {
        setUser(data);
        setStatus('authenticated');
      } else {
        void error;
        clearSession();
        setAccessToken(null);
        setUser(null);
        setStatus('unauthenticated');
      }
    });
    return () => {
      cancelled = true;
    };
    // Only ever run once, on mount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    function handleUnauthorized(): void {
      setAccessToken(null);
      setUser(null);
      setStatus('unauthenticated');
      navigate('/login', { replace: true });
    }
    authEvents.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
    return () =>
      authEvents.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
  }, [navigate]);

  const login = useCallback(async (email: string, password: string) => {
    const { data, error } = await apiClient.POST('/api/auth/login', {
      body: { email, password },
    });
    if (error || !data?.accessToken || !data.user) {
      throw new Error(getErrorMessage(error, 'Invalid email or password.'));
    }
    saveSession({ accessToken: data.accessToken, user: data.user });
    setAccessToken(data.accessToken);
    setUser(data.user);
    setStatus('authenticated');
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setAccessToken(null);
    setUser(null);
    setStatus('unauthenticated');
    navigate('/login', { replace: true });
  }, [navigate]);

  return (
    <AuthContext.Provider value={{ user, accessToken, status, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

// Colocating the hook with its provider is the standard context pattern;
// it only costs Fast Refresh granularity (a change here remounts the whole
// tree instead of hot-swapping), never correctness.
// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider');
  return ctx;
}
