import { API_URL } from "../config/api";
import { apiFetch } from "../config/http";
import type { FinancialAlert, FinancialAlerts } from "./financialAlertsService";

export interface AlertCenterFilters {
  inicio?: string;
  fim?: string;
  gravidade?: FinancialAlert["gravidade"];
  tipo?: FinancialAlert["tipo"];
  estado?: "ATIVO" | "RESOLVIDO";
  lido?: boolean;
  pagina: number;
}
export interface AlertCenterRecord {
  id: number;
  alerta: FinancialAlert;
  estado: "ATIVO" | "RESOLVIDO";
  lido: boolean;
  ocorrencias: number;
  criadoEm: string;
  alteradoEm: string;
  verificadoEm: string;
  resolvidoEm: string | null;
}
export interface AlertCenterData {
  clienteId: number;
  inicio: string;
  fim: string;
  pagina: number;
  tamanho: number;
  total: number;
  totalPaginas: number;
  registros: AlertCenterRecord[];
}
export interface AlertCenterUpdate {
  clienteId: number;
  inicio: string;
  fim: string;
  atualizadoEm: string;
  avisosEncontrados: number;
  comparacao: FinancialAlerts["comparacao"];
}

async function conferir(response: Response) {
  if (response.ok) return;
  const erro = await response.json().catch(() => null);
  throw new Error(
    response.status === 401
      ? "Entre novamente para acessar a central."
      : (erro?.mensagem ??
          "Não foi possível concluir a operação na Central de Alertas."),
  );
}
export async function listarAlertas(
  filtros: AlertCenterFilters,
  signal: AbortSignal,
): Promise<AlertCenterData> {
  const params = new URLSearchParams({ tamanho: "20" });
  Object.entries(filtros).forEach(([nome, valor]) => {
    if (valor !== undefined && valor !== "") params.set(nome, String(valor));
  });
  const response = await apiFetch(
    `${API_URL}/api/alertas-financeiros/central?${params}`,
    { signal, cache: "no-store" },
  );
  await conferir(response);
  return response.json();
}
export async function reavaliarAlertas(
  inicio: string | undefined,
  fim: string | undefined,
  signal: AbortSignal,
): Promise<AlertCenterUpdate> {
  const response = await apiFetch(
    `${API_URL}/api/alertas-financeiros/central/reavaliar`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ inicio: inicio || null, fim: fim || null }),
      signal,
    },
  );
  await conferir(response);
  return response.json();
}
export async function marcarAlertaLido(
  id: number,
  signal: AbortSignal,
): Promise<void> {
  const response = await apiFetch(
    `${API_URL}/api/alertas-financeiros/central/${id}/lido`,
    { method: "POST", signal },
  );
  await conferir(response);
}
