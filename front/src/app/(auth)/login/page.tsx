'use client';

import { useAuth } from '@/lib/hooks/useAuth';

export default function LoginPage() {
  const { loginWithKakao } = useAuth();

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: '100vh',
      backgroundColor: '#FEE500',
      fontFamily: 'sans-serif'
    }}>
      <h1 style={{ fontSize: '2rem', fontWeight: 'bold', marginBottom: '2rem', color: '#3C1E1E' }}>
        AutoQuant AI
      </h1>
      <p style={{ marginBottom: '3rem', color: '#3C1E1E', fontSize: '1.1rem' }}>
        AI 기반 실시간 모의투자 플랫폼
      </p>
      <button
        onClick={loginWithKakao}
        style={{
          backgroundColor: '#3C1E1E',
          color: '#FEE500',
          border: 'none',
          borderRadius: '12px',
          padding: '16px 48px',
          fontSize: '1.2rem',
          fontWeight: 'bold',
          cursor: 'pointer',
          transition: 'transform 0.2s',
        }}
        onMouseEnter={(e) => (e.currentTarget.style.transform = 'scale(1.05)')}
        onMouseLeave={(e) => (e.currentTarget.style.transform = 'scale(1)')}
      >
        카카오로 시작하기
      </button>
    </div>
  );
}