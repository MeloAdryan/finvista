import { apiFetch } from "../config/http";
import { API_URL } from "../config/api";

export const recorrencias = {
  SEM_RECORRENCIA: "Sem recorrência",
  MENSAL: "Mensal",
  TRIMESTRAL: "Trimestral",
  SEMESTRAL: "Semestral",
  ANUAL: "Anual",
} as const;

export interface RecurrenceRequest {
  nome: string;
  recorrencia: keyof typeof recorrencias;
  dataInicio: string;
  dataFim: string;
  valorLimite: number;
  percentualAlerta: number;
  categoria: string | null;
  centroCusto: string | null;
}

export interface RecurrencePreview {
  recorrencia: string;
  limiteTotal: number;
  periodos: {
    inicio: string;
    fim: string;
    limite: number;
    dias: number;
    diasPeriodoCompleto: number;
    proporcional: boolean;
  }[];
}

async function erroDaResposta(response: Response) {
  const dados: unknown = await response.json().catch(() => null);
  if (
    dados &&
    typeof dados === "object" &&
    "mensagem" in dados &&
    typeof dados.mensagem === "string"
  ) {
    return dados.mensagem;
  }
  if (response.status === 403)
    return "Esta operação exige uma sessão de administrador válida.";
  if (response.status === 404)
    return "Recorrência não encontrada para o cliente selecionado.";
  return "Não foi possível concluir a operação. Confira os dados e tente novamente.";
}

export async function previaRecorrencia(
  pedido: RecurrenceRequest,
): Promise<RecurrencePreview> {
  const response = await apiFetch(
    `${API_URL}/api/metas-gastos/recorrencias/previa`,
    {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(pedido),
    },
  );
  if (!response.ok) throw new Error(await erroDaResposta(response));
  return response.json();
}

export async function criarRecorrencia(
  pedido: RecurrenceRequest,
): Promise<void> {
  const response = await apiFetch(`${API_URL}/api/metas-gastos/recorrencias`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(pedido),
  });
  if (!response.ok) throw new Error(await erroDaResposta(response));
}

export async function excluirRecorrencia(serieId: string): Promise<void> {
  const response = await apiFetch(
    `${API_URL}/api/metas-gastos/recorrencias/${encodeURIComponent(serieId)}`,
    {
      method: "DELETE",
      credentials: "include",
    },
  );
  if (!response.ok) throw new Error(await erroDaResposta(response));
}
