import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { RequireRole } from './auth/RequireRole';
import { LoginPage } from './pages/LoginPage';
import { SeatMapPage } from './pages/SeatMapPage';
import { MyBookingsPage } from './pages/MyBookingsPage';
import { ReservationsPage } from './pages/ReservationsPage';
import { AdminPage } from './pages/AdminPage';
import { ApiViewPage } from './pages/ApiViewPage';
import { NotFoundPage } from './pages/NotFoundPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      <Route
        path="/"
        element={
          <ProtectedRoute>
            <AppShell>
              <SeatMapPage />
            </AppShell>
          </ProtectedRoute>
        }
      />

      <Route
        path="/my-bookings"
        element={
          <ProtectedRoute>
            <AppShell>
              <MyBookingsPage />
            </AppShell>
          </ProtectedRoute>
        }
      />

      <Route
        path="/reservations"
        element={
          <ProtectedRoute>
            <AppShell>
              <RequireRole roles={['MANAGER', 'ADMIN']}>
                <ReservationsPage />
              </RequireRole>
            </AppShell>
          </ProtectedRoute>
        }
      />

      {/* Admins only — a manager may hold desks for their own team, but not close a colleague's
          account or withdraw a desk from the whole office. The server enforces the same split. */}
      <Route
        path="/admin"
        element={
          <ProtectedRoute>
            <AppShell>
              <RequireRole roles={['ADMIN']}>
                <AdminPage />
              </RequireRole>
            </AppShell>
          </ProtectedRoute>
        }
      />

      {/* Admins only, same as /admin: the telemetry socket behind it is admin-gated server-side,
          so a non-admin reaching this route would get an empty screen and a refused subscription. */}
      <Route
        path="/api-view"
        element={
          <ProtectedRoute>
            <AppShell>
              <RequireRole roles={['ADMIN']}>
                <ApiViewPage />
              </RequireRole>
            </AppShell>
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default App;
