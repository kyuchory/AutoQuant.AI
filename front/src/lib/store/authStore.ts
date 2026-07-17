import { create } from 'zustand';
import { UserInfo } from '@/types/auth';

interface AuthState {
  accessToken: string | null;
  user: UserInfo | null;
  isLoggedIn: boolean;
  setAuth: (accessToken: string, user: UserInfo) => void;
  clearAuth: () => void;
  setAccessToken: (token: string) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  isLoggedIn: false,

  setAuth: (accessToken, user) => set({
    accessToken,
    user,
    isLoggedIn: true,
  }),

  clearAuth: () => set({
    accessToken: null,
    user: null,
    isLoggedIn: false,
  }),

  setAccessToken: (accessToken) => set({ accessToken }),
}));