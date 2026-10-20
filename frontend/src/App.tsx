import { createBrowserRouter, Navigate } from 'react-router';
import { RequireAuth } from './auth/RequireAuth';
import { Layout } from './components/Layout';
import { BookingsPage } from './pages/BookingsPage';
import { LoginPage } from './pages/LoginPage';
import { RegisterPage } from './pages/RegisterPage';
import { ServicesPage } from './pages/ServicesPage';
import { StaffPage } from './pages/StaffPage';

// Paths are relative to /app. The backend forwards each of these to index.html (see SpaController).
export const router = createBrowserRouter(
  [
    { path: '/login', element: <LoginPage /> },
    { path: '/register', element: <RegisterPage /> },
    {
      path: '/',
      element: (
        <RequireAuth>
          <Layout />
        </RequireAuth>
      ),
      children: [
        { index: true, element: <Navigate to="/bookings" replace /> },
        { path: 'bookings', element: <BookingsPage /> },
        { path: 'staff', element: <StaffPage /> },
        { path: 'services', element: <ServicesPage /> },
      ],
    },
    { path: '*', element: <Navigate to="/bookings" replace /> },
  ],
  { basename: '/app' },
);
