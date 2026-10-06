import { API_URL } from "../config/api";

export type Money = number | string | null;
export interface Allocation {
  bloco: number;
  categoria: string | null;
  valorCategoria: Money;
  centro: string | null;
  valorCentro: Money;
}
export interface ConferenceLine {
  numero: number;
  competencia: string | null;
  vencimento: string | null;
  descricao: string;
  original: Money;
  realizado: Money;
  aberto: Money;
  totalRealizado: Money;
  totalAberto: Money;
  rateios: Allocation[];
  avisos: string[];
  erros: string[];
}
export interface Conference {
  arquivo: string;
  tipo: string;
  linhasLidas: number;
  aceitas: number;
  rejeitadas: number;
  linhasComAvisos: number;
  valorOriginal: Money;
  valorTotalRealizado: Money;
  valorTotalAberto: Money;
  linhas: ConferenceLine[];
}
export interface PlanItem {
  linha: number;
  descricao: string;
  valorOriginal: string;
  sugestao: string;
  candidatos: number[];
  avisos: string[];
}
export interface SyncPlan {
  arquivo: string;
  dataCorte: string;
  token: string;
  linhas: number;
  itens: PlanItem[];
}
export interface SyncDecision {
  linha: number;
  acao: "ATUALIZAR" | "CRIAR";
  lancamentoId: number | null;
}
export interface SyncResult {
  loteId: number;
  criados: number;
  atualizados: number;
  mantidos: number;
  pendentes: number;
}
export interface Candidate {
  id: number;
  descricao: string;
  competencia: string | null;
  vencimento: string | null;
  original: Money;
  realizado: Money;
  aberto: Money;
  situacao: string | null;
  contraparte: string | null;
  referencia: string | null;
  categoria: string | null;
  centro: string | null;
}
export class SyncHttpError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}
async function read<T>(response: Response): Promise<T> {
  const text = await response.text();
  let data: unknown;
  try {
    data = JSON.parse(text);
  } catch {
    data = null;
  }
  if (!response.ok) {
    const info = data as {
      erro?: string;
      mensagem?: string;
      message?: string;
      detail?: string;
    } | null;
    const fallback =
      response.status === 401
        ? "Entre novamente no FinVista."
        : response.status === 409
          ? "O plano mudou. Gere e revise outro plano."
          : "Não foi possível concluir a operação.";
    throw new SyncHttpError(
      response.status,
      info?.mensagem || info?.detail || info?.message || info?.erro || fallback,
    );
  }
  if (data === null)
    throw new Error("O servidor não retornou os dados esperados.");
  return data as T;
}
function body(file: File, cut: string) {
  const form = new FormData();
  form.append("arquivo", file);
  form.append("dataCorte", cut);
  return form;
}
export async function conferContaAzul(
  file: File,
  cut: string,
  signal: AbortSignal,
) {
  return read<Conference>(
    await fetch(`${API_URL}/api/importacoes/conta-azul/conferencia`, {
      method: "POST",
      credentials: "include",
      body: body(file, cut),
      signal,
    }),
  );
}
export async function planContaAzul(
  file: File,
  cut: string,
  signal: AbortSignal,
) {
  return read<SyncPlan>(
    await fetch(
      `${API_URL}/api/importacoes/conta-azul/sincronizacao/planejar`,
      {
        method: "POST",
        credentials: "include",
        body: body(file, cut),
        signal,
      },
    ),
  );
}
export async function getCandidate(id: number, signal: AbortSignal) {
  return read<Candidate>(
    await fetch(
      `${API_URL}/api/importacoes/conta-azul/sincronizacao/candidatos/${id}`,
      {
        credentials: "include",
        signal,
      },
    ),
  );
}
export async function applyContaAzul(
  file: File,
  plan: SyncPlan,
  decisions: SyncDecision[],
) {
  const form = body(file, plan.dataCorte);
  form.append("token", plan.token);
  form.append("decisoes", JSON.stringify(decisions));
  return read<SyncResult>(
    await fetch(`${API_URL}/api/importacoes/conta-azul/sincronizacao/aplicar`, {
      method: "POST",
      credentials: "include",
      body: form,
    }),
  );
}
