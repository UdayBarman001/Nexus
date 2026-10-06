import { create } from 'zustand';
import { User, AuthResponse } from '@/types';

interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  setAuth: (authData: AuthResponse) => void;
  setUser: (user: User) => void;
  logout: () => void;
  checkAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  token: null,
  isAuthenticated: false,
  isLoading: true,

  setAuth: (authData: AuthResponse) => {
    if (typeof window !== 'undefined') {
      localStorage.setItem('nexus_token', authData.token);
      localStorage.setItem('nexus_user', JSON.stringify(authData.user));
    }
    set({
      user: authData.user,
      token: authData.token,
      isAuthenticated: true,
      isLoading: false,
    });
  },

  setUser: (user: User) => {
    if (typeof window !== 'undefined') {
      localStorage.setItem('nexus_user', JSON.stringify(user));
    }
    set({ user });
  },

  logout: () => {
    if (typeof window !== 'undefined') {
      localStorage.removeItem('nexus_token');
      localStorage.removeItem('nexus_user');
    }
    set({
      user: null,
      token: null,
      isAuthenticated: false,
      isLoading: false,
    });
  },

  checkAuth: () => {
    if (typeof window !== 'undefined') {
      const token = localStorage.getItem('nexus_token');
      const userStr = localStorage.getItem('nexus_user');
      if (token && userStr) {
        try {
          const user = JSON.parse(userStr) as User;
          set({
            user,
            token,
            isAuthenticated: true,
            isLoading: false,
          });
          return;
        } catch {
          localStorage.removeItem('nexus_token');
          localStorage.removeItem('nexus_user');
        }
      }
    }
    set({
      user: null,
      token: null,
      isAuthenticated: false,
      isLoading: false,
    });
  },
}));
