import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import { ToastProvider } from './contexts/ToastContext';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './components/ProtectedRoute';
import { LoginPage } from './pages/LoginPage';
import { PageLoader } from './components/ui/loading';
import './index.css';

// Perf: authenticated pages pull in heavy deps (recharts, @dnd-kit, react-datepicker).
// Lazy-loading them keeps those chunks out of the initial bundle, so the unauthenticated
// login screen loads fast. The shell (Login, Layout, ProtectedRoute) stays eager because
// it is needed on first paint; Suspense streams each page chunk on navigation.
const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage }))
);
const UsersPage = lazy(() =>
  import('./pages/UsersPage').then((m) => ({ default: m.UsersPage }))
);
const ProjectsPage = lazy(() =>
  import('./pages/ProjectsPage').then((m) => ({ default: m.ProjectsPage }))
);
const ProjectDetailPage = lazy(() =>
  import('./pages/ProjectDetailPage').then((m) => ({ default: m.ProjectDetailPage }))
);
const TicketsPage = lazy(() =>
  import('./pages/TicketsPage').then((m) => ({ default: m.TicketsPage }))
);
const TicketDetailPage = lazy(() =>
  import('./pages/TicketDetailPage').then((m) => ({ default: m.TicketDetailPage }))
);
const MentionsPage = lazy(() =>
  import('./pages/MentionsPage').then((m) => ({ default: m.MentionsPage }))
);
const AuditLogsPage = lazy(() =>
  import('./pages/AuditLogsPage').then((m) => ({ default: m.AuditLogsPage }))
);

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <Suspense fallback={<PageLoader />}>
            <Routes>
              {/* Public Routes */}
              <Route path="/login" element={<LoginPage />} />

              {/* Protected Routes */}
              <Route
                element={
                  <ProtectedRoute>
                    <Layout />
                  </ProtectedRoute>
                }
              >
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/projects" element={<ProjectsPage />} />
                <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
                <Route path="/tickets" element={<TicketsPage />} />
                <Route path="/tickets/:ticketId" element={<TicketDetailPage />} />
                <Route path="/users/:userId/mentions" element={<MentionsPage />} />

                {/* Admin Only Routes */}
                <Route
                  path="/users"
                  element={
                    <ProtectedRoute requiredRole="ADMIN">
                      <UsersPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/audit-logs"
                  element={
                    <ProtectedRoute requiredRole="ADMIN">
                      <AuditLogsPage />
                    </ProtectedRoute>
                  }
                />
              </Route>

              {/* Default Route */}
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </Suspense>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}
