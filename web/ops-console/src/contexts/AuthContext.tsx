import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { User, UserRole } from '../types/api';
import { paycoreApi } from '../api/paycoreApi';
import { setToken, clearToken } from '../api/client';

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (credentials: { username: string; password: string }) => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (role: UserRole) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const initAuth = async () => {
      try {
        const data = await paycoreApi.refresh();
        if (data?.accessToken) {
          setToken(data.accessToken);
          setUser({
            id: data.userId || '1',
            username: data.username || 'admin',
            email: data.email || 'admin@paycore.dev',
            fullName: data.fullName || 'Platform Administrator',
            roles: data.roles || ['ADMIN'],
            active: true,
            createdAt: new Date().toISOString(),
          });
        }
      } catch {
        clearToken();
        setUser(null);
      } finally {
        setIsLoading(false);
      }
    };

    initAuth();
  }, []);

  const login = useCallback(async (credentials: { username: string; password: string }) => {
    const data = await paycoreApi.login(credentials);
    setToken(data.accessToken);
    setUser({
      id: data.userId || '1',
      username: data.username || credentials.username,
      email: data.email || `${credentials.username}@paycore.dev`,
      fullName: data.fullName || credentials.username,
      roles: data.roles || ['ADMIN'],
      active: true,
      createdAt: new Date().toISOString(),
    });
  }, []);

  const logout = useCallback(async () => {
    try {
      await paycoreApi.logout();
    } catch {
      // Ignore network errors on logout
    } finally {
      clearToken();
      setUser(null);
    }
  }, []);

  const hasRole = useCallback(
    (role: UserRole) => {
      if (!user) return false;
      return user.roles.includes(role);
    },
    [user]
  );

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        logout,
        hasRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
