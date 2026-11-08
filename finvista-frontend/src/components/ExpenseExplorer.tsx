import { criterionNames } from "../services/financialViewService";
import { useContext } from "react";
import {
  FinancialViewContext,
  FinancialViewUpdateContext,
} from "../contexts/financialViewContext";
import { useEffect, useState, type FormEvent } from "react";
import {
  getExpenseAnalysis,
  type ExpenseAnalysis,
  type ExpenseFilters,
} from "../services/expenseExplorerService";
import { agruparCategorias } from "../services/analysisChartData";
import {
  exportarFinanceiro,
  moedaFinanceira,
} from "../services/financialVisualization";
import { useAnalysisPeriod } from "../contexts/analysisPeriodContext";
import { descreverRegistros } from "../services/comparisonCoverage";
import DistributionBars from "./DistributionBars";
import "../styles/analysis-explorer.css";
export default function ExpenseExplorer({
  prioridade = "categorias",
  revision = 0,
}: {
  prioridade?: "centros" | "categorias";
  revision?: number;
}) {
  const viewFilter = useContext(FinancialViewContext);
  const updateView = useContext(FinancialViewUpdateContext);
  const { periodo, atualizarPeriodo } = useAnalysisPeriod();
  const [filtros, setFiltros] = useState<ExpenseFilters>(() => ({
    ...periodo,
  }));
  const [draft, setDraft] = useState<ExpenseFilters>(() => ({ ...periodo }));
  const [dados, setDados] = useState<ExpenseAnalysis | null>(null);
  const [erro, setErro] = useState("");
  const [loading, setLoading] = useState(true);
  const [tentativa, setTentativa] = useState(0);
  const [tabela, setTabela] = useState(false);
  const [todas, setTodas] = useState(false);
  const [todosCentros, setTodosCentros] = useState(false);
  const [tipoTabela, setTipoTabela] = useState<"categorias" | "centros">(
    prioridade,
  );
  const [filtrosAbertos, setFiltrosAbertos] = useState(false);
  const [hierarquia, setHierarquia] = useState(false);
  const [pagina, setPagina] = useState(0);
  const [busca, setBusca] = useState("");
  const despesas = prioridade === "categorias";
  const normalizar = (texto: string) =>
    texto.normalize("NFD").replace(/\p{M}/gu, "").toLocaleLowerCase("pt-BR");
  const termo = despesas ? normalizar(busca.trim()) : "";
  const itensVisiveis =
    dados?.itens.filter((item) =>
      normalizar(item.descricao ?? "").includes(termo),
    ) ?? [];
  useEffect(() => {
    const c = new AbortController();
    let ativo = true;
    async function carregar() {
      setLoading(true);
      try {
        const d = await getExpenseAnalysis(
          filtros,
          c.signal,
          viewFilter ?? undefined,
        );
        if (ativo) {
          setDados(d);
          atualizarPeriodo({
            inicio: d.inicio,
            fim: d.fim,
            categoria: d.categoria ?? undefined,
          });
          setDraft({ ...filtros, inicio: d.inicio, fim: d.fim });
          setErro("");
          setPagina(0);
        }
      } catch (e) {
        if (ativo && !c.signal.aborted)
          setErro(
            e instanceof Error ? e.message : "Falha ao consultar análise.",
          );
      } finally {
        if (ativo) setLoading(false);
      }
    }
    void carregar();
    return () => {
      ativo = false;
      c.abort();
    };
  }, [filtros, revision, tentativa, atualizarPeriodo, viewFilter]);
  function aplicar(e: FormEvent) {
    e.preventDefault();
    if (
      (!despesas || !viewFilter) &&
      (!draft.inicio || !draft.fim || draft.fim < draft.inicio)
    ) {
      setErro("Informe as duas datas em ordem válida.");
      return;
    }
    if (
      !despesas &&
      draft.inicio &&
      draft.fim &&
      viewFilter &&
      updateView &&
      (draft.inicio !== dados?.inicio || draft.fim !== dados?.fim)
    )
      updateView({
        ...viewFilter,
        periodo: "PERSONALIZADO",
        inicio: draft.inicio,
        fim: draft.fim,
      });
    setFiltros(
      despesas && viewFilter
        ? { ...draft, inicio: undefined, fim: undefined }
        : { ...draft },
    );
    if (despesas) setFiltrosAbertos(false);
  }
  function selecionar(tipo: "centroCusto" | "categoria", nome: string) {
    if (!dados) return;
    setFiltros({
      ...filtros,
      inicio: dados.inicio,
      fim: dados.fim,
      [tipo]: filtros[tipo] === nome ? "" : nome,
    });
  }
  const datas = (de: string, ate: string) =>
    `${de.split("-").reverse().join("/")} — ${ate.split("-").reverse().join("/")}`;
  const ordenar = (linhas: ExpenseAnalysis["categorias"]) =>
    [...linhas].sort(
      (a, b) => b.atual - a.atual || a.nome.localeCompare(b.nome, "pt-BR"),
    );
  const categorias = ordenar(
    dados?.categorias.filter((m) => m.atual !== 0 || m.anterior !== 0) ?? [],
  );
  const centros = ordenar(
    dados?.centros.filter((m) => m.atual !== 0 || m.anterior !== 0) ?? [],
  );
  const linhasTabela = tipoTabela === "centros" ? centros : categorias;
  const topCinco = categorias.slice(0, 5).reduce((s, m) => s + m.atual, 0);
  const exportar = () => {
    if (dados)
      exportarFinanceiro("despesas-por-categoria.csv", [
        [
          "Categoria",
          "Atual",
          "Anterior",
          "Diferença",
          "Participação (%)",
          "Início",
          "Fim",
        ],
        ...ordenar(dados.categorias).map((m) => [
          m.nome,
          m.atual,
          m.anterior,
          m.diferenca,
          dados.total > 0 ? (m.atual / dados.total) * 100 : null,
          dados.inicio,
          dados.fim,
        ]),
      ]);
  };
  const comparavel =
    !!dados &&
    (dados.registros?.atual ?? 0) > 0 &&
    (dados.registros?.anterior ?? 0) > 0 &&
    dados.totalAnterior > 0;
  const diferenca = dados ? dados.total - dados.totalAnterior : 0;
  const percentualVariacao =
    comparavel && dados ? (diferenca / dados.totalAnterior) * 100 : null;
  const painelCentros = dados && (
    <section className="ax-panel">
      <div className="ax-heading">
        <div>
          <h2>Gastos por centro de custo</h2>
          <p className="ax-note">Onde os gastos estão concentrados?</p>
        </div>
        {centros.length > 10 && (
          <button
            type="button"
            aria-pressed={todosCentros}
            onClick={() => setTodosCentros(!todosCentros)}
          >
            {todosCentros ? "Mostrar menos centros" : "Ver todos os centros"}
          </button>
        )}
      </div>
      <p className="ax-note">
        Barras ordenadas por valor. Clique para filtrar categorias e
        lançamentos; clique novamente para remover.
      </p>
      <DistributionBars
        dados={todosCentros ? centros : centros.slice(0, 10)}
        total={dados.total}
        selecionado={dados.centroCusto ?? ""}
        onSelecionar={(nome) => selecionar("centroCusto", nome)}
      />
      {centros.length === 0 && (
        <p className="ax-note">
          Nenhum centro com valores no período atual ou anterior.
        </p>
      )}
      <p className="ax-note">
        {todosCentros
          ? `${centros.length} centros`
          : `Top ${Math.min(10, centros.length)} de ${centros.length} centros`}
        . Laranja: maior valor atual · azul: demais centros · traço: valor no
        período anterior. Escala em reais.
      </p>
    </section>
  );
  const painelCategorias = dados && (
    <section className="ax-panel">
      <div className="ax-heading">
        <div>
          <h2>
            {prioridade === "centros" && dados.centroCusto
              ? `Categorias de ${dados.centroCusto}`
              : "Despesas por categoria"}
          </h2>
          <p className="ax-note">
            {dados.total > 0
              ? `As cinco maiores somam ${((topCinco / dados.total) * 100).toLocaleString("pt-BR", { maximumFractionDigits: 1 })}% do total.`
              : "Participação percentual indisponível quando o total é zero ou negativo."}
          </p>
        </div>
        <div className="ax-actions">
          <button
            type="button"
            aria-pressed={hierarquia}
            onClick={() => setHierarquia(!hierarquia)}
          >
            {hierarquia ? "Ver categorias" : "Agrupar por código"}
          </button>
          {!hierarquia && categorias.length > 10 && (
            <button
              type="button"
              aria-pressed={todas}
              onClick={() => setTodas(!todas)}
            >
              {todas ? "Mostrar menos" : "Ver todas"}
            </button>
          )}
        </div>
      </div>
      {prioridade === "categorias" && (
        <div className="ax-shape-legend" aria-label="Legenda da comparação">
          <span>▰ Barra: valor atual</span>
          <span>◆ Losango: período anterior</span>
          <span>1–3: maiores valores do ranking exibido</span>
        </div>
      )}
      {hierarquia ? (
        <div className="ax-groups">
          {agruparCategorias(categorias).map((g) => (
            <details key={g.nome}>
              <summary>
                {g.nome}
                <strong>{moedaFinanceira(g.atual)}</strong>
              </summary>
              <DistributionBars
                dados={g.filhos}
                total={dados.total}
                selecionado={dados.categoria ?? ""}
                onSelecionar={(nome) => selecionar("categoria", nome)}
                ranking={prioridade === "categorias"}
                destaques={3}
              />
            </details>
          ))}
        </div>
      ) : (
        <DistributionBars
          dados={todas ? categorias : categorias.slice(0, 10)}
          total={dados.total}
          selecionado={dados.categoria ?? ""}
          onSelecionar={(nome) => selecionar("categoria", nome)}
          ranking={prioridade === "categorias"}
          destaques={3}
        />
      )}
      {categorias.length === 0 && (
        <p className="ax-note">
          Nenhuma categoria com valores no período atual ou anterior.
        </p>
      )}
      <p className="ax-note">
        {todas || hierarquia
          ? `${categorias.length} categorias`
          : `Top ${Math.min(10, categorias.length)} de ${categorias.length} categorias`}
        . Clique para filtrar todos os resultados. Os três maiores valores
        {prioridade === "categorias"
          ? "recebem destaque; o losango marca o período anterior."
          : "recebem destaque; o traço marca o período anterior."}
      </p>
    </section>
  );
  return (
    <div
      className={`ax-explorer ${prioridade === "categorias" ? "ax-expenses" : ""}`}
    >
      <section className="ax-panel">
        <h1>
          {prioridade === "centros"
            ? "Análise dos centros de custo"
            : "Direcionamento das despesas"}
        </h1>
        <p className="ax-note">
          {prioridade === "centros"
            ? "Compare os centros e selecione um para consultar suas categorias e lançamentos."
            : "Explore as categorias de despesas e os lançamentos que compõem cada valor."}
        </p>
        {prioridade === "categorias" && (
          <>
            <div className="ax-filter-toolbar">
              <p className="ax-note">
                Categoria, centro e filtros adicionais afetam toda a análise.
              </p>
              <button
                type="button"
                aria-expanded={filtrosAbertos}
                aria-controls="expense-local-filters"
                onClick={() => setFiltrosAbertos(!filtrosAbertos)}
              >
                {filtrosAbertos ? "Ocultar mais filtros" : "Mais filtros"}
              </button>
            </div>
            {dados && (
              <div
                className="ax-applied-chips"
                aria-label="Critérios aplicados à análise"
              >
                {viewFilter && (
                  <span>{criterionNames[viewFilter.criterio]}</span>
                )}
                <span>Centro: {dados.centroCusto || "Todos"}</span>
                <span>Categoria: {dados.categoria || "Todas"}</span>
                {filtros.comportamento && (
                  <span>
                    Comportamento:{" "}
                    {filtros.comportamento === "FIXO"
                      ? "Fixo"
                      : filtros.comportamento === "VARIAVEL"
                        ? "Variável"
                        : "Não classificado"}
                  </span>
                )}
                {filtros.natureza && (
                  <span>
                    Natureza:{" "}
                    {filtros.natureza
                      .replaceAll("_", " ")
                      .toLocaleLowerCase("pt-BR")}
                  </span>
                )}
              </div>
            )}
          </>
        )}
        <form onSubmit={aplicar} className="ax-filter-grid">
          {(!despesas || !viewFilter) && (
            <>
              <label>
                Data inicial
                <input
                  type="date"
                  disabled={loading}
                  value={draft.inicio ?? ""}
                  onChange={(e) =>
                    setDraft({ ...draft, inicio: e.target.value })
                  }
                />
              </label>
              <label>
                Data final
                <input
                  type="date"
                  disabled={loading}
                  value={draft.fim ?? ""}
                  onChange={(e) => setDraft({ ...draft, fim: e.target.value })}
                />
              </label>
            </>
          )}
          <label>
            Centro de custo
            <select
              disabled={loading}
              value={draft.centroCusto ?? ""}
              onChange={(e) =>
                setDraft({ ...draft, centroCusto: e.target.value })
              }
            >
              <option value="">Todos</option>
              {dados?.centrosDisponiveis.map((n) => (
                <option key={n}>{n}</option>
              ))}
            </select>
          </label>
          <label>
            Categoria
            <select
              disabled={loading}
              value={draft.categoria ?? ""}
              onChange={(e) =>
                setDraft({ ...draft, categoria: e.target.value })
              }
            >
              <option value="">Todas</option>
              {dados?.categoriasDisponiveis.map((n) => (
                <option key={n}>{n}</option>
              ))}
            </select>
          </label>
          <div
            id={despesas ? "expense-local-filters" : undefined}
            className="ax-advanced-fields"
            hidden={despesas && !filtrosAbertos}
          >
            <label>
              Comportamento
              <select
                disabled={loading}
                value={draft.comportamento ?? ""}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    comportamento: e.target
                      .value as ExpenseFilters["comportamento"],
                  })
                }
              >
                <option value="">Todos</option>
                <option value="FIXO">Fixo</option>
                <option value="VARIAVEL">Variável</option>
                <option value="NAO_CLASSIFICADO">Não classificado</option>
              </select>
            </label>
            <label>
              Natureza
              <select
                disabled={loading}
                value={draft.natureza ?? ""}
                onChange={(e) =>
                  setDraft({
                    ...draft,
                    natureza: e.target.value as ExpenseFilters["natureza"],
                  })
                }
              >
                <option value="">Todas</option>
                {[
                  ["TAXAS", "Taxas"],
                  ["IMPOSTOS", "Impostos"],
                  ["PESSOAL", "Pessoal"],
                  ["SERVICOS", "Serviços"],
                  ["MATERIAIS", "Materiais"],
                  ["OUTROS", "Outros"],
                  ["NAO_CLASSIFICADO", "Não classificado"],
                ].map(([v, n]) => (
                  <option key={v} value={v}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="ax-actions">
            <button disabled={loading} type="submit">
              Aplicar filtros
            </button>
            <button
              disabled={loading}
              type="button"
              onClick={() => setFiltros({})}
            >
              {despesas && viewFilter
                ? "Limpar filtros locais"
                : "Voltar ao mês de referência"}
            </button>
          </div>
        </form>
        {dados && (
          <p className="ax-note">
            Exibido: {datas(dados.inicio, dados.fim)} · comparação:{" "}
            {datas(dados.inicioAnterior, dados.fimAnterior)} ·{" "}
            {dados.centroCusto || "Todos os centros"} ·{" "}
            {dados.categoria || "Todas as categorias"}. Os gráficos usam os
            filtros aplicados. Datas e categoria são compartilhadas com o
            painel. Centro, comportamento e natureza se aplicam somente a esta
            análise.
          </p>
        )}
      </section>
      {loading ? (
        <p role="status">Atualizando análise…</p>
      ) : erro ? (
        <p role="alert">
          {erro}{" "}
          <button type="button" onClick={() => setTentativa(tentativa + 1)}>
            Tentar novamente
          </button>
        </p>
      ) : (
        dados && (
          <>
            <p className="ax-note" role="status">
              {descreverRegistros(
                dados.registros?.atual,
                dados.registros?.anterior,
              )}{" "}
              A existência de registros não confirma importação completa.
            </p>
            <div className="ax-summary">
              <div>
                {despesas ? "Total filtrado" : "Despesa na seleção"}
                <strong>{moedaFinanceira(dados.total)}</strong>
              </div>
              <div>
                Período anterior
                <strong>{moedaFinanceira(dados.totalAnterior)}</strong>
              </div>
              <div>
                Diferença entre totais registrados
                <strong
                  className={
                    prioridade === "categorias" && comparavel
                      ? diferenca > 0
                        ? "ax-increase"
                        : diferenca < 0
                          ? "ax-decrease"
                          : ""
                      : ""
                  }
                >
                  {prioridade === "categorias" && comparavel
                    ? `${diferenca > 0 ? "+" : ""}${moedaFinanceira(diferenca)}`
                    : moedaFinanceira(dados.total - dados.totalAnterior)}
                </strong>
                {prioridade === "categorias" && (
                  <small className="ax-variation-note">
                    {percentualVariacao === null
                      ? "Sem base comparável para percentual"
                      : `${percentualVariacao > 0 ? "↑ +" : percentualVariacao < 0 ? "↓ " : ""}${percentualVariacao.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}% sobre o período anterior`}
                  </small>
                )}
              </div>
              {despesas && (
                <div>
                  Maior categoria
                  <strong>
                    {categorias[0]?.atual > 0 ? categorias[0].nome : "—"}
                  </strong>
                  {categorias[0]?.atual > 0 && (
                    <small>{moedaFinanceira(categorias[0].atual)}</small>
                  )}
                </div>
              )}
            </div>
            {(dados.centroCusto || dados.categoria) && (
              <div className="ax-actions">
                <button
                  type="button"
                  onClick={() =>
                    setFiltros({ ...filtros, centroCusto: "", categoria: "" })
                  }
                >
                  Limpar seleção dos gráficos
                </button>
              </div>
            )}
            {prioridade === "centros" ? (
              <>
                {painelCentros}
                {dados.centroCusto ? (
                  painelCategorias
                ) : (
                  <details className="ax-secondary">
                    <summary>Consultar categorias dos centros</summary>
                    {painelCategorias}
                  </details>
                )}
              </>
            ) : (
              <>
                {painelCategorias}
                <details className="ax-secondary">
                  <summary>Consultar distribuição por centro de custo</summary>
                  {painelCentros}
                </details>
              </>
            )}
            <section className="ax-panel">
              <div className="ax-heading">
                <h2>
                  {tipoTabela === "centros"
                    ? "Valores por centro de custo"
                    : "Valores por categoria"}
                </h2>
                <div className="ax-actions">
                  <button
                    type="button"
                    aria-pressed={tipoTabela === "categorias"}
                    onClick={() => setTipoTabela("categorias")}
                  >
                    Categorias
                  </button>
                  <button
                    type="button"
                    aria-pressed={tipoTabela === "centros"}
                    onClick={() => setTipoTabela("centros")}
                  >
                    Centros
                  </button>
                  <button type="button" onClick={() => setTabela(!tabela)}>
                    {tabela ? "Ocultar tabela" : "Ver como tabela"}
                  </button>
                  <button type="button" onClick={exportar}>
                    CSV de categorias
                  </button>
                  <button
                    type="button"
                    onClick={() =>
                      exportarFinanceiro("despesas-por-centro.csv", [
                        [
                          "Centro",
                          "Atual",
                          "Anterior",
                          "Diferença",
                          "Participação (%)",
                          "Início",
                          "Fim",
                        ],
                        ...ordenar(dados.centros).map((m) => [
                          m.nome,
                          m.atual,
                          m.anterior,
                          m.diferenca,
                          dados.total > 0
                            ? (m.atual / dados.total) * 100
                            : null,
                          dados.inicio,
                          dados.fim,
                        ]),
                      ])
                    }
                  >
                    CSV de centros
                  </button>
                </div>
              </div>
              {tabela && (
                <div
                  className="ax-table"
                  tabIndex={0}
                  role="region"
                  aria-label="Tabela financeira, role horizontalmente para ver todas as colunas"
                >
                  <table>
                    <thead>
                      <tr>
                        <th>
                          {tipoTabela === "centros"
                            ? "Centro de custo"
                            : "Categoria"}
                        </th>
                        <th>Atual</th>
                        <th>Anterior</th>
                        <th>Diferença</th>
                      </tr>
                    </thead>
                    <tbody>
                      {linhasTabela.map((m) => (
                        <tr key={m.nome}>
                          <th>{m.nome}</th>
                          <td>{moedaFinanceira(m.atual)}</td>
                          <td>{moedaFinanceira(m.anterior)}</td>
                          <td>{moedaFinanceira(m.diferenca)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
            <section className="ax-panel">
              <div className="ax-heading">
                <div>
                  <h2>Lançamentos da seleção</h2>
                  <p className="ax-note">
                    {dados.itens.length} lançamentos. O valor selecionado soma
                    só as parcelas dos rateios que atendem aos filtros.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() =>
                    exportarFinanceiro("lancamentos-selecionados.csv", [
                      [
                        "ID",
                        "Data",
                        "Descrição",
                        "Valor selecionado",
                        "Valor integral",
                      ],
                      ...dados.itens.map((m) => [
                        String(m.id),
                        m.data,
                        m.descricao,
                        m.valorSelecionado,
                        m.valorIntegral,
                      ]),
                    ])
                  }
                >
                  {despesas ? "CSV do recorte completo" : "CSV de lançamentos"}
                </button>
              </div>
              {despesas && (
                <label className="ax-expense-search">
                  Busca nesta tabela
                  <input
                    type="search"
                    placeholder="Buscar descrição"
                    value={busca}
                    onChange={(event) => {
                      setBusca(event.target.value);
                      setPagina(0);
                    }}
                  />
                  <span role="status">
                    {itensVisiveis.length} de {dados.itens.length} lançamentos
                  </span>
                </label>
              )}
              <div
                className="ax-table"
                tabIndex={0}
                role="region"
                aria-label="Tabela financeira, role horizontalmente para ver todas as colunas"
              >
                <table>
                  <thead>
                    <tr>
                      <th>
                        {despesas && viewFilter
                          ? {
                              COMPETENCIA: "Competência",
                              VENCIMENTO: "Vencimento",
                              BAIXA: "Data da baixa",
                              BAIXA_EXTRATO: "Data do movimento",
                            }[viewFilter.criterio]
                          : "Data"}
                      </th>
                      <th>Descrição</th>
                      <th>Selecionado</th>
                      <th>Integral</th>
                    </tr>
                  </thead>
                  <tbody>
                    {itensVisiveis
                      .slice(pagina * 15, pagina * 15 + 15)
                      .map((m) => (
                        <tr key={m.id}>
                          <td>{m.data.split("-").reverse().join("/")}</td>
                          <th>{m.descricao}</th>
                          <td>{moedaFinanceira(m.valorSelecionado)}</td>
                          <td>{moedaFinanceira(m.valorIntegral)}</td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
              <div className="ax-actions">
                <button
                  type="button"
                  disabled={pagina === 0}
                  onClick={() => setPagina(pagina - 1)}
                >
                  Anterior
                </button>
                <span>
                  {pagina + 1} de{" "}
                  {Math.max(1, Math.ceil(itensVisiveis.length / 15))}
                </span>
                <button
                  type="button"
                  disabled={(pagina + 1) * 15 >= itensVisiveis.length}
                  onClick={() => setPagina(pagina + 1)}
                >
                  Próxima
                </button>
              </div>
            </section>
            <p className="ax-note">
              Base: valores contabilizados pela data de análise, incluindo
              registros em aberto. Ajustes negativos permanecem nos totais e nas
              barras. Percentuais podem superar 100% quando houver ajustes
              negativos em outras categorias.
            </p>
          </>
        )
      )}
    </div>
  );
}
