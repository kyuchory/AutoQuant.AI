import { create } from 'zustand'
import type { ExecutionPayload } from '@/types/socket'

export type ExecutionItem = ExecutionPayload

interface ExecutionState {
  executions: ExecutionItem[]
  pushExecution: (item: ExecutionItem) => void
}

export const useExecutionStore = create<ExecutionState>((set) => ({
  executions: [],
  pushExecution: (item) =>
    set((state) => ({
      executions: [item, ...state.executions].slice(0, 30),
    })),
}))