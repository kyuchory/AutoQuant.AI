'use client';

import { useTranslation } from 'react-i18next';
import { useAuth } from '@/lib/hooks/useAuth';

export default function DashboardPage() {
  const { t } = useTranslation();
  const { user } = useAuth();

  return (
    <div style={{
      padding: '40px 24px',
      maxWidth: '1200px',
      margin: '0 auto',
    }}>
      {/* Welcome card */}
      <div className="glass-card animate-fade-in" style={{
        borderRadius: '16px',
        padding: '32px',
        marginBottom: '24px',
      }}>
        <h1 style={{
          margin: '0 0 8px 0',
          fontSize: '1.5rem',
          fontWeight: 700,
          color: 'var(--text-primary)',
        }}>
          {t('dashboard.welcomeMessage', { nickname: user?.nickname || '' })}
        </h1>
        <p style={{
          margin: '0',
          color: 'var(--text-secondary)',
          fontSize: '0.9rem',
        }}>
          {user?.email}
        </p>
      </div>

      {/* Placeholder for future dashboard content */}
      <div className="stock-grid-bg" style={{
        borderRadius: '16px',
        padding: '48px',
        border: '1px solid var(--border-color)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '16px',
        minHeight: '300px',
      }}>
        <p style={{
          color: 'var(--text-muted)',
          fontSize: '1rem',
          textAlign: 'center',
        }}>
          {t('dashboard.title')}
        </p>
      </div>
    </div>
  );
}