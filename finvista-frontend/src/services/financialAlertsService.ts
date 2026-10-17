import { API_URL } from "../config/api";

export interface FinancialAlert {
  id: string;
  tipo: "META_LIMITE" | "ORCAMENTO_EXCEDENTE" | "AUMENTO_DESPESAS";
  gravidade: "INFORMATIVO" | "ATENCAO" | "CRITICO";
  entidadeId: number | null;
  titulo: string;
  motivo: string;
  inicio: string;
  fim: string;
  valorAtual: number;
  valorBase: number;
  diferenca: number;
  percentual: number;
  limiarPercentual: number;
  inicioAnterior: string | null;
  fimAnterior: string | null;
  categoria: string | null;
  centroCusto: string | null;
  encerrado: boolean;
}

export interface FinancialAlerts {
  clienteId: number;
  inicio: string;
  fim: string;
  categoria: string | null;
  total: number;
  alertas: FinancialAlert[];
  comparacao:
    | "DISPONIVEL"
    | "SEM_REGISTROS"
    | "BASE_NAO_POSITIVA"
    | "PERIODO_EM_ANDAMENTO";
}

export async function getFinancialAlerts(
  inicio: string,
  fim: string,
  categoria: string | null,
  signal: AbortSignal,
): Promise<FinancialAlerts> {
  const params = new URLSearchParams({ inicio, fim });
  if (categoria) params.set("categoria", categoria);
  const response = await fetch(`${API_URL}/api/alertas-financeiros?${params}`, {
    credentials: "include",
    cache: "no-store",
    signal,
  });
  if (!response.ok) {
    throw new Error(
      response.status === 401
        ? "Entre novamente para consultar os alertas."
        : "Não foi possível consultar os alertas financeiros.",
    );
  }
  return response.json();
}
