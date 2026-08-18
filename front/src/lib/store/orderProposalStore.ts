'use client'

import { create } from 'zustand'
import type { OrderProposalPayload } from '@/types/socket'

interface OrderProposalState {
  proposal: OrderProposalPayload | null
  isOpen: boolean
  openProposal: (payload: OrderProposalPayload) => void
  closeProposal: () => void
}

export const useOrderProposalStore = create<OrderProposalState>((set) => ({
  proposal: null,
  isOpen: false,
  openProposal: (payload) => set({ proposal: payload, isOpen: true }),
  closeProposal: () => set({ isOpen: false, proposal: null }),
}))
