import { API_URL } from "../config/api";

export type SpendingGoalStatus = "NORMAL" | "ALERTA" | "EXCEDIDA";

export interface SpendingGoal {
  id: number;
  tipo: string;
  dataInicio: string;
  dataFim: string;
  valorLimite: number;
  gastoAtual: number;
  percentualUtilizado: number;
  saldoMeta: number;
  percentualAlerta: number;
  status: SpendingGoalStatus;

  categoria: string | null;
  centroCusto: string | null;
}

export interface CreateSpendingGoalRequest {
  tipo: string;
  dataInicio: string;
  dataFim: string;
  valorLimite: number;
  percentualAlerta: number;

  categoria: string | null;
  centroCusto: string | null;
}

export interface SpendingGoalFilters {
  centroCusto?: string | null;
  categoria?: string | null;
}

export async function listarMetas(
  filtros: SpendingGoalFilters = {},
): Promise<SpendingGoal[]> {
  const params = new URLSearchParams();

  const centroCusto = filtros.centroCusto?.trim();
  const categoria = filtros.categoria?.trim();

  if (centroCusto) {
    params.set("centroCusto", centroCusto);
  }

  if (categoria) {
    params.set("categoria", categoria);
  }

  const queryString = params.toString();

  const url = queryString
    ? `${API_URL}/api/metas-gastos?${queryString}`
    : `${API_URL}/api/metas-gastos`;

  const response = await fetch(url, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error("Não foi possível carregar as metas.");
  }

  return response.json();
}

export async function criarMeta(
  dados: CreateSpendingGoalRequest,
): Promise<void> {
  const response = await fetch(`${API_URL}/api/metas-gastos`, {
    method: "POST",
    credentials: "include",

    headers: {
      "Content-Type": "application/json",
    },

    body: JSON.stringify(dados),
  });

  if (!response.ok) {
    const mensagem = await response.text().catch(() => "");

    throw new Error(
      mensagem || "Não foi possível criar a meta.",
    );
  }
}