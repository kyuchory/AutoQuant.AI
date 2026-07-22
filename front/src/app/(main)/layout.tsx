'use client';

import { ReactNode, useEffect } from 'react';
import Header from '@/components/common/Header';
import { useAuthStore } from '@/lib/store/authStore';
import { socketClient } from '@/lib/socket/socketClient';

export default function MainLayout({ children }: { children: ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken);

  useEffect(() => {
    socketClient.connect();
    return () => socketClient.disconnect();
  }, [accessToken]);

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Header />
      <main style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        {children}
      </main>
    </div>
  );
}
