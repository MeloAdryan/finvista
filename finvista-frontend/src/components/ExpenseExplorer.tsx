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
import DistributionBars from "./DistributionBars";
import "../styles/analysis-explorer.css";
export default function ExpenseExplorer({
  prioridade = "categorias",
  revision = 0,
}: {
  prioridade?: "centros" | "categorias";
  revision?: number;
}) {
  const [filtros, setFiltros] = useState<ExpenseFilters>({});
  const [draft, setDraft] = useState<ExpenseFilters>({});
  const [dados, setDados] = useState<ExpenseAnalysis | null>(null);
  const [erro, setErro] = useState("");
  const [loading, setLoading] = useState(true);
  const [tentativa, setTentativa] = useState(0);
  const [tabela, setTabela] = useState(false);
  const [todas, setTodas] = useState(false);
  const [hierarquia, setHierarquia] = useState(false);
  const [pagina, setPagina] = useState(0);
  useEffect(() => {
    const c = new AbortController();
    let ativo = true;
    async function carregar() {
      setLoading(true);
      try {
        const d = await getExpenseAnalysis(filtros, c.signal);
        if (ativo) {
          setDados(d);
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
  }, [filtros, revision, tentativa]);
  function aplicar(e: FormEvent) {
    e.preventDefault();
    if (!draft.inicio || !draft.fim || draft.fim < draft.inicio) {
      setErro("Informe as duas datas em ordem válida.");
      return;
    }
    setFiltros({ ...draft });
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
  const categorias =
    dados?.categorias.filter((m) => m.atual !== 0 || m.anterior !== 0) ?? [];
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
        ...dados.categorias.map((m) => [
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
  const painelCentros = dados && (
    <section className="ax-panel">
      <h2>Centros de custo</h2>
      <p className="ax-note">
        Barras ordenadas por valor. Clique para filtrar categorias e
        lançamentos; clique novamente para remover.
      </p>
      <DistributionBars
        dados={dados.centros}
        total={dados.total}
        selecionado={dados.centroCusto ?? ""}
        onSelecionar={(nome) => selecionar("centroCusto", nome)}
      />
      <p className="ax-note">
        Laranja: maior valor atual · azul: demais centros · traço: valor no
        período anterior. Escala em reais.
      </p>
    </section>
  );
  const painelCategorias = dados && (
    <section className="ax-panel">
      <div className="ax-heading">
        <div>
          <h2>Despesas por categoria</h2>
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
          <button type="button" onClick={() => setTodas(!todas)}>
            {todas ? "Mostrar menos" : "Ver todas"}
          </button>
        </div>
      </div>
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
          destaques={3}
        />
      )}
      <p className="ax-note">
        {todas || hierarquia
          ? `${categorias.length} categorias`
          : `Top ${Math.min(10, categorias.length)} de ${categorias.length} categorias`}
        . Clique para filtrar todos os resultados. Os três maiores valores
        recebem destaque; o traço marca o período anterior.
      </p>
    </section>
  );
  return (
    <div className="ax-explorer">
      <section className="ax-panel">
        <h1>
          {prioridade === "centros"
            ? "Análise dos centros de custo"
            : "Análise das despesas"}
        </h1>
        <form onSubmit={aplicar} className="ax-filter-grid">
          <label>
            Data inicial
            <input
              type="date"
              disabled={loading}
              value={draft.inicio ?? ""}
              onChange={(e) => setDraft({ ...draft, inicio: e.target.value })}
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
          <div className="ax-actions">
            <button disabled={loading} type="submit">
              Aplicar filtros
            </button>
            <button
              disabled={loading}
              type="button"
              onClick={() => setFiltros({})}
            >
              Voltar ao mês de referência
            </button>
          </div>
        </form>
        {dados && (
          <p className="ax-note">
            Exibido: {datas(dados.inicio, dados.fim)} · comparação:{" "}
            {datas(dados.inicioAnterior, dados.fimAnterior)} ·{" "}
            {dados.centroCusto || "Todos os centros"} ·{" "}
            {dados.categoria || "Todas as categorias"}. Os gráficos usam os
            filtros aplicados.
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
            <div className="ax-summary">
              <div>
                Despesa na seleção
                <strong>{moedaFinanceira(dados.total)}</strong>
              </div>
              <div>
                Período anterior
                <strong>{moedaFinanceira(dados.totalAnterior)}</strong>
              </div>
              <div>
                Diferença
                <strong>
                  {moedaFinanceira(dados.total - dados.totalAnterior)}
                </strong>
              </div>
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
                {painelCategorias}
              </>
            ) : (
              <>
                {painelCategorias}
                {painelCentros}
              </>
            )}
            <section className="ax-panel">
              <div className="ax-heading">
                <h2>Valores exatos</h2>
                <div className="ax-actions">
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
                        ["Centro", "Atual", "Anterior", "Diferença"],
                        ...dados.centros.map((m) => [
                          m.nome,
                          m.atual,
                          m.anterior,
                          m.diferenca,
                        ]),
                      ])
                    }
                  >
                    CSV de centros
                  </button>
                </div>
              </div>
              {tabela && (
                <div className="ax-table">
                  <table>
                    <thead>
                      <tr>
                        <th>Categoria</th>
                        <th>Atual</th>
                        <th>Anterior</th>
                        <th>Diferença</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dados.categorias.map((m) => (
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
                  CSV de lançamentos
                </button>
              </div>
              <div className="ax-table">
                <table>
                  <thead>
                    <tr>
                      <th>Data</th>
                      <th>Descrição</th>
                      <th>Selecionado</th>
                      <th>Integral</th>
                    </tr>
                  </thead>
                  <tbody>
                    {dados.itens
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
                  {Math.max(1, Math.ceil(dados.itens.length / 15))}
                </span>
                <button
                  type="button"
                  disabled={(pagina + 1) * 15 >= dados.itens.length}
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
