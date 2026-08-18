'use client'

import { QueryClient, QueryClientProvider as TanStackQueryClientProvider } from '@tanstack/react-query'
import { useEffect, useState, type ReactNode } from 'react'

/**
 * socketClient.ts(React 트리 바깥, 브라우저 전용)에서 WS 이벤트 수신 시
 * React Query 캐시를 무효화하기 위한 참조.
 * 컴포넌트 마운트 시에만 채워지므로 SSR 시 요청 간에 공유되지 않는다.
 */
export const queryClientRef: { current: QueryClient | null } = { current: null }

export function QueryClientProvider({ children }: { children: ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: 1,
            refetchOnWindowFocus: false,
          },
        },
      })
  )

  useEffect(() => {
    queryClientRef.current = queryClient
    return () => {
      queryClientRef.current = null
    }
  }, [queryClient])

  return (
    <TanStackQueryClientProvider client={queryClient}>
      {children}
    </TanStackQueryClientProvider>
  )
}