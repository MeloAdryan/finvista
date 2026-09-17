import { API_URL } from '../config/api'

export interface DashboardData {
  receita: number
  despesa: number
  resultado: number
  margem: number
}

export async function getDashboard(): Promise<DashboardData> {
  const response = await fetch(`${API_URL}/api/dashboard`)

  if (!response.ok) {
    throw new Error('Erro ao carregar os dados do dashboard')
  }

  return response.json()
}