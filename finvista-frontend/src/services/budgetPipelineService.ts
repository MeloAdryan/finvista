import { getFinancialView, type ViewFilter } from "./financialViewService";
import { apiFetch } from "../config/http";
import { API_URL } from "../config/api";

export interface BudgetPipelineData {
  id: number;
  nome: string;
  centroCusto: string | null;
  categoria: string | null;
  valorPlanejado: number;
  valorUtilizado: number;
  valorDisponivel: number;
  percentualUtilizado: number;
  dataInicio: string;
  dataFim: string;
}

export interface CreateBudgetData {
  nome: string;
  centroCusto: string | null;
  categoria: string | null;
  valorPlanejado: number;
  dataInicio: string;
  dataFim: string;
}

export async function getBudgetPipeline(
  viewFilter?: ViewFilter,
): Promise<BudgetPipelineData[]> {
  const response = await apiFetch(`${API_URL}/api/orcamentos`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error("Erro ao carregar os orçamentos");
  }

  const configs: BudgetPipelineData[] = await response.json();
  if (!viewFilter) return configs;
  const source = await getFinancialView(
    viewFilter,
    new AbortController().signal,
  );
  return configs
    .filter((c) => source.orcamentos.some((b) => b.id === c.id))
    .map((c) => {
      const b = source.orcamentos.find((b) => b.id === c.id)!;
      const spent = Number(b.consumoPeriodo);
      return {
        ...c,
        valorUtilizado: spent,
        valorDisponivel: Math.round((c.valorPlanejado - spent) * 100) / 100,
        percentualUtilizado: Number(b.percentual ?? 0),
      };
    });
}

export async function createBudget(
  dados: CreateBudgetData,
): Promise<BudgetPipelineData> {
  const response = await apiFetch(`${API_URL}/api/orcamentos`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(dados),
  });

  if (!response.ok) {
    throw new Error("Erro ao cadastrar o orçamento");
  }

  return response.json();
}
