import { API_URL } from "../config/api";

// Interfaces descrevem o contrato JSON; não fazem consultas por conta própria.
export interface CentroDetalhe {
  id_centro_custo: string | null;
  nome_centro_custo: string | null;
  valor: number | null;
  valor_bruto: number | null;
}
export interface RateioDetalhe {
  id_categoria: string | null;
  nome_categoria: string | null;
  valor: number | null;
  valor_bruto: number | null;
  rateio_centro_custo: CentroDetalhe[] | null;
}
export interface ParcelaDetalhe {
  id: string;
  descricao: string | null;
  status: string | null;
  data_vencimento: string | null;
  valor_total_liquido: number | null;
  valor_liquido_conferido?: number | null;
  origem_valor_liquido?:
    | "VALOR_TOTAL_LIQUIDO"
    | "COMPOSICAO_CONFERIDA"
    | "NAO_CONFERIDO";
  valor_pago: number | null;
  nao_pago: number | null;
  valor_composicao: {
    valor_bruto: number | null;
    valor_liquido: number | null;
    juros: number | null;
    multa: number | null;
    desconto: number | null;
    taxa: number | null;
  } | null;
  evento: {
    id: string | null;
    tipo: string | null;
    data_competencia: string | null;
    condicao_pagamento: { quantidade_parcelas: number | null } | null;
    rateio: RateioDetalhe[] | null;
  } | null;
}
export interface RespostaDetalhe {
  clienteId: number;
  consultadoEm: string;
  parcela: ParcelaDetalhe;
}

// Esta função é a única parte do frontend que conhece a URL de detalhe.
export async function consultarDetalheParcela(
  clienteId: number,
  parcelaId: string,
  signal: AbortSignal,
): Promise<RespostaDetalhe> {
  const response = await fetch(
    `${API_URL}/api/integracoes/conta-azul/parcelas/${encodeURIComponent(parcelaId)}?clienteId=${clienteId}`,
    { credentials: "include", signal },
  );
  if (!response.ok) {
    let message = "Não foi possível consultar o detalhe da parcela.";
    try {
      const data = await response.json();
      message = data.mensagem || data.erro || data.message || message;
    } catch {
      /* resposta sem JSON */
    }
    throw new Error(message);
  }
  const data = (await response.json()) as RespostaDetalhe;
  if (
    data.clienteId !== clienteId ||
    data.parcela.id.toLowerCase() !== parcelaId.toLowerCase()
  )
    throw new Error(
      "A resposta não corresponde à empresa e parcela solicitadas.",
    );
  return data;
}
