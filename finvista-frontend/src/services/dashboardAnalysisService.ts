import { dashboardForFilter } from "./financialPageAdapters";
import type { ViewFilter } from "./financialViewService";
import { API_URL } from "../config/api";
import type { DashboardData } from "./dashboardService";
export interface DashboardAnalysis {
  registros?: {
    receitasAtual: number;
    despesasAtual: number;
    receitasAnterior: number;
    despesasAnterior: number;
  };
  clienteId: number;
  inicio: string;
  fim: string;
  inicioAnterior: string;
  fimAnterior: string;
  hoje: string;
  categoria: string | null;
  indicadores: DashboardData;
  categorias: string[];
  meses: {
    periodo: string;
    receita: number;
    despesa: number;
    resultado: number;
    emAndamento: boolean;
    recebimentos?: {
      recebido: number;
      emAberto: number;
      semDetalhamento: number;
      registrosSemDetalhamento: number;
    } | null;
  }[];
  variacoes: {
    categoria: string;
    anterior: number;
    atual: number;
    diferenca: number;
  }[];
  itens: {
    movimentoId?: string | null;
    id: number;
    data: string;
    descricao: string;
    tipo: string;
    valorSelecionado: number;
    valorContabilizadoIntegral: number;
    categorias: string[];
  }[];
}
export async function getDashboardAnalysis(
  inicio?: string,
  fim?: string,
  categoria?: string,
  signal?: AbortSignal,
  viewFilter?: ViewFilter,
): Promise<DashboardAnalysis> {
  if (viewFilter) return dashboardForFilter(viewFilter, categoria, signal);
  const params = new URLSearchParams();
  if (inicio) params.set("inicio", inicio);
  if (fim) params.set("fim", fim);
  if (categoria) params.set("categoria", categoria);
  const response = await fetch(`${API_URL}/api/dashboard/analise?${params}`, {
    credentials: "include",
    signal,
    cache: "no-store",
  });
  if (!response.ok)
    throw new Error(
      response.status === 401
        ? "Entre novamente no FinVista."
        : "Não foi possível consultar o painel. Confira o período e tente novamente.",
    );
  return response.json();
}
export const brl = (value: number) =>
  value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
export const dataBR = (value: string) => value.split("-").reverse().join("/");
export function exportarCSV(nome: string, linhas: (string | number)[][]) {
  const campo = (v: string | number) => {
    let text = String(v);
    if (typeof v === "string" && /^[\s]*[=+@-]/.test(text)) text = "'" + text;
    return `"${text.replaceAll('"', '""')}"`;
  };
  const blob = new Blob(
    ["\uFEFF" + linhas.map((l) => l.map(campo).join(";")).join("\r\n")],
    { type: "text/csv;charset=utf-8" },
  );
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = nome;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
