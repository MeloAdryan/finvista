import { API_URL } from '../config/api'

export interface CostCenterData {
  nome: string
  valor: number
}

export async function getCostCenters():
Promise<CostCenterData[]> {
  const response = await fetch(
    `${API_URL}/api/centros-custo`,
    {
      method: 'GET',
      credentials: 'include',
    },
  )

  if (!response.ok) {
    throw new Error(
      'Erro ao carregar os centros de custo',
    )
  }

  return response.json()
}