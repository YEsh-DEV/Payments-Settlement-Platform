import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Loader as Loader2,
  Eye,
  EyeOff,
  ArrowRight,
  User,
  Lock,
  Scale,
  RefreshCw,
  ShieldCheck,
  Store,
} from 'lucide-react';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import { Logo, LogoMark } from '../components/Logo';

const loginSchema = z.object({
  username: z.string().min(1, 'Username is required'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

type LoginFormData = z.infer<typeof loginSchema>;

const HIGHLIGHTS = [
  {
    icon: Scale,
    title: 'Immutable Double-Entry Ledger',
    description: 'PostgreSQL trigger-enforced immutability, zero float, zero money loss.',
  },
  {
    icon: RefreshCw,
    title: 'Multi-Stage Reconciliation',
    description: 'Line matching, fee variance detection, and subset-sum bank deposit clearing.',
  },
  {
    icon: ShieldCheck,
    title: 'Dual-Control Maker-Checker',
    description: 'Enforced 4-eyes principle on dispute adjustments and ledger write-offs.',
  },
];

const DEMO_ACCOUNTS = [
  {
    role: 'admin' as const,
    label: 'Admin',
    blurb: 'Full ops control',
    icon: ShieldCheck,
    credentials: { username: 'admin', password: 'password123' },
  },
  {
    role: 'analyst' as const,
    label: 'Analyst',
    blurb: 'Recon & Cases',
    icon: Scale,
    credentials: { username: 'analyst', password: 'password123' },
  },
  {
    role: 'auditor' as const,
    label: 'Auditor',
    blurb: 'Read-only audit',
    icon: RefreshCw,
    credentials: { username: 'auditor', password: 'password123' },
  },
  {
    role: 'merchant' as const,
    label: 'Merchant',
    blurb: 'Portal view',
    icon: Store,
    credentials: { username: 'merchant', password: 'password123' },
  },
];

const PARTICLES = Array.from({ length: 14 }, (_, i) => ({
  id: i,
  left: `${(i * 7 + 5) % 96}%`,
  size: 4 + (i % 4) * 3,
  duration: 9 + (i % 6) * 2.5,
  delay: (i % 7) * 1.3,
}));

export function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const { showToast } = useToast();
  const [isLoading, setIsLoading] = React.useState(false);
  const [showPassword, setShowPassword] = React.useState(false);

  const from =
    (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ||
    '/dashboard';

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginFormData) => {
    setIsLoading(true);
    try {
      await login(data);
      showToast('Welcome to PayCore Ops Console', 'success');
      navigate(from, { replace: true });
    } catch (error) {
      const message =
        (error as { response?: { data?: { message?: string } } })?.response?.data?.message ||
        'Invalid credentials. Please try again.';
      showToast(message, 'error');
    } finally {
      setIsLoading(false);
    }
  };

  const signInWithDemo = (credentials: LoginFormData) => {
    setValue('username', credentials.username, { shouldValidate: true });
    setValue('password', credentials.password, { shouldValidate: true });
    void onSubmit(credentials);
  };

  return (
    <main className="login-aurora relative flex min-h-dvh w-full items-center justify-center overflow-hidden px-4 py-10 sm:px-6">
      <div
        aria-hidden="true"
        className="login-orbs pointer-events-none absolute inset-0 overflow-hidden"
      >
        <span className="login-orb login-orb--1" />
        <span className="login-orb login-orb--2" />
        <span className="login-orb login-orb--3" />
      </div>
      <div
        aria-hidden="true"
        className="login-particles pointer-events-none absolute inset-0 overflow-hidden"
      >
        {PARTICLES.map((p) => (
          <span
            key={p.id}
            className="login-particle"
            style={{
              left: p.left,
              width: `${p.size}px`,
              height: `${p.size}px`,
              animationDuration: `${p.duration}s`,
              animationDelay: `${p.delay}s`,
            }}
          />
        ))}
      </div>

      <div className="relative z-10 flex w-full max-w-6xl flex-col items-center justify-center gap-12 lg:flex-row lg:items-center lg:justify-between lg:gap-16">
        <section className="hidden w-full max-w-xl text-white lg:block">
          <div className="login-rise text-white">
            <Logo wordmarkClassName="text-2xl font-semibold tracking-tight" />
          </div>

          <span
            className="login-rise mt-9 inline-flex items-center rounded-full bg-white/10 px-3 py-1 text-xs font-medium tracking-wide text-white/85 ring-1 ring-white/20 backdrop-blur-sm"
            style={{ animationDelay: '80ms' }}
          >
            Payments & Settlement Engine
          </span>

          <h1
            className="login-rise mt-5 text-balance text-4xl font-bold leading-[1.1] tracking-tight xl:text-5xl"
            style={{ animationDelay: '120ms' }}
          >
            Mission-critical financial ledger & reconciliation.
          </h1>
          <p
            className="login-rise mt-4 max-w-md text-base leading-relaxed text-white/75"
            style={{ animationDelay: '160ms' }}
          >
            PayCore provides mathematical balance guarantees, automated 4-stage provider clearing, and audited dual-control resolution.
          </p>

          <ul className="mt-9 space-y-5">
            {HIGHLIGHTS.map((item, index) => {
              const Icon = item.icon;
              return (
                <li
                  key={item.title}
                  className="login-rise flex items-start gap-3.5"
                  style={{ animationDelay: `${200 + index * 70}ms` }}
                >
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white/15 ring-1 ring-white/25 backdrop-blur-sm">
                    <Icon className="h-5 w-5" />
                  </span>
                  <div>
                    <p className="font-semibold">{item.title}</p>
                    <p className="text-sm text-white/70">{item.description}</p>
                  </div>
                </li>
              );
            })}
          </ul>
        </section>

        <section className="w-full max-w-md">
          <div className="mb-6 flex flex-col items-center gap-2 text-center lg:hidden">
            <LogoMark className="h-11 w-11" />
            <span className="text-xl font-semibold tracking-tight text-white">PayCore</span>
            <span className="text-sm text-white/70">Payments & Settlement Operations</span>
          </div>

          <div
            className="login-rise rounded-2xl border border-white/40 bg-white/80 p-7 shadow-2xl backdrop-blur-2xl sm:p-9"
            style={{ animationDelay: '120ms' }}
          >
            <h1 className="text-2xl font-semibold tracking-tight text-foreground">
              Sign in to PayCore
            </h1>
            <p className="mt-1.5 text-sm text-muted-foreground">
              Enter your credentials to access operations console.
            </p>

            <form onSubmit={handleSubmit(onSubmit)} className="mt-7" noValidate>
              <div className="divide-y divide-border overflow-hidden rounded-xl border border-input bg-white/70 shadow-sm transition-all duration-150 focus-within:border-primary focus-within:ring-2 focus-within:ring-ring/30">
                <div className="relative">
                  <input
                    id="username"
                    type="text"
                    placeholder=" "
                    autoComplete="username"
                    aria-invalid={!!errors.username}
                    className="peer h-14 w-full bg-transparent pb-1.5 pl-11 pr-4 pt-6 text-sm text-foreground outline-none placeholder:text-transparent disabled:opacity-60"
                    {...register('username')}
                    disabled={isLoading}
                  />
                  <label
                    htmlFor="username"
                    className="pointer-events-none absolute left-11 top-1/2 -translate-y-1/2 text-sm text-muted-foreground transition-all duration-150 peer-focus:top-4 peer-focus:translate-y-0 peer-focus:text-[11px] peer-focus:font-medium peer-focus:text-primary peer-[:not(:placeholder-shown)]:top-4 peer-[:not(:placeholder-shown)]:translate-y-0 peer-[:not(:placeholder-shown)]:text-[11px]"
                  >
                    Username
                  </label>
                  <User className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground transition-colors peer-focus:text-primary" />
                </div>

                <div className="relative">
                  <input
                    id="password"
                    type={showPassword ? 'text' : 'password'}
                    placeholder=" "
                    autoComplete="current-password"
                    aria-invalid={!!errors.password}
                    className="peer h-14 w-full bg-transparent pb-1.5 pl-11 pr-12 pt-6 text-sm text-foreground outline-none placeholder:text-transparent disabled:opacity-60"
                    {...register('password')}
                    disabled={isLoading}
                  />
                  <label
                    htmlFor="password"
                    className="pointer-events-none absolute left-11 top-1/2 -translate-y-1/2 text-sm text-muted-foreground transition-all duration-150 peer-focus:top-4 peer-focus:translate-y-0 peer-focus:text-[11px] peer-focus:font-medium peer-focus:text-primary peer-[:not(:placeholder-shown)]:top-4 peer-[:not(:placeholder-shown)]:translate-y-0 peer-[:not(:placeholder-shown)]:text-[11px]"
                  >
                    Password
                  </label>
                  <Lock className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground transition-colors peer-focus:text-primary" />
                  <button
                    type="button"
                    onClick={() => setShowPassword((prev) => !prev)}
                    disabled={isLoading}
                    aria-label={showPassword ? 'Hide password' : 'Show password'}
                    aria-pressed={showPassword}
                    className="absolute right-1.5 top-1/2 flex h-10 w-10 -translate-y-1/2 items-center justify-center rounded-lg text-muted-foreground transition-colors hover:bg-muted hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              {(errors.username || errors.password) && (
                <div role="alert" className="mt-2 space-y-1 text-sm text-destructive">
                  {errors.username && <p>{errors.username.message}</p>}
                  {errors.password && <p>{errors.password.message}</p>}
                </div>
              )}

              <button
                type="submit"
                disabled={isLoading}
                className="group mt-5 flex h-11 w-full items-center justify-center rounded-lg bg-primary text-sm font-medium text-primary-foreground shadow-md transition-all duration-150 hover:-translate-y-0.5 hover:bg-[#0055cc] hover:shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 active:translate-y-0 active:scale-[0.99] disabled:pointer-events-none disabled:opacity-60"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Authenticating...
                  </>
                ) : (
                  <>
                    Log in
                    <ArrowRight className="ml-1.5 h-4 w-4 transition-transform duration-150 group-hover:translate-x-0.5" />
                  </>
                )}
              </button>
            </form>

            <div className="mt-6">
              <div className="flex items-center gap-3 text-[11px] font-medium uppercase tracking-wide text-muted-foreground">
                <span className="h-px flex-1 bg-border" />
                or choose role demo
                <span className="h-px flex-1 bg-border" />
              </div>
              <div className="mt-4 grid grid-cols-2 gap-3">
                {DEMO_ACCOUNTS.map((account) => {
                  const Icon = account.icon;
                  return (
                    <button
                      key={account.role}
                      type="button"
                      onClick={() => signInWithDemo(account.credentials)}
                      disabled={isLoading}
                      className="group flex items-center gap-3 rounded-lg border border-border bg-white/70 px-3 py-2.5 text-left transition-all duration-150 hover:-translate-y-0.5 hover:border-primary/40 hover:bg-accent hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-60"
                    >
                      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-accent text-accent-foreground transition-colors group-hover:bg-primary group-hover:text-primary-foreground">
                        <Icon className="h-4 w-4" />
                      </span>
                      <span className="min-w-0">
                        <span className="block text-sm font-medium text-foreground">
                          {account.label}
                        </span>
                        <span className="block truncate text-xs text-muted-foreground">
                          {account.blurb}
                        </span>
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          <p className="mt-6 text-center text-xs text-white/60">
            PayCore — mission-critical financial settlement platform.
          </p>
        </section>
      </div>
    </main>
  );
}
