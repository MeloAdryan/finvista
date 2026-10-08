import { API_URL } from "../config/api";
export interface GoalTimeline {
  id: number;
  inicio: string;
  fim: string;
  hoje: string;
  situacaoTemporal: string;
  diasTotais: number;
  diasDecorridos: number;
  limite: number;
  totalRegistrado: number;
  registradoAteHoje: number;
  registradoDepoisHoje: number;
  ritmoIdealPercentual: number;
  estimativaFim: number | null;
  pontos: {
    data: string;
    acumuladoRegistrado: number | null;
    ritmoIdeal: number;
    estimativa: number | null;
  }[];
  categorias: { categoria: string; valor: number }[];
}
export async function obterAcompanhamento(
  id: number,
  signal: AbortSignal,
): Promise<GoalTimeline> {
  const response = await fetch(
    `${API_URL}/api/metas-gastos/${id}/acompanhamento`,
    { credentials: "include", signal, cache: "no-store" },
  );
  if (!response.ok)
    throw new Error(
      response.status === 401
        ? "Entre novamente para consultar a meta."
        : "Não foi possível carregar a evolução da meta.",
    );
  return response.json();
}
export function exportarMeta(dados: GoalTimeline) {
  const linhas = [
    [
      "Data",
      "Registrado acumulado (R$)",
      "Ritmo ideal (R$)",
      "Estimativa (R$)",
    ],
    ...dados.pontos.map((p) =>
      [p.data, p.acumuladoRegistrado, p.ritmoIdeal, p.estimativa].map((v) =>
        v == null ? "" : String(v),
      ),
    ),
  ];
  const csv =
    "\uFEFF" +
    linhas
      .map((l) =>
        l.map((v) => '"' + String(v).replaceAll('"', '""') + '"').join(";"),
      )
      .join("\r\n");
  const url = URL.createObjectURL(
    new Blob([csv], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = `meta-${dados.id}-${dados.inicio}-${dados.fim}.csv`;
  a.click();
  URL.revokeObjectURL(url);
}
