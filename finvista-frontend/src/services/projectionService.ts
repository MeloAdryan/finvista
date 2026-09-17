import { API_URL } from '../config/api'

export interface ProjectionData {
  mes: string
  receita: number
  despesa: number
  resultado: number
  saldo: number
}

export async function getProjection(): Promise<ProjectionData[]> {
const response = await fetch(
  `${API_URL}/api/projecao`
)

  if (!response.ok) {
    throw new Error('Erro ao carregar a projeção financeira')
  }

  return response.json()
}