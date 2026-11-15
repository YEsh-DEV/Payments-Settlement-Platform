import React from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import {
  LayoutDashboard,
  Users,
  FolderKanban,
  Ticket,
  FileText,
  Menu,
  X,
  LogOut,
  User,
  Bell,
} from 'lucide-react';
import { cn } from '../lib/utils';
import { Button } from './ui/button';
import { LogoMark } from './Logo';

const navigation = [
  { name: 'Dashboard', href: '/dashboard', icon: LayoutDashboard },
  { name: 'Projects', href: '/projects', icon: FolderKanban },
  { name: 'Tickets', href: '/tickets', icon: Ticket },
  { name: 'Users', href: '/users', icon: Users, adminOnly: true },
  { name: 'Audit Logs', href: '/audit-logs', icon: FileText, adminOnly: true },
];

const AVATAR_COLORS = [
  '#0c66e4', '#1f845a', '#6e5dc6', '#c9372c', '#e2772c', '#206b74',
];

function avatarColor(seed: string) {
  let hash = 0;
  for (let i = 0; i < seed.length; i++) hash = seed.charCodeAt(i) + ((hash << 5) - hash);
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length];
}

export function Layout() {
  const [sidebarOpen, setSidebarOpen] = React.useState(false);
  const { user, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const filteredNavigation = navigation.filter(
    (item) => !item.adminOnly || user?.role === 'ADMIN'
  );

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      {/* Mobile sidebar overlay */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-40 bg-[#091e42]/50 backdrop-blur-sm lg:hidden"
          onClick={() => setSidebarOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Sidebar — deep brand rail echoing the login aurora */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-gradient-to-b from-[#0a2754] via-[#0a1f44] to-[#0a1733] text-white transition-transform duration-200 lg:static lg:translate-x-0',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <div className="flex h-16 items-center justify-between px-5">
          <Link
            to="/dashboard"
            className="flex items-center gap-2.5 rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/40"
            onClick={() => setSidebarOpen(false)}
          >
            <LogoMark className="h-8 w-8" />
            <span className="text-[17px] font-semibold tracking-tight text-white">IssueFlow</span>
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
          <p className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-wider text-white/40">
            Workspace
          </p>
          <div className="space-y-1">
            {filteredNavigation.map((item) => {
              const isActive = location.pathname.startsWith(item.href);
              return (
                <Link
                  key={item.name}
                  to={item.href}
                  aria-current={isActive ? 'page' : undefined}
                  className={cn(
                    'group relative flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-150 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/40',
                    isActive
                      ? 'bg-white/15 text-white shadow-sm'
                      : 'text-white/70 hover:bg-white/10 hover:text-white'
                  )}
                  onClick={() => setSidebarOpen(false)}
                >
                  {isActive && (
                    <span className="absolute left-0 top-1/2 h-6 w-1 -translate-y-1/2 rounded-r-full bg-[#4c9aff]" />
                  )}
                  <item.icon
                    className={cn(
                      'h-[18px] w-[18px] transition-colors',
                      isActive ? 'text-white' : 'text-white/60 group-hover:text-white'
                    )}
                  />
                  {item.name}
                </Link>
              );
            })}
          </div>
        </nav>

        <div className="border-t border-white/10 p-3">
          <div className="flex items-center gap-2.5 rounded-lg px-2 py-1.5">
            <div
              className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-full text-sm font-semibold text-white ring-2 ring-white/20"
              style={{ backgroundColor: avatarColor(user?.username || 'u') }}
            >
              {user?.fullName?.charAt(0).toUpperCase() || <User className="h-4 w-4" />}
            </div>
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-medium text-white">{user?.fullName}</p>
              <p className="truncate text-xs text-white/55">{user?.role}</p>
            </div>
          </div>
        </div>
      </aside>

      {/* Main content area */}
      <div className="flex flex-1 flex-col overflow-hidden">
        {/* Header */}
        <header className="z-30 flex h-16 flex-shrink-0 items-center justify-between border-b border-border bg-white/90 px-4 backdrop-blur-sm lg:px-6">
          <button
            className="text-muted-foreground transition-colors hover:text-foreground lg:hidden"
            onClick={() => setSidebarOpen(true)}
            aria-label="Open sidebar"
          >
            <Menu className="h-5 w-5" />
          </button>

          <div className="flex-1" />

          <div className="flex items-center gap-1 sm:gap-2">
            <Link to={`/users/${user?.id}/mentions`} aria-label="Mentions">
              <Button
                variant="ghost"
                size="icon"
                className="relative text-muted-foreground hover:text-foreground"
              >
                <Bell className="h-[18px] w-[18px]" />
              </Button>
            </Link>

            <div className="mx-1 hidden h-6 w-px bg-border sm:block" />

            <div className="flex items-center gap-2.5">
              <div className="hidden text-right sm:block">
                <p className="text-sm font-medium leading-tight text-foreground">{user?.fullName}</p>
                <p className="text-xs leading-tight text-muted-foreground">{user?.role}</p>
              </div>
              <div
                className="flex h-8 w-8 items-center justify-center rounded-full text-sm font-semibold text-white"
                style={{ backgroundColor: avatarColor(user?.username || 'u') }}
              >
                {user?.fullName?.charAt(0).toUpperCase() || <User className="h-4 w-4" />}
              </div>
              <Button
                variant="ghost"
                size="sm"
                onClick={handleLogout}
                className="gap-2 text-muted-foreground hover:text-foreground"
              >
                <LogOut className="h-4 w-4" />
                <span className="hidden sm:inline">Logout</span>
              </Button>
            </div>
          </div>
        </header>

        {/* Page content */}
        <main className="flex-1 overflow-auto">
          {/* key on pathname → subtle fade/slide as routes change (respects reduced-motion via CSS) */}
          <div key={location.pathname} className="page-enter mx-auto w-full max-w-7xl p-4 lg:p-6">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
