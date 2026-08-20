'use client';

import { ReactNode, useEffect } from 'react';
import Header from '@/components/common/Header';
import OrderProposalModal from '@/components/common/OrderProposalModal';
import { useAuthStore } from '@/lib/store/authStore';
import { socketClient } from '@/lib/socket/socketClient';
import { useAssets } from '@/lib/hooks/useAssets';

export default function MainLayout({ children }: { children: ReactNode }) {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  useAssets();

  // isLoggedIn(로그인/로그아웃)에만 의존한다 — accessToken은 401 인터셉터가 setAccessToken()으로
  // 로테이션할 때마다 바뀌므로, 그걸 의존성으로 두면 매 토큰 갱신(~1시간)마다 소켓이 불필요하게
  // 끊겼다 재연결되어 실시간 시세에 순간 공백이 생긴다. socketClient.connect()는 호출 시점에
  // useAuthStore.getState().accessToken으로 최신 토큰을 직접 읽으므로 여기서 넘겨줄 필요가 없다.
  useEffect(() => {
    if (!isLoggedIn) return;
    socketClient.connect();
    return () => socketClient.disconnect();
  }, [isLoggedIn]);

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      <Header />
      <main style={{ flex: 1, minHeight: 0, overflow: 'hidden' }}>
        {children}
      </main>
      {/* 실시간 반자동 매매 제안 모달 (어느 화면에서든 팝업) */}
      <OrderProposalModal />
    </div>
  );
}
