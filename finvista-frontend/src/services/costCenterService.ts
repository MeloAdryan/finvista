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
  export type CostBehavior = "FIXO" | "VARIAVEL" | "NAO_CLASSIFICADO";

  export type CostNature = "TAXAS" | "IMPOSTOS" | "PESSOAL" | "SERVICOS" | "MATERIAIS" | "OUTROS" | "NAO_CLASSIFICADO";

export interface CostFilters {
  inicio?: string;
  fim?: string;
  comportamento?: CostBehavior | "";
  natureza?: CostNature | "";
}

export interface CostAnalysis {
  inicio: string;
  fim: string;
  total: number;
  centros: CostCenterData[];
}

export interface CostClassification {
  categoria: string;
  comportamento: CostBehavior;
  natureza: CostNature;
}

async function costRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_URL}/api/centros-custo/${path}`, {
    ...options, credentials: "include",
  });
  if (!response.ok) {
    const error = await response.json().catch(() => null);
    throw new Error(error?.message || error?.mensagem || "Não foi possível consultar ou salvar os custos.");
  }
  return response.json();
}

export function getCostAnalysis(filters: CostFilters, signal?: AbortSignal): Promise<CostAnalysis> {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value); });
  return costRequest(`analise?${params.toString()}`, { signal });
}

export function getCostClassifications(signal?: AbortSignal): Promise<CostClassification[]> {
  return costRequest("classificacoes", { signal });
}
export function saveCostClassification(value: CostClassification): Promise<CostClassification> {
  return costRequest("classificacoes", {
    method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(value),
  });
}

