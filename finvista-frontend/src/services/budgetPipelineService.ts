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

export async function getBudgetPipeline(): Promise<BudgetPipelineData[]> {
  const response = await fetch(`${API_URL}/api/orcamentos`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error("Erro ao carregar os orçamentos");
  }

  return response.json();
}

export async function createBudget(
  dados: CreateBudgetData,
): Promise<BudgetPipelineData> {
  const response = await fetch(`${API_URL}/api/orcamentos`, {
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