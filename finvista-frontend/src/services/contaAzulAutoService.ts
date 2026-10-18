import { API_URL } from "../config/api";
import { apiFetch } from "../config/http";

export interface AutoConfig {
  clienteId: number;
  habilitada: boolean;
  inicio: string;
  fim: string;
  intervaloMinutos: number;
  incluirNovas: boolean;
  proximaEm: string | null;
  alteradoEm: string | null;
}
export interface AutoRun {
  id: string;
  status: string;
  inicio: string;
  fim: string;
  criadoEm: string;
  concluidoEm: string | null;
  incluidas: number;
  atualizadas: number;
  mantidas: number;
  pendentes: number;
  emFila: number;
  mensagem: string | null;
}
export interface AutoStatus {
  processadorHabilitado: boolean;
  configuracao: AutoConfig;
  execucoes: AutoRun[];
  pendencias: {
    parcelaId: string;
    tipo: string;
    descricao: string | null;
    mensagem: string;
    lancamentoId: number | null;
  }[];
  totalPendencias: number;
  paginaPendencias: number;
}
export interface AutoPedido {
  clienteId: number;
  habilitada: boolean;
  inicio: string;
  fim: string;
  intervaloMinutos: number;
  incluirNovas: boolean;
  confirmado: boolean;
  confirmoHistorico: boolean;
}

export async function consultarAutomacao(
  clienteId: number,
  pagina: number,
  signal: AbortSignal,
) {
  return ler(
    await apiFetch(
      `${API_URL}/api/integracoes/conta-azul/automatica?clienteId=${clienteId}&paginaPendencias=${pagina}`,
      { credentials: "include", signal, cache: "no-store" },
    ),
    clienteId,
  );
}
export async function alterarAutomacao(
  pedido: AutoPedido,
  signal: AbortSignal,
) {
  return post("", pedido, pedido.clienteId, signal);
}
export async function acaoAutomacao(
  clienteId: number,
  acao: "pausar" | "executar",
  signal: AbortSignal,
) {
  return post(`/${acao}`, { clienteId }, clienteId, signal);
}
async function post(
  path: string,
  body: object,
  clienteId: number,
  signal: AbortSignal,
) {
  return ler(
    await apiFetch(`${API_URL}/api/integracoes/conta-azul/automatica${path}`, {
      method: "POST",
      credentials: "include",
      signal,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    }),
    clienteId,
  );
}
async function ler(response: Response, clienteId: number): Promise<AutoStatus> {
  const body = await response.json().catch(() => null);
  if (!response.ok)
    throw new Error(
      body?.mensagem ||
        body?.detail ||
        "Não foi possível consultar ou alterar a sincronização automática. Confira o backend e a migração do banco.",
    );
  if (body?.configuracao?.clienteId !== clienteId)
    throw new Error("A resposta pertence a outra empresa. Atualize a tela.");
  return body as AutoStatus;
}
