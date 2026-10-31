import { API_URL } from "../config/api";
export type ViewCriterion =
  | "COMPETENCIA"
  | "VENCIMENTO"
  | "BAIXA"
  | "BAIXA_EXTRATO";
export type ViewPeriod =
  | "SEMANAL"
  | "MENSAL"
  | "ANUAL"
  | "TODOS"
  | "PERSONALIZADO";
export interface ViewFilter {
  clienteId?: number;
  criterio: ViewCriterion;
  periodo: ViewPeriod;
  situacao: "TODAS" | "PAGAS" | "ABERTAS" | "VENCIDAS" | "PARCIAIS";
  referencia: string;
  inicio: string;
  fim: string;
  categoria: string;
  centro: string;
}
export interface ViewLine {
  movimentoId?: string | null;
  principalOriginal?: number | null;
  principalRealizado?: number | null;
  principalAberto?: number | null;
  liquidoRealizado?: number | null;
  liquidoAberto?: number | null;
  id: number;
  documento: string | null;
  descricao: string;
  tipo: string;
  data: string;
  competencia: string | null;
  vencimento: string | null;
  categoria: string;
  centro: string;
  valor: number;
}
export interface ViewLimit {
  id: number;
  nome: string;
  inicio: string;
  fim: string;
  planejado: number;
  consumoPeriodo: number;
  percentual: number | null;
  recorteParcial: boolean;
}
export interface FinancialView {
  clienteId: number;
  filtro: ViewFilter;
  intervalo: { inicio: string; fim: string };
  hoje: string;
  totais: { receita: number; despesa: number; resultado: number };
  serie: {
    periodo: string;
    receita: number;
    despesa: number;
    resultado: number;
  }[];
  linhas: ViewLine[];
  exclusoes: Record<string, number>;
  categorias: string[];
  centros: string[];
  metas: ViewLimit[];
  orcamentos: ViewLimit[];
  baseValor: string;
  aviso: string;
}
export const criterionNames = {
  COMPETENCIA: "Competência",
  VENCIMENTO: "Vencimento (caixa previsto)",
  BAIXA: "Baixa — API (pago/recebido)",
  BAIXA_EXTRATO: "Baixa — extrato importado",
};
export function todayInBrazil(now = new Date()) {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Sao_Paulo",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}
export function initialViewFilter(): ViewFilter {
  const today = todayInBrazil();
  return {
    criterio: "COMPETENCIA",
    periodo: "MENSAL",
    situacao: "TODAS",
    referencia: today,
    inicio: today,
    fim: today,
    categoria: "",
    centro: "",
  };
}
export function viewRange(
  f: ViewFilter,
): { inicio: string; fim: string } | null {
  if (f.periodo === "TODOS") return null;
  if (f.periodo === "PERSONALIZADO") return { inicio: f.inicio, fim: f.fim };
  const d = new Date(`${f.referencia}T12:00:00Z`);
  const iso = (v: Date) => v.toISOString().slice(0, 10);
  if (f.periodo === "SEMANAL") {
    d.setUTCDate(d.getUTCDate() - ((d.getUTCDay() + 6) % 7));
    const inicio = iso(d);
    d.setUTCDate(d.getUTCDate() + 6);
    return { inicio, fim: iso(d) };
  }
  if (f.periodo === "ANUAL")
    return {
      inicio: `${d.getUTCFullYear()}-01-01`,
      fim: `${d.getUTCFullYear()}-12-31`,
    };
  return {
    inicio: `${f.referencia.slice(0, 7)}-01`,
    fim: iso(
      new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0, 12)),
    ),
  };
}
export async function getFinancialView(
  f: ViewFilter,
  signal: AbortSignal,
): Promise<FinancialView> {
  const params = new URLSearchParams();
  Object.entries(f).forEach(([k, v]) => {
    if (v) params.set(k, String(v));
  });
  const r = await fetch(`${API_URL}/api/analises/financeira?${params}`, {
    credentials: "include",
    cache: "no-store",
    signal,
  });
  if (!r.ok)
    throw new Error(
      r.status === 401
        ? "Entre novamente no FinVista."
        : "Não foi possível consultar os filtros. Confira as datas e a disponibilidade da atualização do servidor.",
    );
  const d: FinancialView = await r.json();
  if (f.clienteId != null && d.clienteId !== f.clienteId)
    throw new Error("A empresa selecionada mudou. Atualize a página.");
  if (
    !Array.isArray(d.linhas) ||
    !d.filtro ||
    d.filtro.criterio !== f.criterio ||
    d.filtro.periodo !== f.periodo ||
    d.filtro.situacao !== f.situacao ||
    (d.filtro.categoria || "") !== (f.categoria || "") ||
    (d.filtro.centro || "") !== (f.centro || "")
  )
    throw new Error("Resposta incompatível com o filtro aplicado.");
  const expected = viewRange(f);
  if (
    expected &&
    (d.intervalo?.inicio !== expected.inicio ||
      d.intervalo?.fim !== expected.fim)
  )
    throw new Error(
      "O servidor retornou outro período. Os valores anteriores não serão usados neste recorte.",
    );
  return d;
}
export function financialViewCSV(data: FinancialView, lines: ViewLine[]) {
  const text = (value: unknown) => {
    let s = String(value ?? "");
    if (/^[\s]*[=+@-]/.test(s)) s = `'${s}`;
    return `"${s.replaceAll('"', '""')}"`;
  };
  const header = [
    "Critério",
    "Período",
    "Situação",
    "Data",
    "ID FinVista",
    "ID origem",
    "Descrição",
    "Tipo",
    "Competência",
    "Vencimento",
    "Categoria",
    "Centro",
    "Valor (R$)",
  ]
    .map(text)
    .join(";");
  const rows = lines.map(
    (l) =>
      [
        data.filtro.criterio,
        `${data.intervalo.inicio} a ${data.intervalo.fim}`,
        data.filtro.situacao,
        l.data,
        l.id,
        l.documento,
        l.descricao,
        l.tipo,
        l.competencia,
        l.vencimento,
        l.categoria,
        l.centro,
      ]
        .map(text)
        .join(";") + `;"${l.valor.toFixed(2).replace(".", ",")}"`,
  );
  return "\uFEFF" + [header, ...rows].join("\r\n");
}
export function downloadFinancialView(data: FinancialView, lines: ViewLine[]) {
  const url = URL.createObjectURL(
    new Blob([financialViewCSV(data, lines)], {
      type: "text/csv;charset=utf-8",
    }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = "finvista-analise-filtrada.csv";
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

// Store only the applied view, scoped to the selected company. Never store financial rows.
export function savedViewFilter(clienteId?: number): ViewFilter {
  const fallback = initialViewFilter();
  try {
    const raw = localStorage.getItem(
      `finvista:financial-filter:v1:${clienteId ?? "sessao"}`,
    );
    if (!raw) return fallback;
    const f = JSON.parse(raw) as ViewFilter;
    const date = (s: unknown) =>
      typeof s === "string" &&
      /^\d{4}-\d{2}-\d{2}$/.test(s) &&
      Number.isFinite(Date.parse(s));
    if (
      !Object.hasOwn(criterionNames, f.criterio) ||
      !["SEMANAL", "MENSAL", "ANUAL", "TODOS", "PERSONALIZADO"].includes(
        f.periodo,
      ) ||
      !["TODAS", "PAGAS", "ABERTAS", "VENCIDAS", "PARCIAIS"].includes(
        f.situacao,
      ) ||
      !date(f.referencia) ||
      !date(f.inicio) ||
      !date(f.fim) ||
      f.inicio > f.fim
    )
      return fallback;
    if (
      (f.criterio === "BAIXA" || f.criterio === "BAIXA_EXTRATO") &&
      !["TODAS", "PAGAS"].includes(f.situacao)
    )
      return fallback;
    return { ...fallback, ...f, clienteId, categoria: "", centro: "" };
  } catch {
    return fallback;
  }
}
export function saveViewFilter(filter: ViewFilter, clienteId?: number): void {
  try {
    localStorage.setItem(
      `finvista:financial-filter:v1:${clienteId ?? "sessao"}`,
      JSON.stringify({ ...filter, categoria: "", centro: "", clienteId }),
    );
  } catch {
    /* Browser storage may be disabled; applying the filter still works. */
  }
}
