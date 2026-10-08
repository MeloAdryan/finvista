import { API_URL } from "../config/api";
import type { CostBehavior, CostNature } from "./costCenterService";
export interface ExpenseFilters {
  inicio?: string;
  fim?: string;
  centroCusto?: string;
  categoria?: string;
  comportamento?: CostBehavior | "";
  natureza?: CostNature | "";
}
export interface ExpenseRow {
  nome: string;
  atual: number;
  anterior: number;
  diferenca: number;
}
export interface ExpenseAnalysis {
  inicio: string;
  fim: string;
  inicioAnterior: string;
  fimAnterior: string;
  centroCusto: string | null;
  categoria: string | null;
  comportamento: string | null;
  natureza: string | null;
  total: number;
  totalAnterior: number;
  centrosDisponiveis: string[];
  categoriasDisponiveis: string[];
  centros: ExpenseRow[];
  categorias: ExpenseRow[];
  itens: {
    id: number;
    data: string;
    descricao: string;
    valorSelecionado: number;
    valorIntegral: number;
  }[];
}
export async function getExpenseAnalysis(
  filtros: ExpenseFilters,
  signal: AbortSignal,
): Promise<ExpenseAnalysis> {
  const params = new URLSearchParams();
  Object.entries(filtros).forEach(([k, v]) => {
    if (v) params.set(k, v);
  });
  const r = await fetch(`${API_URL}/api/despesas/analise?${params}`, {
    credentials: "include",
    signal,
    cache: "no-store",
  });
  if (!r.ok)
    throw new Error(
      r.status === 401
        ? "Entre novamente para consultar despesas."
        : "Não foi possível consultar despesas. Confira os filtros e tente novamente.",
    );
  return r.json();
}
