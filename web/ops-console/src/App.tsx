import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import { ToastProvider } from './contexts/ToastContext';
import { Layout } from './components/Layout';
import { ProtectedRoute } from './components/ProtectedRoute';
import { LoginPage } from './pages/LoginPage';
import { PageLoader } from './components/ui/loading';
import './index.css';

// Lazy-loaded pages to maintain high performance and small initial bundle size
const DashboardPage = lazy(() =>
  import('./pages/DashboardPage').then((m) => ({ default: m.DashboardPage }))
);
const CasesPage = lazy(() =>
  import('./pages/CasesPage').then((m) => ({ default: m.CasesPage }))
);
const CaseDetailPage = lazy(() =>
  import('./pages/CaseDetailPage').then((m) => ({ default: m.CaseDetailPage }))
);
const ReconciliationPage = lazy(() =>
  import('./pages/ReconciliationPage').then((m) => ({ default: m.ReconciliationPage }))
);
const PayoutsPage = lazy(() =>
  import('./pages/PayoutsPage').then((m) => ({ default: m.PayoutsPage }))
);
const LedgerPage = lazy(() =>
  import('./pages/LedgerPage').then((m) => ({ default: m.LedgerPage }))
);
const MerchantPortalPage = lazy(() =>
  import('./pages/MerchantPortalPage').then((m) => ({ default: m.MerchantPortalPage }))
);
const UsersPage = lazy(() =>
  import('./pages/UsersPage').then((m) => ({ default: m.UsersPage }))
);
const AuditLogsPage = lazy(() =>
  import('./pages/AuditLogsPage').then((m) => ({ default: m.AuditLogsPage }))
);
const SystemPage = lazy(() =>
  import('./pages/SystemPage').then((m) => ({ default: m.SystemPage }))
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

              {/* Protected Shell Routes */}
              <Route
                element={
                  <ProtectedRoute>
                    <Layout />
                  </ProtectedRoute>
                }
              >
                {/* Core Operations Routes */}
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/cases" element={<CasesPage />} />
                <Route path="/cases/:id" element={<CaseDetailPage />} />
                <Route path="/reconciliation" element={<ReconciliationPage />} />
                <Route path="/payouts" element={<PayoutsPage />} />
                <Route path="/ledger" element={<LedgerPage />} />
                <Route path="/me" element={<MerchantPortalPage />} />
                <Route path="/system" element={<SystemPage />} />

                {/* Admin Only Routes */}
                <Route
                  path="/admin/users"
                  element={
                    <ProtectedRoute requiredRole="ADMIN">
                      <UsersPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/admin/audit"
                  element={
                    <ProtectedRoute requiredRole="ADMIN">
                      <AuditLogsPage />
                    </ProtectedRoute>
                  }
                />
              </Route>

              {/* Default Fallbacks */}
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </Suspense>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}
