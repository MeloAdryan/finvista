import { API_URL } from "../config/api";

export type TipoConsulta = "RECEITA" | "DESPESA";
export interface FiltroContaAzul {
  clienteId: number;
  tipo: TipoConsulta;
  inicio: string;
  fim: string;
}
export interface ClassificacaoContaAzul {
  id: string | null;
  nome: string | null;
}
export interface ParcelaContaAzul {
  id: string;
  descricao: string | null;
  data_vencimento: string | null;
  data_competencia: string | null;
  status: string | null;
  status_traduzido: string | null;
  total: number | null;
  pago: number | null;
  nao_pago: number | null;
  categorias: ClassificacaoContaAzul[] | null;
  centros_custo: ClassificacaoContaAzul[] | null;
}
export interface ConsultaContaAzul extends FiltroContaAzul {
  pagina: number;
  tamanhoPagina: number;
  totalItens: number;
  consultadoEm: string;
  itens: ParcelaContaAzul[];
}
export async function consultarFinanceiroContaAzul(
  filtro: FiltroContaAzul,
  pagina: number,
  signal: AbortSignal,
): Promise<ConsultaContaAzul> {
  const query = new URLSearchParams({
    clienteId: String(filtro.clienteId),
    tipo: filtro.tipo,
    inicio: filtro.inicio,
    fim: filtro.fim,
    pagina: String(pagina),
  });
  const response = await fetch(
    `${API_URL}/api/integracoes/conta-azul/financeiro?${query}`,
    { credentials: "include", signal },
  );
  if (!response.ok) {
    let message = "Não foi possível consultar as parcelas da Conta Azul.";
    try {
      const data = await response.json();
      message = data.mensagem || data.erro || data.message || message;
    } catch {
      /* resposta sem JSON */
    }
    throw new Error(message);
  }
  const data = (await response.json()) as ConsultaContaAzul;
  if (data.clienteId !== filtro.clienteId)
    throw new Error(
      "A empresa retornada não corresponde à consulta. Atualize a tela.",
    );
  return data;
}
