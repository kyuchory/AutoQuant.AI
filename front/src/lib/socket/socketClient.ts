'use client'

import { useAuthStore } from '@/lib/store/authStore'
import { useAssetStore } from '@/lib/store/assetStore'
import { useChartStore } from '@/lib/store/chartStore'
import { useExecutionStore } from '@/lib/store/executionStore'
import { useOrderProposalStore } from '@/lib/store/orderProposalStore'
import { queryClientRef } from '@/components/common/QueryClientProvider'
import { SOCKET_EVENTS } from './socketEvents'

const MAX_RECONNECT_DELAY = 30000
const DISCONNECTED_BADGE_THRESHOLD = 5 // 연속 재연결 실패 이 횟수 이상이면 UI에 끊김 배지 표시

class SocketClient {
  private ws: WebSocket | null = null
  private reconnectDelay = 1000
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private intentionalClose = false
  private consecutiveFailures = 0

  connect() {
    // 이미 연결된 상태면 무시
    if (this.ws && (this.ws.readyState === WebSocket.OPEN || this.ws.readyState === WebSocket.CONNECTING)) {
      console.log('⏭️ WebSocket 이미 연결됨, 무시')
      return
    }

    const token = useAuthStore.getState().accessToken
    if (!token) {
      console.warn('⚠️ accessToken 없음, WebSocket 연결 보류')
      return
    }

    this.intentionalClose = false
    const baseUrl = process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:8080/ws'
    console.log('→ WebSocket 연결 시도...')
    this.ws = new WebSocket(`${baseUrl}?token=${token}`)

    this.ws.onopen = () => {
      console.log('✅ WebSocket 연결됨')
      this.reconnectDelay = 1000
      this.consecutiveFailures = 0
      useChartStore.getState().setConnectionStatus('connected')
    }

    this.ws.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data)
        const { type, payload } = message
        console.log('📩 WebSocket 수신:', type, payload)

        switch (type) {
          case SOCKET_EVENTS.PRICE_TICK:
            // 전체 종목 실시간 시세 브로드캐스트 (조건 발동과 무관한 단순 시세 틱)
            useAssetStore.getState().updateHoldingPrice(payload.stockCode, payload.currentPrice)
            useChartStore.getState().updatePrice(payload.stockCode, payload.currentPrice, payload.changeRate ?? 0, payload.volume ?? 0)
            break

          case SOCKET_EVENTS.PRICE_ALERT:
          case SOCKET_EVENTS.AI_SCORE_ALERT:
          case SOCKET_EVENTS.ORDER_FAILED:
            // 조건(conditionId) 충족/실패 알림. 토스트 UI가 아직 없어 우선 로그로만 노출한다.
            // TODO: 토스트 컴포넌트 도입 시 여기서 호출 (docs/frontend.md §5.2)
            console.info(`🔔 [${type}]`, payload)
            break

          case SOCKET_EVENTS.ORDER_FILLED:
            useAssetStore.getState().applyOrderFilled(payload)
            // 조건 매칭 워커가 비동기로 체결한 주문은 대시보드/포트폴리오가 구독 중인
            // React Query 캐시(['assets'], ['histories'])를 직접 갱신해야 화면에 반영된다.
            queryClientRef.current?.invalidateQueries({ queryKey: ['assets'] })
            queryClientRef.current?.invalidateQueries({ queryKey: ['histories'] })
            break

          case SOCKET_EVENTS.EXECUTION:
            useExecutionStore.getState().pushExecution(payload)
            break

          case SOCKET_EVENTS.ORDER_PROPOSAL:
            useOrderProposalStore.getState().openProposal(payload)
            break

          case SOCKET_EVENTS.REPORT_READY:
            // reports/[stockCode]/page.tsx의 실제 쿼리 키는 ['report', stockCode] (단수형)
            queryClientRef.current?.invalidateQueries({ queryKey: ['report', payload.stockCode] })
            break

          default:
            console.warn('⚠️ 알 수 없는 WebSocket 메시지 타입:', type, payload)
            break
        }
      } catch (e) {
        console.warn('WebSocket 메시지 파싱 실패:', e)
      }
    }

    this.ws.onclose = () => {
      console.log('WebSocket 연결 종료')
      if (!this.intentionalClose) {
        this.consecutiveFailures += 1
        if (this.consecutiveFailures >= DISCONNECTED_BADGE_THRESHOLD) {
          useChartStore.getState().setConnectionStatus('disconnected')
        }
        // ±20% 지터 — 서버 재시작 시 다수 클라이언트의 동시 재연결(thundering herd) 방지
        const jitter = this.reconnectDelay * (0.8 + Math.random() * 0.4)
        console.log(`→ ${Math.round(jitter)}ms 후 재연결... (연속 실패 ${this.consecutiveFailures}회)`)
        this.reconnectTimer = setTimeout(() => this.connect(), jitter)
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, MAX_RECONNECT_DELAY)
      }
    }

    this.ws.onerror = () => {
      // onerror는 Event 객체만 전달하므로 상세 정보를 제공하지 않음
      // 실제 에러 처리는 onclose에서 자동 재연결로 수행됨
    }
  }

  disconnect() {
    console.log('🛑 WebSocket 명시적 종료')
    this.intentionalClose = true
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
  }
}

export const socketClient = new SocketClient()
