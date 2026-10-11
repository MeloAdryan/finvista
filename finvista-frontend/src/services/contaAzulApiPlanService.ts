import { API_URL } from "../config/api";
import type { ParcelaDetalhe } from "./contaAzulParcelDetailService";

export interface PlanoApi {
  clienteId: number;
  identidadeApi: string;
  situacao:
    | "BLOQUEADO"
    | "REGISTRO_IDENTIFICADO"
    | "CORRESPONDENCIA_POSSIVEL"
    | "SEM_CORRESPONDENCIA";
  consultadoEm: string;
  parcela: ParcelaDetalhe;
  avisos: string[];
  totalCorrespondencias: number;
  correspondencias: {
    id: number;
    descricao: string | null;
    tipo: string | null;
    competencia: string | null;
    vencimento: string | null;
    valor: number | null;
    origem: string | null;
  }[];
}

export async function prepararPlanoApi(
  clienteId: number,
  parcelaId: string,
  signal: AbortSignal,
): Promise<PlanoApi> {
  const response = await fetch(
    `${API_URL}/api/integracoes/conta-azul/parcelas/${encodeURIComponent(parcelaId)}/plano?clienteId=${clienteId}`,
    { credentials: "include", signal },
  );
  if (!response.ok) {
    let message = "Não foi possível preparar o plano.";
    try {
      const data = await response.json();
      message = data.mensagem || data.erro || data.message || message;
    } catch {
      /* mantém a mensagem padrão */
    }
    throw new Error(message);
  }
  const data = (await response.json()) as PlanoApi;
  if (
    data.clienteId !== clienteId ||
    data.parcela?.id?.toLowerCase() !== parcelaId.toLowerCase()
  )
    throw new Error(
      "O plano não corresponde à empresa e parcela selecionadas.",
    );
  return data;
}
