'use client';

import { useAuthStore } from '@/lib/store/authStore';
import { useRouter } from 'next/navigation';
import * as authApi from '@/lib/api/auth';

export function useAuth() {
  const { accessToken, user, isLoggedIn, setAuth, clearAuth } = useAuthStore();
  const router = useRouter();

  const loginWithKakao = () => {
    const clientId = process.env.NEXT_PUBLIC_KAKAO_CLIENT_ID || '';
    const redirectUri = process.env.NEXT_PUBLIC_KAKAO_REDIRECT_URI || 'http://localhost:3000/callback/kakao';
    window.location.href =
      `https://kauth.kakao.com/oauth/authorize?response_type=code&client_id=${clientId}&redirect_uri=${redirectUri}`;
  };

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } finally {
      clearAuth();
      router.push('/login');
    }
  };

  return {
    accessToken,
    user,
    isLoggedIn,
    loginWithKakao,
    logout: handleLogout,
    setAuth,
  };
}