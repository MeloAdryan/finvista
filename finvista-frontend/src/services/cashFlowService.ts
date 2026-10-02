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
    let mensagem =
      "Erro ao carregar o fluxo de caixa.";

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

  const dados =
    (await response.json()) as CashFlowData[];

  return dados.map((item) => ({
    ...item,
    saldoInicial: Number(item.saldoInicial),
    entradas: Number(item.entradas),
    saidas: Number(item.saidas),
    saldoFinal: Number(item.saldoFinal),
  }));
}