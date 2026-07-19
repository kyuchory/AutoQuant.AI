'use client';

import { useTranslation } from 'react-i18next';
import { useParams } from 'next/navigation';

export default function ReportPage() {
  const { t } = useTranslation();
  const params = useParams();
  const stockCode = params.stockCode as string;

  return (
    <div style={{ padding: '40px 24px', maxWidth: '1200px', margin: '0 auto' }}>
      <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '24px', color: 'var(--text-primary)' }}>
        {t('reports.title')} - {stockCode}
      </h1>
      <div className="glass-card" style={{ borderRadius: '16px', padding: '48px', textAlign: 'center' }}>
        <p style={{ color: 'var(--text-muted)' }}>{t('reports.noReport')}</p>
      </div>
    </div>
  );
}