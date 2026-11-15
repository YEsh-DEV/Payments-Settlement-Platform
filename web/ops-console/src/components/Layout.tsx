import React from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import {
  LayoutDashboard,
  Users,
  AlertCircle,
  FileCheck,
  BookOpen,
  Landmark,
  ShieldAlert,
  Cpu,
  Menu,
  X,
  LogOut,
  User as UserIcon,
} from 'lucide-react';
import { cn } from '../lib/utils';
import { Button } from './ui/button';

const navigation = [
  { name: 'Dashboard', href: '/dashboard', icon: LayoutDashboard },
  { name: 'Cases & Exceptions', href: '/cases', icon: AlertCircle },
  { name: 'Reconciliation', href: '/reconciliation', icon: FileCheck },
  { name: 'Ledger Explorer', href: '/ledger', icon: BookOpen },
  { name: 'Batches & Payouts', href: '/payouts', icon: Landmark },
  { name: 'Merchant Portal', href: '/me', icon: UserIcon },
  { name: 'User Management', href: '/admin/users', icon: Users, adminOnly: true },
  { name: 'Audit Logs', href: '/admin/audit', icon: ShieldAlert, adminOnly: true },
  { name: 'System & Invariants', href: '/system', icon: Cpu, adminOnly: true },
];

export function Layout() {
  const [sidebarOpen, setSidebarOpen] = React.useState(false);
  const { user, logout, hasRole } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const filteredNavigation = navigation.filter(
    (item) => !item.adminOnly || hasRole('ADMIN')
  );

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-slate-900/50 backdrop-blur-sm lg:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Sidebar */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-slate-950 text-white transition-transform duration-200 lg:static lg:translate-x-0 border-r border-slate-800',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <div className="flex h-16 items-center justify-between px-5 border-b border-slate-800">
          <Link
            to="/dashboard"
            className="flex items-center gap-2.5 rounded-lg focus-visible:outline-none"
            onClick={() => setSidebarOpen(false)}
          >
            <div className="h-8 w-8 rounded-lg bg-indigo-600 flex items-center justify-center font-bold text-white tracking-wider">
              PC
            </div>
            <div className="flex flex-col">
              <span className="text-base font-bold tracking-tight text-white leading-tight">
                PayCore
              </span>
              <span className="text-[10px] uppercase font-semibold text-indigo-400 tracking-wider">
                Ops Console
              </span>
            </div>
          </Link>
          <button
            className="text-white/60 transition-colors hover:text-white lg:hidden"
            onClick={() => setSidebarOpen(false)}
            aria-label="Close sidebar"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        <nav className="flex-1 overflow-y-auto px-3 py-4">
          <p className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400">
            Operations
          </p>
          <div className="space-y-1">
            {filteredNavigation.map((item) => {
              const isActive = location.pathname.startsWith(item.href);
              const Icon = item.icon;
              return (
                <Link
                  key={item.name}
                  to={item.href}
                  aria-current={isActive ? 'page' : undefined}
                  className={cn(
                    'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors',
                    isActive
                      ? 'bg-indigo-600 text-white'
                      : 'text-slate-300 hover:bg-slate-800/60 hover:text-white'
                  )}
                  onClick={() => setSidebarOpen(false)}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  <span>{item.name}</span>
                </Link>
              );
            })}
          </div>
        </nav>

        {/* User bar */}
        <div className="border-t border-slate-800 p-3">
          <div className="flex items-center justify-between px-2 py-1.5">
            <div className="flex items-center gap-2.5 overflow-hidden">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-slate-800 text-xs font-semibold text-white">
                {user?.username?.slice(0, 2).toUpperCase() || 'OP'}
              </div>
              <div className="min-w-0">
                <p className="truncate text-xs font-medium text-white">{user?.fullName || user?.username}</p>
                <p className="truncate text-[10px] text-slate-400">{user?.roles?.join(', ') || 'ANALYST'}</p>
              </div>
            </div>
            <Button
              variant="ghost"
              size="icon"
              className="text-slate-400 hover:text-white hover:bg-slate-800 h-8 w-8"
              onClick={handleLogout}
              title="Logout"
            >
              <LogOut className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="flex h-16 items-center justify-between border-b border-border bg-card px-4 lg:px-8">
          <div className="flex items-center gap-3">
            <button
              className="text-muted-foreground hover:text-foreground lg:hidden"
              onClick={() => setSidebarOpen(true)}
              aria-label="Open sidebar"
            >
              <Menu className="h-6 w-6" />
            </button>
            <h1 className="text-sm font-medium text-muted-foreground">
              PayCore Settlement Platform
            </h1>
          </div>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-xs font-medium text-emerald-500 border border-emerald-500/20">
              PostgreSQL Isolated Schemas Active
            </span>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto bg-slate-50 p-4 lg:p-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
