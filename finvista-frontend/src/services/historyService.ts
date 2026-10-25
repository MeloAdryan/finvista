import { historyForFilter } from "./financialPageAdapters";
import type { ViewFilter } from "./financialViewService";
import { API_URL } from "../config/api";

export interface HistoryData {
  periodo: string;
  receita: number;
  despesa: number;
  resultado: number;
  margem: number;
}

export async function getHistory(
  inicio?: string,
  fim?: string,
  signal?: AbortSignal,
  viewFilter?: ViewFilter,
): Promise<HistoryData[]> {
  if (viewFilter) return historyForFilter(viewFilter, signal);
  const parametros = new URLSearchParams();

  if (inicio) {
    parametros.append("inicio", inicio);
  }

  if (fim) {
    parametros.append("fim", fim);
  }

  const query = parametros.toString();

  const url = query
    ? `${API_URL}/api/historico?${query}`
    : `${API_URL}/api/historico`;

  const response = await fetch(url, {
    method: "GET",
    credentials: "include",
    signal,
  });

  if (!response.ok) {
    throw new Error(
      `Erro ao carregar o histórico financeiro: HTTP ${response.status}`,
    );
  }

  return response.json();
}
