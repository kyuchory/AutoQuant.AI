'use client';

import { useTranslation } from 'react-i18next';
import { Suspense } from 'react';
import { useEffect, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuthStore } from '@/lib/store/authStore';
import * as authApi from '@/lib/api/auth';
import Image from 'next/image';

function KakaoCallbackInner() {
  const { t } = useTranslation();
  const router = useRouter();
  const searchParams = useSearchParams();
  const { setAuth } = useAuthStore();
  const calledRef = useRef(false);

  useEffect(() => {
    const code = searchParams.get('code');

    if (!code) {
      router.push('/login');
      return;
    }

    if (calledRef.current) return;
    calledRef.current = true;

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
    <div className="callback-container">
      <div className="callback-content animate-fade-in">
        <div className="logo-wrapper animate-pulse-glow">
          <Image
            src="/AutoQuant.AI_LOGO.png"
            alt="AutoQuant AI"
            width={64}
            height={64}
            style={{ objectFit: 'contain' }}
          />
        </div>
        <div className="spinner" />
        <p className="callback-text">{t('callback.processing')}</p>
        <p className="callback-sub">{t('callback.redirecting')}</p>
      </div>

      <style jsx>{`
        .callback-container {
          min-height: 100vh;
          display: flex;
          align-items: center;
          justify-content: center;
          background-color: var(--bg-primary);
          position: relative;
          overflow: hidden;
        }

        .callback-content {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 24px;
          z-index: 1;
        }

        .logo-wrapper {
          width: 80px;
          height: 80px;
          display: flex;
          align-items: center;
          justify-content: center;
          border-radius: 20px;
          background: rgba(26, 31, 46, 0.6);
          border: 1px solid var(--border-color);
          padding: 10px;
        }

        .spinner {
          width: 40px;
          height: 40px;
          border: 3px solid var(--border-color);
          border-top-color: var(--accent-blue);
          border-radius: 50%;
          animation: spin 0.8s linear infinite;
        }

        @keyframes spin {
          to { transform: rotate(360deg); }
        }

        .callback-text {
          color: var(--text-primary);
          font-size: 1.1rem;
          font-weight: 600;
          margin: 0;
        }

        .callback-sub {
          color: var(--text-muted);
          font-size: 0.9rem;
          margin: 0;
        }
      `}</style>
    </div>
  );
}

export default function KakaoCallbackPage() {
  return (
    <Suspense fallback={
      <div style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: 'var(--bg-primary)',
        color: 'var(--text-primary)',
        fontFamily: 'sans-serif'
      }}>
        <p>Loading...</p>
      </div>
    }>
      <KakaoCallbackInner />
    </Suspense>
  );
}