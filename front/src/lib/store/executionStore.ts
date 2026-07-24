import { create } from 'zustand'

export interface ExecutionItem {
  stockCode: string
  price: number
  volume: number
  changeRate: number
  accumulatedVolume: number
  time: string
  sign: string
}

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