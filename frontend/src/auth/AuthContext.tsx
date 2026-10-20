import { useQueryClient } from '@tanstack/react-query';
import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { SESSION_EXPIRED_EVENT, tokenStore } from '../api/http';

interface AuthState {
  isAuthenticated: boolean;
  signIn: (accessToken: string) => void;
  signOut: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [token, setToken] = useState(() => tokenStore.get());

  const signIn = useCallback((accessToken: string) => {
    tokenStore.set(accessToken);
    setToken(accessToken);
  }, []);

  const signOut = useCallback(() => {
    tokenStore.clear();
    setToken(null);
    // Cached data belongs to the previous user's business
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    window.addEventListener(SESSION_EXPIRED_EVENT, signOut);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, signOut);
  }, [signOut]);

  const value = useMemo(() => ({ isAuthenticated: token !== null, signIn, signOut }), [token, signIn, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const state = useContext(AuthContext);
  if (!state) throw new Error('useAuth must be used inside AuthProvider');
  return state;
}
