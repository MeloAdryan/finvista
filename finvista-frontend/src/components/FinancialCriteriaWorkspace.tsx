import {
  useCallback,
  useEffect,
  useId,
  useMemo,
  useState,
  type ReactNode,
  type FormEvent,
} from "react";
import {
  FinancialViewContext,
  FinancialViewUpdateContext,
} from "../contexts/financialViewContext";
import {
  criterionNames,
  getFinancialView,
  savedViewFilter,
  saveViewFilter,
  viewRange,
  type FinancialView,
  type ViewFilter,
} from "../services/financialViewService";
import "../styles/financial-criteria.css";

const supported = new Set([
  "dashboard",
  "metas",
  "projecao",
  "fluxo-caixa",
  "centros-custo",
  "despesas",
  "orcamentos",
  "historico",
]);
const periods = {
  SEMANAL: "Semanal",
  MENSAL: "Mensal",
  ANUAL: "Anual",
  TODOS: "Todo período importado",
  PERSONALIZADO: "Personalizado",
};

export default function FinancialCriteriaWorkspace({
  page,
  clienteId,
  children,
}: {
  page: string;
  clienteId?: number;
  children: ReactNode;
}) {
  const [applied, setApplied] = useState<ViewFilter>(() =>
    savedViewFilter(clienteId),
  );
  const [draft, setDraft] = useState<ViewFilter>(() =>
    savedViewFilter(clienteId),
  );
  const [meta, setMeta] = useState<FinancialView | null>(null);
  const [error, setError] = useState("");
  const [filtersOpen, setFiltersOpen] = useState(false);
  const id = useId();
  const enabled = supported.has(page),
    projection = page === "projecao";
  const scopedFilter = useMemo(
    () => ({
      ...applied,
      categoria: page === "dashboard" ? applied.categoria : "",
      centro: "",
      clienteId,
    }),
    [applied, clienteId, page],
  );
  const update = useCallback(
    (next: ViewFilter) => {
      saveViewFilter(next, clienteId);
      setApplied(next);
      setDraft(next);
      setMeta(null);
      setError("");
    },
    [clienteId],
  );
  useEffect(() => {
    if (!enabled || projection) return;
    const c = new AbortController();
    void getFinancialView({ ...applied, categoria: "", centro: "" }, c.signal)
      .then((data) => {
        if (clienteId != null && data.clienteId !== clienteId)
          throw new Error("A empresa mudou. Atualize a página.");
        if (!c.signal.aborted) setMeta(data);
      })
      .catch((e) => {
        if (!c.signal.aborted)
          setError(e instanceof Error ? e.message : "Falha nos filtros.");
      });
    return () => c.abort();
  }, [applied, clienteId, enabled, projection]);
  if (!enabled) return <>{children}</>;
  const baixa =
    draft.criterio === "BAIXA" || draft.criterio === "BAIXA_EXTRATO";
  function submit(e: FormEvent) {
    e.preventDefault();
    const range = viewRange(draft);
    if (range && (!range.inicio || !range.fim || range.inicio > range.fim)) {
      setError("Informe as datas em ordem válida.");
      return;
    }
    update({ ...draft });
    setFiltersOpen(false);
  }
  const range = projection
    ? viewRange(applied)
    : (meta?.intervalo ?? viewRange(applied));
  return (
    <FinancialViewContext.Provider value={scopedFilter}>
      <FinancialViewUpdateContext.Provider value={update}>
        {page === "dashboard" && (
          <header className="exec-compact-header">
            <div>
              <h1>Painel executivo</h1>
              <p>
                {criterionNames[applied.criterio]} ·{" "}
                {applied.categoria || "Todas as categorias"}
              </p>
            </div>
            <div className="exec-header-actions">
              <button
                type="button"
                onClick={() => setFiltersOpen(true)}
                aria-label="Selecionar período"
              >
                {range
                  ? applied.periodo === "MENSAL"
                    ? new Date(`${range.inicio}T12:00:00`).toLocaleDateString(
                        "pt-BR",
                        { month: "long", year: "numeric" },
                      )
                    : `${range.inicio.split("-").reverse().join("/")} – ${range.fim.split("-").reverse().join("/")}`
                  : "Todo período"}{" "}
                ▾
              </button>
              <button
                type="button"
                aria-expanded={filtersOpen}
                aria-controls={`${id}-filters`}
                onClick={() => setFiltersOpen((v) => !v)}
              >
                Filtros
              </button>
            </div>
          </header>
        )}
        <section
          id={`${id}-filters`}
          hidden={page === "dashboard" && !filtersOpen}
          className="fvf-page-filters"
          aria-label="Critérios para os gráficos da página"
        >
          <form className="fvf-filters" onSubmit={submit}>
            {!projection && (
              <label htmlFor={`${id}-criterion`}>
                Critério de data
                <select
                  aria-label="Critério de data"
                  id={`${id}-criterion`}
                  value={draft.criterio}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      criterio: e.target.value as ViewFilter["criterio"],
                      situacao: "TODAS",
                    })
                  }
                >
                  {Object.entries(criterionNames).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <label htmlFor={`${id}-period`}>
              Período
              <select
                aria-label="Período"
                id={`${id}-period`}
                value={draft.periodo}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    periodo: e.target.value as ViewFilter["periodo"],
                  })
                }
              >
                {Object.entries(periods).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
            {draft.periodo !== "TODOS" && draft.periodo !== "PERSONALIZADO" && (
              <label>
                Data de referência
                <input
                  type="date"
                  required
                  min="1900-01-01"
                  max="2200-12-31"
                  value={draft.referencia}
                  onChange={(e) =>
                    setDraft({ ...draft, referencia: e.target.value })
                  }
                />
              </label>
            )}
            {draft.periodo === "PERSONALIZADO" && (
              <>
                <label>
                  Data inicial
                  <input
                    type="date"
                    required
                    min="1900-01-01"
                    max="2200-12-31"
                    value={draft.inicio}
                    onChange={(e) =>
                      setDraft({ ...draft, inicio: e.target.value })
                    }
                  />
                </label>
                <label>
                  Data final
                  <input
                    type="date"
                    required
                    min={draft.inicio || "1900-01-01"}
                    max="2200-12-31"
                    value={draft.fim}
                    onChange={(e) =>
                      setDraft({ ...draft, fim: e.target.value })
                    }
                  />
                </label>
              </>
            )}
            {!projection && (
              <label>
                Situação
                <select
                  aria-label="Situação"
                  value={draft.situacao}
                  onChange={(e) =>
                    setDraft({
                      ...draft,
                      situacao: e.target.value as ViewFilter["situacao"],
                    })
                  }
                >
                  <option value="TODAS">Todas</option>
                  <option value="PAGAS">
                    {baixa ? "Com baixa conferida" : "Quitadas"}
                  </option>
                  {!baixa && (
                    <>
                      <option value="ABERTAS">Em aberto</option>
                      <option value="VENCIDAS">
                        Vencidas e em aberto hoje
                      </option>
                      <option value="PARCIAIS">Parcialmente pagas</option>
                    </>
                  )}
                </select>
              </label>
            )}
            {page === "dashboard" && (
              <label>
                Categoria
                <select
                  aria-label="Categoria"
                  value={draft.categoria}
                  onChange={(e) =>
                    setDraft({ ...draft, categoria: e.target.value })
                  }
                >
                  <option value="">Todas as categorias</option>
                  {Array.from(
                    new Set(
                      [...(meta?.categorias ?? []), applied.categoria].filter(
                        Boolean,
                      ),
                    ),
                  ).map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <div className="fvf-buttons">
              <button
                type="button"
                onClick={() => {
                  setDraft(applied);
                  setFiltersOpen(false);
                }}
              >
                Cancelar
              </button>
              <button type="submit">Aplicar filtro</button>
            </div>
          </form>
          <p className="fvf-filter-caption">
            {projection
              ? "Meses exibidos da projeção"
              : criterionNames[applied.criterio]}
            {range
              ? ` · ${range.inicio.split("-").reverse().join("/")} a ${range.fim.split("-").reverse().join("/")}`
              : " · Todo período importado"}
          </p>
          {error && <p role="alert">{error}</p>}
          {!projection &&
            meta &&
            applied.criterio === "BAIXA_EXTRATO" &&
            meta.linhas.length === 0 && (
              <p role="status">
                Nenhuma movimentação financeira do extrato neste recorte.
                Confira a empresa, o período e a inclusão do extrato. Este
                critério não usa valores por competência nem por vencimento.
              </p>
            )}
          {!projection && meta && (
            <details className="fvf-source-coverage">
              <summary>Critério e cobertura dos dados</summary>
              <p>{meta.aviso}</p>
              {Object.entries(meta.exclusoes)
                .filter(([, n]) => n > 0)
                .map(([label, count]) => (
                  <p key={label}>
                    {label}: {count}
                  </p>
                ))}
            </details>
          )}
        </section>
        {error && page === "dashboard" && !filtersOpen && (
          <p role="alert">{error}</p>
        )}
        {children}
      </FinancialViewUpdateContext.Provider>
    </FinancialViewContext.Provider>
  );
}
