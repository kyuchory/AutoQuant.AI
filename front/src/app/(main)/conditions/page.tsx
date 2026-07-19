'use client';

import { useTranslation } from 'react-i18next';

export default function ConditionsPage() {
  const { t } = useTranslation();

  return (
    <div style={{ padding: '40px 24px', maxWidth: '1200px', margin: '0 auto' }}>
      <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '24px', color: 'var(--text-primary)' }}>
        {t('conditions.title')}
      </h1>
      <div className="glass-card" style={{ borderRadius: '16px', padding: '48px', textAlign: 'center' }}>
        <p style={{ color: 'var(--text-muted)' }}>{t('conditions.noConditions')}</p>
      </div>
    </div>
  );
}