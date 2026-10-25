import {
  getFinancialView,
  type FinancialView,
  type ViewFilter,
  type ViewLine,
} from "./financialViewService";
import type { DashboardAnalysis } from "./dashboardAnalysisService";
import type {
  ExpenseAnalysis,
  ExpenseFilters,
  ExpenseRow,
} from "./expenseExplorerService";
import type { HistoryData } from "./historyService";
import type { CashFlowData, RealizedCashFlow } from "./cashFlowService";
import { getCostClassifications } from "./costCenterService";
import type { GoalTimeline } from "./spendingGoalTimelineService";
import type { SpendingGoal } from "./spendingGoalService";

const cents = (v: number) => Math.round(Number(v) * 100);
const sum = (lines: ViewLine[], type: string) =>
  lines.filter((l) => l.tipo === type).reduce((s, l) => s + cents(l.valor), 0) /
  100;
const match = (a: string | undefined | null, b: string) =>
  !a || a.trim().toLocaleLowerCase() === b.trim().toLocaleLowerCase();
export const selectedLines = (view: FinancialView, filter: ViewFilter) =>
  view.linhas.filter(
    (l) =>
      match(filter.categoria, l.categoria) && match(filter.centro, l.centro),
  );
const iso = (date: Date) => date.toISOString().slice(0, 10);
export function previousRange(start: string, end: string) {
  const a = new Date(start + "T12:00:00Z"),
    b = new Date(end + "T12:00:00Z");
  const monthEnd = new Date(
    Date.UTC(b.getUTCFullYear(), b.getUTCMonth() + 1, 0, 12),
  );
  const previousEnd = new Date(a);
  previousEnd.setUTCDate(a.getUTCDate() - 1);
  let previousStart: Date;
  if (a.getUTCDate() === 1 && b.getUTCDate() === monthEnd.getUTCDate()) {
    const months =
      (b.getUTCFullYear() - a.getUTCFullYear()) * 12 +
      b.getUTCMonth() -
      a.getUTCMonth() +
      1;
    previousStart = new Date(
      Date.UTC(a.getUTCFullYear(), a.getUTCMonth() - months, 1, 12),
    );
  } else {
    previousStart = new Date(a);
    previousStart.setUTCDate(
      a.getUTCDate() - Math.round((b.getTime() - a.getTime()) / 86400000) - 1,
    );
  }
  return { inicio: iso(previousStart), fim: iso(previousEnd) };
}
async function read(filter: ViewFilter, signal?: AbortSignal) {
  return getFinancialView(
    { ...filter, categoria: "", centro: "" },
    signal ?? new AbortController().signal,
  );
}
export async function readPagePair(filter: ViewFilter, signal?: AbortSignal) {
  const current = await read(filter, signal);
  const range = previousRange(current.intervalo.inicio, current.intervalo.fim);
  const previous =
    Number(range.inicio.slice(0, 4)) >= 1900
      ? await read({ ...filter, periodo: "PERSONALIZADO", ...range }, signal)
      : null;
  if (previous && current.clienteId !== previous.clienteId)
    throw new Error("A empresa mudou. Atualize a página.");
  return { current, previous, range };
}
export function monthlyLines(lines: ViewLine[], start: string, end: string) {
  const rows = new Map<
    string,
    { periodo: string; receita: number; despesa: number; resultado: number }
  >();
  const d = new Date(start.slice(0, 7) + "-01T12:00:00Z");
  while (iso(d).slice(0, 7) <= end.slice(0, 7)) {
    const period = iso(d).slice(0, 7);
    rows.set(period, { periodo: period, receita: 0, despesa: 0, resultado: 0 });
    d.setUTCMonth(d.getUTCMonth() + 1);
  }
  for (const l of lines) {
    const row = rows.get(l.data.slice(0, 7));
    if (!row) continue;
    if (l.tipo === "RECEITA") row.receita += cents(l.valor);
    if (l.tipo === "DESPESA") row.despesa += cents(l.valor);
  }
  return [...rows.values()].map((r) => ({
    ...r,
    receita: r.receita / 100,
    despesa: r.despesa / 100,
    resultado: (r.receita - r.despesa) / 100,
  }));
}
function items(lines: ViewLine[], all: ViewLine[]) {
  const rows = new Map<
    string,
    {
      movimentoId?: string | null;
      id: number;
      data: string;
      descricao: string;
      tipo: string;
      valorSelecionado: number;
      valorContabilizadoIntegral: number;
      categorias: string[];
    }
  >();
  for (const line of lines) {
    const key = `${line.id}:${line.documento ?? ""}:${line.movimentoId ?? ""}:${line.data}`;
    let row = rows.get(key);
    if (!row) {
      row = {
        movimentoId: line.movimentoId,
        id: line.id,
        data: line.data,
        descricao: line.descricao,
        tipo: line.tipo,
        valorSelecionado: 0,
        valorContabilizadoIntegral:
          all
            .filter(
              (l) =>
                l.id === line.id &&
                l.documento === line.documento &&
                l.movimentoId === line.movimentoId &&
                l.data === line.data,
            )
            .reduce((s, l) => s + cents(l.valor), 0) / 100,
        categorias: [],
      };
      rows.set(key, row);
    }
    row.valorSelecionado += cents(line.valor);
    if (!row.categorias.includes(line.categoria))
      row.categorias.push(line.categoria);
  }
  return [...rows.values()].map((r) => ({
    ...r,
    valorSelecionado: r.valorSelecionado / 100,
  }));
}
const margin = (r: number, d: number) => (r === 0 ? 0 : ((r - d) / r) * 100);
const variation = (a: number, b: number) =>
  a === 0 ? null : ((b - a) / Math.abs(a)) * 100;
export function receiptComposition(lines: ViewLine[], filter: ViewFilter) {
  let paid = 0,
    open = 0,
    unknown = 0,
    count = 0;
  const groups = new Map<string, ViewLine[]>();
  for (const l of lines.filter((l) => l.tipo === "RECEITA")) {
    const key = `${l.id}:${l.documento ?? ""}`;
    groups.set(key, [...(groups.get(key) ?? []), l]);
  }
  for (const rows of groups.values()) {
    const value = rows.reduce((s, l) => s + cents(l.valor), 0),
      first = rows[0];
    if (filter.criterio === "BAIXA" || filter.criterio === "BAIXA_EXTRATO") {
      paid += value;
      continue;
    }
    const p =
      filter.criterio === "COMPETENCIA"
        ? first.principalRealizado
        : first.liquidoRealizado;
    const o =
      filter.criterio === "COMPETENCIA"
        ? first.principalAberto
        : first.liquidoAberto;
    const base =
      filter.criterio === "COMPETENCIA"
        ? first.principalOriginal
        : p != null && o != null
          ? Number(p) + Number(o)
          : null;
    if (
      p == null ||
      o == null ||
      base == null ||
      cents(base) !== cents(p) + cents(o) ||
      value !== cents(base) ||
      Number(p) < 0 ||
      Number(o) < 0 ||
      value < 0
    ) {
      unknown += value;
      count++;
      continue;
    }
    paid += cents(p);
    open += cents(o);
  }
  return {
    recebido: paid / 100,
    emAberto: open / 100,
    semDetalhamento: unknown / 100,
    registrosSemDetalhamento: count,
  };
}
function groups(
  now: ViewLine[],
  before: ViewLine[],
  field: "categoria" | "centro",
): ExpenseRow[] {
  const names = new Set(
    [...now, ...before]
      .filter((l) => l.tipo === "DESPESA")
      .map((l) => l[field]),
  );
  return [...names].map((nome) => {
    const atual = sum(
        now.filter((l) => l[field] === nome),
        "DESPESA",
      ),
      anterior = sum(
        before.filter((l) => l[field] === nome),
        "DESPESA",
      );
    return {
      nome,
      atual,
      anterior,
      diferenca: Math.round((atual - anterior) * 100) / 100,
    };
  });
}
export async function dashboardForFilter(
  filter: ViewFilter,
  categoria?: string,
  signal?: AbortSignal,
): Promise<DashboardAnalysis> {
  const f = { ...filter, categoria: categoria ?? filter.categoria };
  const { current, previous, range } = await readPagePair(f, signal);
  const now = selectedLines(current, f),
    before = previous ? selectedLines(previous, f) : [];
  const r = sum(now, "RECEITA"),
    d = sum(now, "DESPESA"),
    pr = sum(before, "RECEITA"),
    pd = sum(before, "DESPESA");
  const result = Math.round((r - d) * 100) / 100,
    prior = Math.round((pr - pd) * 100) / 100;
  const currentItems = items(now, current.linhas),
    priorItems = items(before, previous?.linhas ?? []);
  return {
    clienteId: current.clienteId,
    inicio: current.intervalo.inicio,
    fim: current.intervalo.fim,
    inicioAnterior: range.inicio,
    fimAnterior: range.fim,
    hoje: current.hoje,
    categoria: f.categoria || null,
    indicadores: {
      receita: r,
      despesa: d,
      resultado: result,
      margem: margin(r, d),
      receitaMesAnterior: pr,
      despesaMesAnterior: pd,
      resultadoMesAnterior: prior,
      margemMesAnterior: margin(pr, pd),
      variacaoReceita: variation(pr, r),
      variacaoDespesa: variation(pd, d),
      variacaoResultado: variation(prior, result),
    },
    categorias: [
      ...new Set([
        ...current.categorias,
        ...(previous?.categorias ?? []),
        ...(f.categoria ? [f.categoria] : []),
      ]),
    ].sort(),
    meses: monthlyLines(
      now,
      current.intervalo.inicio,
      current.intervalo.fim,
    ).map((m) => ({
      ...m,
      emAndamento: m.periodo === current.hoje.slice(0, 7),
      recebimentos: receiptComposition(
        now.filter((l) => l.data.slice(0, 7) === m.periodo),
        f,
      ),
    })),
    variacoes: groups(now, before, "categoria").map((g) => ({
      categoria: g.nome,
      atual: g.atual,
      anterior: g.anterior,
      diferenca: g.diferenca,
    })),
    itens: currentItems,
    registros: {
      receitasAtual: currentItems.filter((l) => l.tipo === "RECEITA").length,
      despesasAtual: currentItems.filter((l) => l.tipo === "DESPESA").length,
      receitasAnterior: priorItems.filter((l) => l.tipo === "RECEITA").length,
      despesasAnterior: priorItems.filter((l) => l.tipo === "DESPESA").length,
    },
  };
}
export async function expensesForFilter(
  filter: ViewFilter,
  local: ExpenseFilters,
  signal: AbortSignal,
): Promise<ExpenseAnalysis> {
  const f = {
    ...filter,
    categoria: local.categoria ?? filter.categoria,
    centro: local.centroCusto ?? filter.centro,
  };
  const { current, previous, range } = await readPagePair(f, signal);
  const classifications =
    local.comportamento || local.natureza
      ? await getCostClassifications(signal)
      : [];
  const classify = (lines: ViewLine[]) =>
    lines.filter((l) => {
      if (l.tipo !== "DESPESA") return false;
      const c = classifications.find(
        (c) =>
          c.categoria.trim().toLocaleLowerCase() ===
          l.categoria.trim().toLocaleLowerCase(),
      );
      return (
        (!local.comportamento ||
          local.comportamento === (c?.comportamento ?? "NAO_CLASSIFICADO")) &&
        (!local.natureza ||
          local.natureza === (c?.natureza ?? "NAO_CLASSIFICADO"))
      );
    });
  const now = classify(selectedLines(current, f)),
    before = classify(previous ? selectedLines(previous, f) : []);
  const currentItems = items(now, current.linhas),
    priorItems = items(before, previous?.linhas ?? []);
  return {
    inicio: current.intervalo.inicio,
    fim: current.intervalo.fim,
    inicioAnterior: range.inicio,
    fimAnterior: range.fim,
    centroCusto: f.centro || null,
    categoria: f.categoria || null,
    comportamento: local.comportamento || null,
    natureza: local.natureza || null,
    total: sum(now, "DESPESA"),
    totalAnterior: sum(before, "DESPESA"),
    centrosDisponiveis: [
      ...new Set([
        ...current.centros,
        ...(previous?.centros ?? []),
        ...(f.centro ? [f.centro] : []),
      ]),
    ].sort(),
    categoriasDisponiveis: [
      ...new Set([
        ...current.categorias,
        ...(previous?.categorias ?? []),
        ...(f.categoria ? [f.categoria] : []),
      ]),
    ].sort(),
    centros: groups(now, before, "centro"),
    categorias: groups(now, before, "categoria"),
    itens: currentItems.map((i) => ({
      id: i.id,
      data: i.data,
      descricao: i.descricao,
      valorSelecionado: i.valorSelecionado,
      valorIntegral: i.valorContabilizadoIntegral,
    })),
    registros: { atual: currentItems.length, anterior: priorItems.length },
  };
}
export async function historyForFilter(
  filter: ViewFilter,
  signal?: AbortSignal,
): Promise<HistoryData[]> {
  const current = await read(filter, signal);
  return monthlyLines(
    selectedLines(current, filter),
    current.intervalo.inicio,
    current.intervalo.fim,
  ).map((m) => ({ ...m, margem: margin(m.receita, m.despesa) }));
}
const monthName = (period: string) =>
  new Date(period + "-01T12:00:00Z")
    .toLocaleDateString("pt-BR", { month: "short", timeZone: "UTC" })
    .replace(".", "") +
  "/" +
  period.slice(0, 4);
export async function cashForFilter(
  filter: ViewFilter,
  signal?: AbortSignal,
): Promise<RealizedCashFlow> {
  const current = await read(filter, signal),
    all = await read({ ...filter, periodo: "TODOS" }, signal);
  if (current.clienteId !== all.clienteId)
    throw new Error("A empresa mudou. Atualize a página.");
  const prior = selectedLines(all, filter).filter(
    (l) => l.data < current.intervalo.inicio,
  );
  let balance = cents(sum(prior, "RECEITA")) - cents(sum(prior, "DESPESA"));
  const lines = selectedLines(current, filter);
  const meses: CashFlowData[] = monthlyLines(
    lines,
    current.intervalo.inicio,
    current.intervalo.fim,
  ).map((m) => {
    const opening = balance;
    balance += cents(m.receita) - cents(m.despesa);
    return {
      mes: monthName(m.periodo),
      entradas: m.receita,
      saidas: m.despesa,
      saldoInicial: opening / 100,
      saldoFinal: balance / 100,
    };
  });
  const count = items(lines, current.linhas).length;
  const missing = Object.entries(current.exclusoes)
    .filter(([name]) => /Sem baixas|Sem líquido|Sem data|Sem valor/.test(name))
    .reduce((s, [, n]) => s + n, 0);
  return {
    clienteId: current.clienteId,
    inicio: current.intervalo.inicio,
    fim: current.intervalo.fim,
    meses,
    lancamentosConferidos: count,
    lancamentosSemDetalhamento: missing,
    baixasNoPeriodo: count,
    parcial: missing > 0,
    base:
      filter.criterio === "BAIXA_EXTRATO"
        ? "EXTRATO_IMPORTADO"
        : "CRITERIO_SELECIONADO",
  };
}
export async function goalTimelineForFilter(
  filter: ViewFilter,
  meta: SpendingGoal,
  signal: AbortSignal,
): Promise<GoalTimeline> {
  const data = await read({ ...filter, categoria: "", centro: "" }, signal);
  const start =
    meta.dataInicio > data.intervalo.inicio
      ? meta.dataInicio
      : data.intervalo.inicio;
  const end =
    meta.dataFim < data.intervalo.fim ? meta.dataFim : data.intervalo.fim;
  const lines = data.linhas.filter(
    (l) =>
      l.tipo === "DESPESA" &&
      l.data >= start &&
      l.data <= end &&
      match(meta.categoria, l.categoria) &&
      match(meta.centroCusto, l.centro),
  );
  const day = (value: string) =>
    new Date(value + "T12:00:00Z").getTime() / 86400000;
  const days = day(meta.dataFim) - day(meta.dataInicio) + 1;
  const elapsed = Math.max(
    0,
    Math.min(days, day(data.hoje) - day(meta.dataInicio) + 1),
  );
  const spent = sum(lines, "DESPESA"),
    toToday = sum(
      lines.filter((l) => l.data <= data.hoje),
      "DESPESA",
    );
  const complete = start === meta.dataInicio && end === meta.dataFim;
  const estimate =
    complete && meta.situacaoTemporal === "EM_ANDAMENTO" && elapsed > 0
      ? Math.round((toToday / elapsed) * days * 100) / 100
      : null;
  const points: GoalTimeline["pontos"] = [];
  for (let date = start; date <= end; ) {
    const accumulated = sum(
      lines.filter((l) => l.data <= date),
      "DESPESA",
    );
    points.push({
      data: date,
      acumuladoRegistrado: accumulated,
      ritmoIdeal:
        Math.round(
          ((meta.valorLimite * (day(date) - day(meta.dataInicio) + 1)) / days) *
            100,
        ) / 100,
      estimativa:
        estimate != null && date >= data.hoje
          ? Math.round(
              (toToday / elapsed) *
                (day(date) - day(meta.dataInicio) + 1) *
                100,
            ) / 100
          : null,
    });
    const next = new Date(date + "T12:00:00Z");
    next.setUTCDate(next.getUTCDate() + 1);
    date = iso(next);
  }
  const categories = groups(lines, [], "categoria").map((g) => ({
    categoria: g.nome,
    valor: g.atual,
  }));
  return {
    id: meta.id,
    inicio: start,
    fim: end,
    hoje: data.hoje,
    situacaoTemporal: meta.situacaoTemporal,
    diasTotais: days,
    diasDecorridos: elapsed,
    limite: meta.valorLimite,
    totalRegistrado: spent,
    registradoAteHoje: toToday,
    registradoDepoisHoje: Math.round((spent - toToday) * 100) / 100,
    ritmoIdealPercentual: (elapsed / days) * 100,
    estimativaFim: estimate,
    pontos: points,
    categorias: categories,
  };
}
