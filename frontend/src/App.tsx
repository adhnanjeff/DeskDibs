import { Route, Routes } from 'react-router-dom';
import { AppShell } from './components/AppShell';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { RequireRole } from './auth/RequireRole';
import { LoginPage } from './pages/LoginPage';
import { SeatMapPage } from './pages/SeatMapPage';
import { MyBookingsPage } from './pages/MyBookingsPage';
import { ReservationsPage } from './pages/ReservationsPage';
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

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}

export default App;
