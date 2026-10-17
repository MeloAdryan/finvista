import { API_URL } from "../config/api";
import { apiFetch } from "../config/http";

export interface SyncResumo {
  descricao: string;
  tipo: string;
  competencia: string | null;
  vencimento: string | null;
  valor: number;
  pago: number | null;
  pendente: number | null;
  rateios: {
    categoria: string;
    valor: number;
    centro: string | null;
    valorCentro: number | null;
  }[];
}

export interface ApiSyncPreview {
  clienteId: number;
  clienteNome: string;
  parcelaId: string;
  acao: "INCLUIR" | "ATUALIZAR" | "VINCULAR_EXCEL";
  lancamentoId: number | null;
  expiraEm: string;
  token: string;
  antes: SyncResumo | null;
  depois: SyncResumo;
  avisos: string[];
}

export interface ApiSyncResult {
  operacaoId: string;
  clienteId: number;
  parcelaId: string;
  acao: "INCLUIR" | "ATUALIZAR" | "VINCULAR_EXCEL" | "SEM_ALTERACAO";
  lancamentoId: number;
  concluidoEm: string;
}

export class ApiSyncError extends Error {
  status: number;
  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

async function post<T>(
  id: string,
  path: string,
  body: object,
  signal: AbortSignal,
  fluxo = "sincronizacao",
): Promise<T> {
  const response = await apiFetch(
    `${API_URL}/api/integracoes/conta-azul/parcelas/${encodeURIComponent(id)}/${fluxo}/${path}`,
    {
      method: "POST",
      credentials: "include",
      signal,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    },
  );
  if (!response.ok) {
    const erro = await response.json().catch(() => null);
    throw new ApiSyncError(
      erro?.mensagem || "Não foi possível concluir a sincronização.",
      response.status,
    );
  }
  return response.json() as Promise<T>;
}

function conferir(
  clienteId: number,
  parcelaId: string,
  resposta: { clienteId: number; parcelaId: string },
) {
  if (
    resposta.clienteId !== clienteId ||
    resposta.parcelaId?.toLowerCase() !== parcelaId.toLowerCase()
  ) {
    throw new Error(
      "A resposta pertence a outra empresa ou parcela. Atualize a tela.",
    );
  }
}

export async function prepararSincronizacao(
  clienteId: number,
  parcelaId: string,
  signal: AbortSignal,
) {
  const previa = await post<ApiSyncPreview>(
    parcelaId,
    "previa",
    { clienteId },
    signal,
  );
  conferir(clienteId, parcelaId, previa);
  return previa;
}

export async function prepararVinculo(
  clienteId: number,
  parcelaId: string,
  lancamentoId: number,
  signal: AbortSignal,
) {
  const previa = await post<ApiSyncPreview>(
    parcelaId,
    "previa",
    { clienteId, lancamentoId },
    signal,
    "conciliacao",
  );
  conferir(clienteId, parcelaId, previa);
  if (previa.acao !== "VINCULAR_EXCEL" || previa.lancamentoId !== lancamentoId)
    throw new Error(
      "O vínculo retornado não corresponde ao lançamento escolhido. Gere outra prévia.",
    );
  return previa;
}

export async function confirmarSincronizacao(
  previa: ApiSyncPreview,
  signal: AbortSignal,
) {
  const resultado = await post<ApiSyncResult>(
    previa.parcelaId,
    "confirmar",
    {
      clienteId: previa.clienteId,
      token: previa.token,
      confirmado: true,
      confirmoNaoDuplicado: previa.acao === "INCLUIR",
    },
    signal,
  );
  conferir(previa.clienteId, previa.parcelaId, resultado);
  return resultado;
}
