import { API_URL } from "../config/api";

export interface CategoryChange {
  categoria: string;
  valorAtual: number;
  valorAnterior: number;
  diferenca: number;
}

export interface FinancialChangesData {
  mesAtual: string;
  mesAnterior: string;
  despesaAtual: number;
  despesaAnterior: number;
  diferenca: number;
  variacaoPercentual: number | null;
  lancamentosAtual: number;
  lancamentosAnterior: number;
  categorias: CategoryChange[];
}

export async function getFinancialChanges(
  signal: AbortSignal,
): Promise<FinancialChangesData> {
  const resposta = await fetch(`${API_URL}/api/dashboard/mudancas`, {
    credentials: "include",
    signal,
  });
  if (!resposta.ok) {
    throw new Error("Não foi possível carregar as mudanças das despesas.");
  }
  return resposta.json();
}
