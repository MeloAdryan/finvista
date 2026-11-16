import { API_URL } from "../config/api";
export interface ProjectionPlanning {
  inicio: string;
  fim: string;
  hoje: string;
  mediaReceitaMensal: number;
  mesesHistorico: number;
  receitaMensalEsperada: number;
  saldoInicial: number;
  variacaoPercentual: number;
  meses: {
    periodo: string;
    receitaRegistrada: number;
    receitaComplementar: number;
    receitaSimulada: number;
    despesaRegistrada: number;
    resultadoMensal: number;
    resultadoAcumulado: number;
    saldoBase: number;
    saldoConservador: number;
    saldoOtimista: number;
  }[];
}
export interface ProjectionInputs {
  receitaMensal?: number;
  saldoInicial?: number;
  variacaoPercentual?: number;
}
export async function getProjectionPlanning(
  dados: ProjectionInputs,
  signal: AbortSignal,
  grupo?: { usuarioAtendido?: number },
): Promise<ProjectionPlanning> {
  const params = new URLSearchParams();
  for (const [chave, valor] of Object.entries(dados))
    if (valor != null) params.set(chave, String(valor));
  if (grupo?.usuarioAtendido != null) {
    params.set("usuarioAtendido", String(grupo.usuarioAtendido));
  }
  const endpoint = grupo
    ? "/api/analises/grupo/projecao"
    : "/api/projecao/planejamento";
  const response = await fetch(`${API_URL}${endpoint}?${params}`, {
    credentials: "include",
    signal,
    cache: "no-store",
  });
  if (!response.ok)
    throw new Error(
      response.status === 401
        ? "Entre novamente para consultar a projeção."
        : "Não foi possível calcular a simulação. Confira os valores informados.",
    );
  return response.json();
}
