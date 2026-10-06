import { API_URL } from "../config/api";

export type FinancialDetailKind = "RECEITA" | "DESPESA";

export interface FinancialDetailItem {
  id: number | null;
  dataAnalise: string;
  vencimento: string | null;
  realizacao: string | null;
  descricao: string | null;
  situacao: string | null;
  valorContabilizado: number;
  valorOriginal: number | null;
  principalRealizado: number | null;
  principalAberto: number | null;
  origem: string | null;
}

export interface FinancialDetailData {
  clienteId: number;
  tipo: FinancialDetailKind;
  mes: string;
  dataInicial: string;
  dataFinal: string;
  total: number;
  quantidadeLancamentos: number;
  pagina: number;
  tamanho: number;
  totalPaginas: number;
  explicacao: string;
  itens: FinancialDetailItem[];
}

export async function getFinancialDetail(
  tipo: FinancialDetailKind,
  pagina: number,
  mes: string | undefined,
  signal: AbortSignal,
): Promise<FinancialDetailData> {
  const parametros = new URLSearchParams({ tipo, pagina: String(pagina), tamanho: "20" });
  if (mes) parametros.set("mes", mes);
  const resposta = await fetch(`${API_URL}/api/detalhamento-financeiro?${parametros}`, {
    credentials: "include",
    signal,
    cache: "no-store",
  });
   if (!resposta.ok) {
    throw new Error(
      resposta.status === 401
        ? "Sua sessão expirou. Entre novamente."
        : `Não foi possível consultar os lançamentos (HTTP ${resposta.status}).`
    );
  }
  const dados: FinancialDetailData = await resposta.json();
  if (dados.tipo !== tipo || dados.pagina !== pagina || !Number.isFinite(dados.total)
      || !Array.isArray(dados.itens) || !/^\d{4}-\d{2}$/.test(dados.mes)
      || (mes !== undefined && dados.mes !== mes)) {
    throw new Error("A resposta do detalhamento não corresponde à consulta. Atualize o painel.");
  }
  return dados;
}
