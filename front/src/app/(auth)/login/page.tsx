'use client';

import { useTranslation } from 'react-i18next';
import { useAuth } from '@/lib/hooks/useAuth';
import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuthStore } from '@/lib/store/authStore';
import Image from 'next/image';

export default function LoginPage() {
  const { t } = useTranslation();
  const { loginWithKakao } = useAuth();
  const router = useRouter();
  const isLoggedIn = useAuthStore((state) => state.isLoggedIn);

  // 이미 로그인되어 있으면 대시보드로 리다이렉트
  useEffect(() => {
    if (isLoggedIn) {
      router.replace('/dashboard');
    }
  }, [isLoggedIn, router]);

  if (isLoggedIn) {
    return null;
  }

  return (
    <div className="login-container stock-grid-bg">
      {/* Animated background elements */}
      <div className="bg-glow-1" />
      <div className="bg-glow-2" />
      <div className="bg-grid-lines" />

      {/* Floating candlestick-like decorations */}
      <div className="candle candle-1" />
      <div className="candle candle-2" />
      <div className="candle candle-3" />

      <div className="login-content animate-fade-in">
        {/* Logo area */}
        <div className="logo-section">
          <div className="logo-wrapper animate-pulse-glow">
            <Image
              src="/AutoQuant.AI_LOGO.png"
              alt="AutoQuant AI"
              width={80}
              height={80}
              priority
              style={{ objectFit: 'contain' }}
            />
          </div>
          <h1 className="login-title">{t('login.title')}</h1>
          <p className="login-subtitle">{t('login.subtitle')}</p>
        </div>

        {/* Description */}
        <p className="login-description">
          {t('login.description')}
        </p>

        {/* CTA Buttons */}
        <div className="cta-section">
          <button className="kakao-btn" onClick={loginWithKakao}>
            <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
              <path d="M12 3C6.48 3 2 6.58 2 11c0 2.77 1.82 5.22 4.56 6.62L5.8 20.5l4.16-2.48c.67.13 1.36.2 2.04.2 5.52 0 10-3.58 10-8S17.52 3 12 3z"/>
            </svg>
            {t('login.kakaoLogin')}
          </button>
        </div>

        {/* Footer note */}
        <div className="login-footer">
          <div className="footer-line" />
          <span className="footer-text">{t('login.startTrading')}</span>
          <div className="footer-line" />
        </div>
      </div>

      <style jsx>{`
        .login-container {
          min-height: 100vh;
          display: flex;
          align-items: center;
          justify-content: center;
          background-color: var(--bg-primary);
          position: relative;
          overflow: hidden;
        }

        /* Background glow effects */
        .bg-glow-1 {
          position: absolute;
          top: -20%;
          left: -10%;
          width: 60%;
          height: 60%;
          background: radial-gradient(circle, rgba(59, 130, 246, 0.08) 0%, transparent 70%);
          pointer-events: none;
        }

        .bg-glow-2 {
          position: absolute;
          bottom: -20%;
          right: -10%;
          width: 60%;
          height: 60%;
          background: radial-gradient(circle, rgba(6, 182, 212, 0.06) 0%, transparent 70%);
          pointer-events: none;
        }

        .bg-grid-lines {
          position: absolute;
          inset: 0;
          background-image:
            linear-gradient(rgba(59, 130, 246, 0.03) 1px, transparent 1px),
            linear-gradient(90deg, rgba(59, 130, 246, 0.03) 1px, transparent 1px);
          background-size: 60px 60px;
          pointer-events: none;
        }

        /* Floating candle-like decorations */
        .candle {
          position: absolute;
          width: 4px;
          border-radius: 2px;
          opacity: 0.15;
          pointer-events: none;
        }

        .candle-1 {
          top: 15%;
          right: 20%;
          height: 80px;
          background: var(--accent-green);
          animation: floatUp 6s ease-in-out infinite;
        }

        .candle-2 {
          top: 40%;
          right: 30%;
          height: 120px;
          background: var(--accent-red);
          animation: floatUp 8s ease-in-out infinite;
          animation-delay: 1s;
        }

        .candle-3 {
          top: 60%;
          right: 15%;
          height: 60px;
          background: var(--accent-green);
          animation: floatUp 7s ease-in-out infinite;
          animation-delay: 2s;
        }

        @keyframes floatUp {
          0%, 100% { transform: translateY(0); }
          50% { transform: translateY(-20px); }
        }

        .login-content {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 32px;
          padding: 48px;
          z-index: 1;
          max-width: 440px;
          width: 100%;
        }

        .logo-section {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 16px;
        }

        .logo-wrapper {
          width: 100px;
          height: 100px;
          display: flex;
          align-items: center;
          justify-content: center;
          border-radius: 24px;
          background: rgba(26, 31, 46, 0.6);
          border: 1px solid var(--border-color);
          padding: 12px;
        }

        .login-title {
          font-size: 2rem;
          font-weight: 800;
          background: var(--gradient-primary);
          -webkit-background-clip: text;
          -webkit-text-fill-color: transparent;
          background-clip: text;
          letter-spacing: -0.5px;
          text-align: center;
          margin: 0;
        }

        .login-subtitle {
          color: var(--text-secondary);
          font-size: 1rem;
          font-weight: 500;
          text-align: center;
          margin: 0;
        }

        .login-description {
          color: var(--text-muted);
          font-size: 0.9rem;
          line-height: 1.6;
          text-align: center;
          white-space: pre-line;
          margin: 0;
        }

        .cta-section {
          display: flex;
          flex-direction: column;
          gap: 12px;
          width: 100%;
        }

        .kakao-btn {
          display: flex;
          align-items: center;
          justify-content: center;
          gap: 10px;
          width: 100%;
          padding: 16px 24px;
          border: none;
          border-radius: 12px;
          background: #FEE500;
          color: #3C1E1E;
          font-size: 1rem;
          font-weight: 700;
          cursor: pointer;
          transition: all 0.3s ease;
          position: relative;
          overflow: hidden;
        }

        .kakao-btn::before {
          content: '';
          position: absolute;
          inset: 0;
          background: linear-gradient(135deg, rgba(255,255,255,0.2), transparent);
          opacity: 0;
          transition: opacity 0.3s ease;
        }

        .kakao-btn:hover {
          transform: translateY(-2px);
          box-shadow: 0 8px 25px rgba(254, 229, 0, 0.2);
        }

        .kakao-btn:hover::before {
          opacity: 1;
        }

        .kakao-btn:active {
          transform: translateY(0);
        }

        .login-footer {
          display: flex;
          align-items: center;
          gap: 16px;
          width: 100%;
        }

        .footer-line {
          flex: 1;
          height: 1px;
          background: var(--border-color);
        }

        .footer-text {
          color: var(--text-muted);
          font-size: 0.75rem;
          white-space: nowrap;
        }
      `}</style>
    </div>
  );
}