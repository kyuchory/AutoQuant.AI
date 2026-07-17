'use client';

import { Suspense } from 'react';
import { useEffect, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuthStore } from '@/lib/store/authStore';
import * as authApi from '@/lib/api/auth';

function KakaoCallbackInner() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { setAuth } = useAuthStore();
  const calledRef = useRef(false); // 중복 요청 방지 플래그

  useEffect(() => {
    const code = searchParams.get('code');

    if (!code) {
      router.push('/login');
      return;
    }

    // 중복 요청 방지 (Strict Mode 2회 실행 + 사용자 연타 대응)
    if (calledRef.current) return;
    calledRef.current = true;

    // POST /api/v1/auth/login → 카카오 인가 코드로 로그인
    authApi.login('KAKAO', code)
      .then((response) => {
        if (response.success && response.data) {
          const { accessToken, user } = response.data;
          setAuth(accessToken, user);
          router.push('/dashboard');
        } else {
          router.push('/login');
        }
      })
      .catch(() => {
        router.push('/login');
      });
  }, [searchParams, router, setAuth]);

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      fontFamily: 'sans-serif',
      backgroundColor: '#FEE500',
      color: '#3C1E1E'
    }}>
      <p>로그인 처리 중...</p>
    </div>
  );
}

export default function KakaoCallbackPage() {
  return (
    <Suspense fallback={
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
        fontFamily: 'sans-serif',
        backgroundColor: '#FEE500',
        color: '#3C1E1E'
      }}>
        <p>로그인 처리 중...</p>
      </div>
    }>
      <KakaoCallbackInner />
    </Suspense>
  );
}