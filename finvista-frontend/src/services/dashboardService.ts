import { API_URL } from '../config/api'

export interface DashboardData {
  receita: number;
  despesa: number;
  resultado: number;
  margem: number;

  receitaMesAnterior?: number | null;
  despesaMesAnterior?: number | null;
  resultadoMesAnterior?: number | null;
  margemMesAnterior?: number | null;

  variacaoReceita?: number | null;
  variacaoDespesa?: number | null;
  variacaoResultado?: number | null;
}

export async function getDashboard():
Promise<DashboardData> {
  const response = await fetch(
    `${API_URL}/api/dashboard`,
    {
      method: 'GET',
      credentials: 'include',
    },
  )

  if (!response.ok) {
    throw new Error(
      'Erro ao carregar os dados do dashboard',
    )
  }

  return response.json()
}