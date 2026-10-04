import { API_URL } from '../config/api'

export interface CostCenterData {
  nome: string
  valor: number
}

export async function getCostCenters(
  historico = false,
): Promise<CostCenterData[]> {
  const query = historico ? "?historico=true" : "";

  const response = await fetch(`${API_URL}/api/centros-custo${query}`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error("Não foi possível carregar a análise financeira.");
  }

  return response.json();
}
