import { API_URL } from '../config/api'

export interface BudgetPipelineData {
  id: number
  cliente: string
  valor: number
  status: string
  probabilidade: number
  valorPonderado: number
}

export async function getBudgetPipeline():
Promise<BudgetPipelineData[]> {
  const response = await fetch(
    `${API_URL}/api/orcamentos`,
    {
      method: 'GET',
      credentials: 'include',
    },
  )

  if (!response.ok) {
    throw new Error(
      'Erro ao carregar os orçamentos',
    )
  }

  return response.json()
}