import { API_URL } from '../config/api'

export interface CashFlowData {
  mes: string
  saldoInicial: number
  entradas: number
  saidas: number
  saldoFinal: number
}

export async function getCashFlow(): Promise<CashFlowData[]> {
  const response = await fetch(`${API_URL}/api/fluxo-caixa`)

  if (!response.ok) {
    throw new Error('Erro ao carregar o fluxo de caixa')
  }

  return response.json()
}