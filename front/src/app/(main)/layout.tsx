'use client';

import { ReactNode } from 'react';
import Header from '@/components/common/Header';

export default function MainLayout({ children }: { children: ReactNode }) {
  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Header />
      <main style={{ flex: 1 }}>
        {children}
      </main>
    </div>
  );
}