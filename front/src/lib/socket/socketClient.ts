'use client'

import { useAuthStore } from '@/lib/store/authStore'
import { useAssetStore } from '@/lib/store/assetStore'
import { useChartStore } from '@/lib/store/chartStore'
import { SOCKET_EVENTS } from './socketEvents'

class SocketClient {
  private ws: WebSocket | null = null
  private reconnectDelay = 1000
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private intentionalClose = false

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
    }

    this.ws.onmessage = (event) => {
      try {
        const message = JSON.parse(event.data)
        const { type, payload } = message
        console.log('📩 WebSocket 수신:', type, payload)

        switch (type) {
          case SOCKET_EVENTS.PRICE_ALERT:
            useAssetStore.getState().updateHoldingPrice(payload.stockCode, payload.currentPrice)
            useChartStore.getState().updatePrice(payload.stockCode, payload.currentPrice)
            break

          case SOCKET_EVENTS.ORDER_FILLED:
            useAssetStore.getState().applyOrderFilled(payload)
            break

          default:
            break
        }
      } catch (e) {
        console.warn('WebSocket 메시지 파싱 실패:', e)
      }
    }

    this.ws.onclose = () => {
      console.log('WebSocket 연결 종료')
      if (!this.intentionalClose) {
        console.log(`→ ${this.reconnectDelay}ms 후 재연결...`)
        this.reconnectTimer = setTimeout(() => this.connect(), this.reconnectDelay)
        this.reconnectDelay = Math.min(this.reconnectDelay * 2, 30000)
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