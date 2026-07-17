'use client';

import { useEffect, useState, createContext, useContext, ReactNode, startTransition } from 'react';
import axios from 'axios';
import { useAuthStore } from '@/lib/store/authStore';
import type { RefreshResponse } from '@/types/auth';
import type { ApiResponse } from '@/types/api';

interface AuthContextValue {
  isReady: boolean;
}

const AuthContext = createContext<AuthContextValue>({ isReady: false });
export const useAuthReady = () => useContext(AuthContext);

export default function AuthProvider({ children }: { children: ReactNode }) {
  const { accessToken, setAuth } = useAuthStore();
  const [isReady, setIsReady] = useState(false);

  useEffect(() => {
    // 이미 accessToken이 메모리에 있으면 바로 통과
    if (accessToken) {
      startTransition(() => {
        setIsReady(true);
      });
      return;
    }

    // accessToken이 없으면 Refresh Token으로 복구 시도
    const baseUrl = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api/v1';

    axios
      .post<ApiResponse<RefreshResponse>>(
        `${baseUrl}/auth/refresh`,
        {},
        { withCredentials: true }
      )
      .then(({ data }) => {
        if (data.success && data.data) {
          const { accessToken: newToken, user } = data.data;
          setAuth(newToken, user);
        }
      })
      .catch(() => {
        // refresh 실패 → 아무것도 하지 않음
        // middleware.ts가 refreshToken 쿠키 없음을 감지해 /login으로 보냄
      })
      .finally(() => {
        startTransition(() => {
          setIsReady(true);
        });
      });
  }, []); // 마운트 시 1회만 실행

  if (!isReady) {
    return null; // 로딩 중에는 빈 화면 (블로킹)
  }

  return (
    <AuthContext.Provider value={{ isReady }}>
      {children}
    </AuthContext.Provider>
  );
}