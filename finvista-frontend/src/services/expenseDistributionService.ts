import { API_URL } from '../config/api'

export interface ExpenseDistributionData {
  categoria: string
  valor: number
}

export async function getExpenseDistribution():
Promise<ExpenseDistributionData[]> {
  const response = await fetch(
    `${API_URL}/api/distribuicao-despesas`,
    {
      method: 'GET',
      credentials: 'include',
    },
  )

  if (!response.ok) {
    throw new Error(
      'Erro ao carregar a distribuição das despesas',
    )
  }

  return response.json()
}