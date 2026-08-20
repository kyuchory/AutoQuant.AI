'use client'

import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getAssets } from '@/lib/api/assets'
import { useAssetStore } from '@/lib/store/assetStore'
import { useAuthStore } from '@/lib/store/authStore'
import type { AssetSummaryResponse } from '@/types/assets'

/**
 * GET /api/v1/assets 조회 후 assetStore를 초기화한다 (docs/frontend.md §3.3).
 *
 * queryKey는 portfolio 페이지의 ['assets'] 쿼리와 동일하게 맞춰 React Query 캐시를 공유한다 —
 * socketClient.ts가 ORDER_FILLED 수신 시 invalidateQueries(['assets'])를 호출하면
 * 이 훅도 함께 리페치되어 assetStore가 최신 상태로 재동기화된다.
 */
export function useAssets() {
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn)
  const setAssets = useAssetStore((s) => s.setAssets)

  const query = useQuery<AssetSummaryResponse>({
    queryKey: ['assets'],
    queryFn: async () => {
      const res = await getAssets()
      if (!res.success || !res.data) throw new Error(res.message)
      return res.data
    },
    enabled: isLoggedIn,
    staleTime: 10_000,
    refetchOnWindowFocus: true,
  })

  useEffect(() => {
    if (query.data) setAssets(query.data)
  }, [query.data, setAssets])

  return query
}
