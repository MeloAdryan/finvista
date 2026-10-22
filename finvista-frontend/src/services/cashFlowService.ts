import { API_URL } from "../config/api";

export type PeriodoFluxoCaixa = 6 | 12 | "todos";

export interface CashFlowData {
  mes: string;
  saldoInicial: number;
  entradas: number;
  saidas: number;
  saldoFinal: number;
}

export async function getCashFlow(
  periodo: PeriodoFluxoCaixa = 6,
): Promise<CashFlowData[]> {
  const params = new URLSearchParams();

  params.set("periodo", String(periodo));

  const response = await fetch(
    `${API_URL}/api/fluxo-caixa?${params.toString()}`,
    {
      method: "GET",
      credentials: "include",
    },
  );

  if (!response.ok) {
    let mensagem = "Erro ao carregar o fluxo de caixa.";

    try {
      const respostaErro = await response.text();

      if (respostaErro.trim()) {
        mensagem = respostaErro;
      }
    } catch {
      // Mantém a mensagem padrão.
    }

    throw new Error(mensagem);
  }

  const dados = (await response.json()) as CashFlowData[];

  return dados.map((item) => ({
    ...item,
    saldoInicial: Number(item.saldoInicial),
    entradas: Number(item.entradas),
    saidas: Number(item.saidas),
    saldoFinal: Number(item.saldoFinal),
  }));
}
export interface RealizedCashFlow {
  clienteId: number;
  inicio: string;
  fim: string;
  lancamentosConferidos: number;
  lancamentosSemDetalhamento: number;
  baixasNoPeriodo: number;
  parcial: boolean;
  base: string;
  meses: CashFlowData[];
}
export async function getRealizedCashFlow(
  periodo: PeriodoFluxoCaixa,
  signal: AbortSignal,
): Promise<RealizedCashFlow> {
  const r = await fetch(
    `${API_URL}/api/fluxo-caixa/realizado?periodo=${periodo}`,
    { credentials: "include", cache: "no-store", signal },
  );
  if (!r.ok)
    throw new Error(
      "Não foi possível consultar o caixa realizado. Confira a sessão e a atualização do backend.",
    );
  const d: RealizedCashFlow = await r.json();
  if (d.base !== "BAIXAS_AUDITADAS_LIQUIDAS" || !Array.isArray(d.meses))
    throw new Error("Resposta de caixa incompatível.");
  const nomes = [
    "Jan",
    "Fev",
    "Mar",
    "Abr",
    "Mai",
    "Jun",
    "Jul",
    "Ago",
    "Set",
    "Out",
    "Nov",
    "Dez",
  ];
  return {
    ...d,
    meses: d.meses.map((m) => {
      const [ano, mes] = m.mes.split("-");
      return {
        ...m,
        mes: `${nomes[Number(mes) - 1]}/${ano}`,
        entradas: Number(m.entradas),
        saidas: Number(m.saidas),
        saldoInicial: Number(m.saldoInicial),
        saldoFinal: Number(m.saldoFinal),
      };
    }),
  };
}
