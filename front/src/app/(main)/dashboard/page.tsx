'use client';

import { useAuth } from '@/lib/hooks/useAuth';

export default function DashboardPage() {
  const { user, logout } = useAuth();

  return (
    <div style={{
      padding: '40px',
      fontFamily: 'sans-serif',
      maxWidth: '800px',
      margin: '0 auto'
    }}>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '40px'
      }}>
        <h1 style={{ color: '#3C1E1E' }}>AutoQuant AI</h1>
        <button
          onClick={logout}
          style={{
            backgroundColor: '#3C1E1E',
            color: '#FEE500',
            border: 'none',
            borderRadius: '8px',
            padding: '10px 24px',
            cursor: 'pointer',
            fontWeight: 'bold'
          }}
        >
          로그아웃
        </button>
      </div>
      <div style={{
        backgroundColor: '#FEE500',
        borderRadius: '16px',
        padding: '32px',
        marginBottom: '24px'
      }}>
        <h1 style={{ margin: '0 0 8px 0', color: '#3C1E1E' }}>
          {user?.nickname}님 환영합니다! 👋
        </h1>
        <p style={{ margin: '0', color: '#3C1E1E', opacity: '0.8' }}>
          {user?.email}
        </p>
      </div>
    </div>
  );
}