import { viewRange } from "../services/financialViewService";
import { useContext } from "react";
import {
  FinancialViewContext,
  FinancialViewUpdateContext,
} from "../contexts/financialViewContext";
import FinancialPositionPanel from "../components/FinancialPositionPanel";
import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";

import {
  getDashboardAnalysis,
  dataBR,
  type DashboardAnalysis,
} from "../services/dashboardAnalysisService";
import { useAnalysisPeriod } from "../contexts/analysisPeriodContext";
import DashboardMonthlyChart from "../charts/DashboardMonthlyChart";
import FinancialWaterfall from "../charts/FinancialWaterfall";
import DashboardFilteredDetail from "../components/DashboardFilteredDetail";
import "../styles/dashboard-filters.css";
import FinancialHelp from "../components/FinancialHelp";
import FinancialAlertsPanel from "../components/FinancialAlertsPanel";

import {
  listarMetas,
  type SpendingGoal,
} from "../services/spendingGoalService";

import { descreverRegistros } from "../services/comparisonCoverage";

import SpendingGoalGauge from "../components/SpendingGoalGauge";

import "../styles/dashboard.css";
import "../styles/dashboard-polish.css";
import { prepararComposicaoExecutiva } from "../services/analysisChartData";
import { brl, exportarCSV } from "../services/dashboardAnalysisService";
import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Legend,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

function Dashboard() {
  const viewFilter = useContext(FinancialViewContext);
  const positionRange = viewFilter ? viewRange(viewFilter) : null;
  const updateView = useContext(FinancialViewUpdateContext);
  const { periodo, atualizarPeriodo } = useAnalysisPeriod();
  const [periodoInicial] = useState(() => ({ ...periodo }));
  const [analysisFilter, setAnalysisFilter] = useState(viewFilter);
  const [analise, setAnalise] = useState<DashboardAnalysis | null>(null);
  const [inicio, setInicio] = useState(periodoInicial.inicio ?? "");
  const [fim, setFim] = useState(periodoInicial.fim ?? "");
  const [categoria, setCategoria] = useState(periodoInicial.categoria ?? "");
  const [metas, setMetas] = useState<SpendingGoal[]>([]);
  const [metaSelecionadaId, setMetaSelecionadaId] = useState<number | null>(
    null,
  );
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [detalhamento, setDetalhamento] = useState<
    "RECEITA" | "DESPESA" | null
  >(null);
  const consulta = useRef<AbortController | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    consulta.current = controller;
    async function abrir() {
      setCarregando(true);
      setErro(null);
      try {
        const [resultado, goals] = await Promise.all([
          getDashboardAnalysis(
            periodoInicial.inicio,
            periodoInicial.fim,
            viewFilter
              ? viewFilter.categoria || undefined
              : periodoInicial.categoria,
            controller.signal,
            viewFilter ?? undefined,
          ),
          listarMetas(
            {},
            viewFilter
              ? { ...viewFilter, categoria: "", centro: "" }
              : undefined,
          ),
        ]);
        if (!controller.signal.aborted) {
          setAnalise(resultado);
          setAnalysisFilter(viewFilter);
          setInicio(resultado.inicio);
          setFim(resultado.fim);
          setCategoria(resultado.categoria ?? "");
          atualizarPeriodo({
            inicio: resultado.inicio,
            fim: resultado.fim,
            categoria: resultado.categoria ?? undefined,
          });
          setMetas(goals);
        }
      } catch (error) {
        if (!controller.signal.aborted)
          setErro(
            error instanceof Error
              ? error.message
              : "Falha ao consultar painel.",
          );
      } finally {
        if (!controller.signal.aborted) setCarregando(false);
      }
    }
    void abrir();
    return () => {
      controller.abort();
      consulta.current?.abort();
    };
  }, [periodoInicial, atualizarPeriodo, viewFilter]);
  async function aplicar(
    event?: FormEvent<HTMLFormElement>,
    seisMeses = false,
    categoriaGrafico?: string,
  ) {
    event?.preventDefault();
    let primeiro =
      categoriaGrafico !== undefined && analise ? analise.inicio : inicio;
    const ultimo =
      (seisMeses || categoriaGrafico !== undefined) && analise
        ? analise.fim
        : fim;
    const categoriaConsulta =
      categoriaGrafico !== undefined ? categoriaGrafico : categoria;
    if (categoriaGrafico !== undefined) {
      setCategoria(categoriaConsulta);
      setInicio(primeiro);
      setFim(ultimo);
    }
    if (seisMeses && analise) {
      const [y, m] = analise.fim.split("-").map(Number);
      const date = new Date(Date.UTC(y, m - 6, 1));
      primeiro = `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}-01`;
      setInicio(primeiro);
      setFim(ultimo);
    }
    if (!primeiro || !ultimo || primeiro > ultimo) {
      setErro("Informe as duas datas em ordem válida.");
      return;
    }
    if (viewFilter && updateView) {
      updateView({
        ...viewFilter,
        periodo: "PERSONALIZADO",
        inicio: primeiro,
        fim: ultimo,
        categoria: categoriaConsulta || "",
      });
      return;
    }
    consulta.current?.abort();
    const controller = new AbortController();
    consulta.current = controller;
    setCarregando(true);
    setErro(null);
    setDetalhamento(null);
    try {
      const result = await getDashboardAnalysis(
        primeiro,
        ultimo,
        categoriaConsulta || undefined,
        controller.signal,
      );
      if (!controller.signal.aborted) {
        setAnalise(result);
        atualizarPeriodo({
          inicio: result.inicio,
          fim: result.fim,
          categoria: result.categoria ?? undefined,
        });
        setMetaSelecionadaId(null);
      }
    } catch (error) {
      if (!controller.signal.aborted)
        setErro(
          error instanceof Error ? error.message : "Falha ao aplicar filtros.",
        );
    } finally {
      if (!controller.signal.aborted) setCarregando(false);
    }
  }
  const metasCompativeis = useMemo(() => {
    if (!analise) return [];
    const priority = { EXCEDIDA: 3, ALERTA: 2, NORMAL: 1 };
    return metas
      .filter((m) => m.dataInicio <= analise.fim && m.dataFim >= analise.inicio)
      .sort(
        (a, b) =>
          priority[b.status] - priority[a.status] ||
          b.percentualUtilizado - a.percentualUtilizado,
      );
  }, [metas, analise]);
  const metaSelecionada =
    metasCompativeis.find((m) => m.id === metaSelecionadaId) ??
    metasCompativeis[0] ??
    null;
  const dados = analise?.indicadores;
  const caixa =
    analysisFilter?.criterio === "BAIXA" ||
    analysisFilter?.criterio === "BAIXA_EXTRATO";
  const nomes = caixa
    ? {
        receita: "Recebimentos",
        despesa: "Pagamentos",
        resultado: "Resultado dos movimentos",
        margem: "Resultado / recebimentos",
      }
    : {
        receita: "Receitas",
        despesa: "Despesas",
        resultado: "Resultado do período",
        margem: "Resultado / receitas",
      };

  const formatarMoeda = (valor: number) =>
    valor.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });

  const formatarPercentual = (valor: number) =>
    valor.toLocaleString("pt-BR", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });

  if (
    viewFilter &&
    JSON.stringify(analysisFilter) !== JSON.stringify(viewFilter)
  ) {
    return (
      <div className="dashboard-state" role={erro ? "alert" : "status"}>
        {erro
          ? `Não foi possível aplicar o período selecionado: ${erro}`
          : "Atualizando indicadores para o período selecionado…"}
      </div>
    );
  }

  if (carregando && !analise) {
    return (
      <div className="dashboard-state">
        <div className="dashboard-loader" />

        <p>Carregando painel executivo...</p>
      </div>
    );
  }

  if (erro && !analise) {
    return (
      <div className="dashboard-state dashboard-state-error">
        <strong>Não foi possível carregar o painel.</strong>

        <p>{erro}</p>
      </div>
    );
  }

  if (!dados || !analise) {
    return (
      <div className="dashboard-state">
        <p>Nenhum dado disponível.</p>
      </div>
    );
  }

  const contagens = analise.registros;
  const resumosComparacao = [
    resumoComparacao(
      dados.receita,
      dados.receitaMesAnterior,
      contagens?.receitasAtual,
      contagens?.receitasAnterior,
    ),
    resumoComparacao(
      dados.despesa,
      dados.despesaMesAnterior,
      contagens?.despesasAtual,
      contagens?.despesasAnterior,
    ),
    resumoComparacao(
      dados.resultado,
      dados.resultadoMesAnterior,
      contagens ? contagens.receitasAtual + contagens.despesasAtual : undefined,
      contagens
        ? contagens.receitasAnterior + contagens.despesasAnterior
        : undefined,
    ),
    dados.receita > 0 && (dados.receitaMesAnterior ?? 0) > 0
      ? resumoComparacao(
          dados.margem,
          dados.margemMesAnterior,
          contagens?.receitasAtual,
          contagens?.receitasAnterior,
          true,
        )
      : "Sem base de receita comparável",
  ];
  const percentualValido =
    dados.receita > 0 &&
    Number.isFinite(dados.margem) &&
    analise.registros?.receitasAtual !== 0;
  return (
    <div id="dashboard" className="dashboard executive-dashboard">
      <p className="df-applied" aria-live="polite">
        Aplicado: {dataBR(analise.inicio)} até {dataBR(analise.fim)} ·{" "}
        {analise.categoria ?? "Todas as categorias"}. Comparação:{" "}
        {dataBR(analise.inicioAnterior)} até {dataBR(analise.fimAnterior)}.
      </p>
      <details className="ev-coverage">
        <summary>Cobertura e registros do período · Ver detalhes</summary>
        <p className="df-note">
          Receitas:{" "}
          {descreverRegistros(
            analise.registros?.receitasAtual,
            analise.registros?.receitasAnterior,
          )}{" "}
          Despesas:{" "}
          {descreverRegistros(
            analise.registros?.despesasAtual,
            analise.registros?.despesasAnterior,
          )}{" "}
          A existência de registros não confirma importação completa.
        </p>
        <p>
          Os gráficos seguem a fonte, o critério, a situação, a categoria, o
          centro e o período aplicados. Valores sem classificação continuam nos
          totais. As contas em aberto usam o intervalo de vencimento selecionado
          e o saldo aberto hoje; os saldos bancários têm referência atual
          própria.
        </p>
      </details>
      {erro && (
        <p role="alert" className="df-error">
          {erro}
        </p>
      )}
      {carregando && <p role="status">Atualizando os dados do recorte…</p>}

      <section className="executive-kpi-grid ev-kpi-compact ev-kpi-four">
        <article className="executive-kpi">
          <span className="ev-kpi-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <rect x="3" y="13" width="4" height="8" rx="1" />
              <rect x="10" y="8" width="4" height="13" rx="1" />
              <rect x="17" y="3" width="4" height="18" rx="1" />
            </svg>
          </span>
          <div className="executive-kpi-top">
            <span>{nomes.receita}</span>
            <FinancialHelp termo="receita" />
          </div>

          <strong>{formatarMoeda(dados.receita)}</strong>
          <p>
            {analise.registros?.receitasAtual === 0
              ? "Sem registros no recorte"
              : caixa
                ? "Entradas financeiras do recorte"
                : "Receitas do recorte"}
          </p>

          <div
            className={`ev-kpi-static ${resumosComparacao[0].startsWith("↑") ? "ev-positive" : resumosComparacao[0].startsWith("↓") ? "ev-negative" : "ev-neutral"}`}
          >
            <span>{resumosComparacao[0]}</span>
          </div>
          <button
            type="button"
            className="financial-detail-trigger"
            aria-haspopup="dialog"
            aria-label="Ver lançamentos de receita"
            onClick={() => setDetalhamento("RECEITA")}
          >
            ↗
          </button>
        </article>

        <article className="executive-kpi">
          <span className="ev-kpi-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <rect
                x="2"
                y="5"
                width="20"
                height="15"
                rx="3"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
              />
              <path d="M3 10h18M6 16h6" stroke="currentColor" strokeWidth="2" />
            </svg>
          </span>
          <div className="executive-kpi-top">
            <span>{nomes.despesa}</span>
            <FinancialHelp termo="despesa" />
          </div>

          <strong>{formatarMoeda(dados.despesa)}</strong>
          <p>
            {analise.registros?.despesasAtual === 0
              ? "Sem registros no recorte"
              : caixa
                ? "Saídas financeiras do recorte"
                : "Despesas do recorte"}
          </p>

          <div
            className={`ev-kpi-static ${resumosComparacao[1].startsWith("↓") ? "ev-positive" : resumosComparacao[1].startsWith("↑") ? "ev-negative" : "ev-neutral"}`}
          >
            <span>{resumosComparacao[1]}</span>
          </div>
          <button
            type="button"
            className="financial-detail-trigger"
            aria-haspopup="dialog"
            aria-label="Ver lançamentos de despesa"
            onClick={() => setDetalhamento("DESPESA")}
          >
            ↗
          </button>
        </article>

        <article className="executive-kpi ev-result-kpi">
          <span className="ev-kpi-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <path
                d="m3 17 6-6 4 4 8-11M15 4h6v6"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </span>
          <div className="executive-kpi-top">
            <span>{nomes.resultado}</span>
            <FinancialHelp termo="resultado" />
          </div>

          <strong
            className={dados.resultado < 0 ? "ev-negative" : "ev-positive"}
          >
            {formatarMoeda(dados.resultado)}
          </strong>
          <p>
            {caixa
              ? "Recebimentos menos pagamentos"
              : "Receitas menos despesas"}
          </p>

          <div
            className={`ev-kpi-static ${resumosComparacao[2].startsWith("↑") ? "ev-positive" : resumosComparacao[2].startsWith("↓") ? "ev-negative" : "ev-neutral"}`}
          >
            <span>{resumosComparacao[2]}</span>
          </div>
        </article>

        <article className="executive-kpi ev-margin-kpi">
          <span className="ev-kpi-icon" aria-hidden="true">
            <svg
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
            >
              <path d="M5 20 19 4" />
              <circle cx="6" cy="6" r="3" />
              <circle cx="18" cy="18" r="3" />
            </svg>
          </span>
          <div className="executive-kpi-top">
            <span>Margem (%)</span>
            <FinancialHelp termo="margem" />
          </div>
          <strong
            className={
              percentualValido
                ? dados.margem < 0
                  ? "ev-negative"
                  : "ev-positive"
                : "ev-rate-neutral"
            }
          >
            {percentualValido ? `${formatarPercentual(dados.margem)}%` : "—"}
          </strong>
          <p>
            {percentualValido ? nomes.margem : "Sem base positiva de receita"}
          </p>
          <div
            className={`ev-kpi-static ${percentualValido && resumosComparacao[3].startsWith("↑") ? "ev-positive" : percentualValido && resumosComparacao[3].startsWith("↓") ? "ev-negative" : "ev-neutral"}`}
          >
            <span>{resumosComparacao[3]}</span>
          </div>
        </article>
      </section>

      <section className="ev-main-grid">
        <DashboardMonthlyChart
          analise={analise}
          caixa={caixa}
          onSeisMeses={() => void aplicar(undefined, true)}
          carregando={carregando}
        />
        <ExecutiveExpenseOverview
          analise={analise}
          caixa={caixa}
          onCategoria={(nome) => void aplicar(undefined, false, nome)}
        />
      </section>
      <ExecutiveComposition
        analise={analise}
        caixa={caixa}
        onCategoria={(nome) => void aplicar(undefined, false, nome)}
      />
      <section className="ev-bottom-grid">
        <SpendingGoalGauge
          compacto
          meta={metaSelecionada}
          metas={metasCompativeis}
          metaSelecionadaId={metaSelecionada?.id ?? null}
          onSelecionarMeta={setMetaSelecionadaId}
        />
        <ExecutiveHighlights
          analise={analise}
          caixa={caixa}
          onDetalhar={setDetalhamento}
          onCategoria={(nome) => void aplicar(undefined, false, nome)}
        />
        <FinancialPositionPanel
          inicio={viewFilter ? positionRange?.inicio : analise.inicio}
          fim={viewFilter ? positionRange?.fim : analise.fim}
          key={analise.clienteId}
          clienteId={analise.clienteId}
        />
      </section>

      {metaSelecionada && (
        <p className="df-note">
          Meta selecionada: {dataBR(metaSelecionada.dataInicio)} até{" "}
          {dataBR(metaSelecionada.dataFim)} ·{" "}
          {metaSelecionada.categoria ?? "Todas as categorias"} ·{" "}
          {metaSelecionada.centroCusto ?? "Todos os centros"}.
          {metaSelecionada.dataFim < analise.hoje
            ? " Meta encerrada: consumo final."
            : metaSelecionada.dataInicio > analise.hoje
              ? " Meta ainda não iniciada."
              : " Meta em andamento."}
        </p>
      )}
      <details className="ev-secondary ev-variation">
        <summary>Variações entre períodos · Ver análise completa</summary>
        <FinancialWaterfall
          analise={analise}
          onCategoria={(nome) => void aplicar(undefined, false, nome)}
        />
      </details>

      <details className="ev-secondary">
        <summary>Alertas e pendências · Ver regras e resultados</summary>
        <FinancialAlertsPanel
          key={`${analise.clienteId}:${analise.inicio}:${analise.fim}:${analise.categoria ?? ""}`}
          clienteId={analise.clienteId}
          inicio={analise.inicio}
          fim={analise.fim}
          categoria={analise.categoria}
        />
      </details>

      {detalhamento !== null && (
        <DashboardFilteredDetail
          analise={analise}
          tipo={detalhamento}
          fechar={() => setDetalhamento(null)}
        />
      )}
    </div>
  );
}

const compact = (n: number) =>
  new Intl.NumberFormat("pt-BR", {
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(n);
type ExecutiveProps = {
  analise: DashboardAnalysis;
  caixa: boolean;
  onCategoria: (nome: string) => void;
};

function ExecutiveExpenseOverview({
  analise,
  caixa,
  onCategoria,
}: ExecutiveProps) {
  const [modo, setModo] = useState<"GASTOS" | "COMPARAR" | "PARETO">("GASTOS");
  const [todas, setTodas] = useState(false);
  const d = prepararComposicaoExecutiva(
    analise.variacoes,
    analise.indicadores.receita,
    analise.indicadores.despesa,
    analise.indicadores.despesaMesAnterior ?? 0,
    todas ? 100000 : 5,
  );
  const comparavel = Boolean(
    analise.registros &&
    analise.registros.despesasAtual > 0 &&
    analise.registros.despesasAnterior > 0 &&
    d.fecha &&
    d.fechaAnterior,
  );
  const dados =
    modo === "PARETO" ? d.pareto.slice(0, todas ? undefined : 5) : d.grupos;
  const maior = Math.max(
    1,
    ...dados.flatMap((r) => [
      Math.abs(r.atual),
      modo === "COMPARAR" ? Math.abs(r.anterior) : 0,
    ]),
  );
  const resumo = (nome: string) => nome.replace(/^\s*\d+(?:\.\d+)*\s+/, "");
  const escolher = (nome: string) =>
    onCategoria(analise.categoria === nome ? "" : nome);
  const exportar = () =>
    exportarCSV("painel-categorias.csv", [
      [
        "Categoria",
        "Início atual",
        "Fim atual",
        "Início anterior",
        "Fim anterior",
        "Atual BRL",
        "Anterior disponível BRL",
        "Diferença disponível BRL",
        "Base comparável",
        "Participação %",
        "Acumulado %",
      ],
      ...d.pareto.map((r) => [
        r.categoria,
        analise.inicio,
        analise.fim,
        analise.inicioAnterior,
        analise.fimAnterior,
        r.atual,
        r.anterior,
        r.diferenca,
        comparavel ? "Registros disponíveis; cobertura não certificada" : "Não",
        r.percentual ?? "Não calculável",
        r.acumulado ?? "Não calculável",
      ]),
    ]);
  return (
    <article className="executive-panel ev-expenses">
      <header className="ev-heading">
        <div>
          <h2>
            {modo === "COMPARAR"
              ? "Categorias: atual × anterior"
              : modo === "PARETO"
                ? "Pareto de despesas"
                : "Onde estão os gastos?"}
          </h2>
        </div>
        <details className="ev-tools">
          <summary aria-label="Opções dos gastos por categoria">⋯</summary>
          <div>
            <button type="button" onClick={() => setModo("GASTOS")}>
              Barras horizontais
            </button>
            <button type="button" onClick={() => setModo("COMPARAR")}>
              Comparar períodos
            </button>
            <button type="button" onClick={() => setModo("PARETO")}>
              Pareto acumulado
            </button>
            <button type="button" onClick={exportar}>
              Exportar tabela completa
            </button>
          </div>
        </details>
      </header>
      <p className="ev-caption">
        {caixa ? "Pagamentos" : "Despesas"} do recorte ·{" "}
        {todas
          ? "Todas as categorias"
          : modo === "PARETO"
            ? "Cinco maiores; acumulado sobre o total"
            : "Maiores categorias e demais agrupadas"}
        .
      </p>
      {!d.fecha && (
        <p className="ev-notice">
          A distribuição difere do indicador em {brl(d.diferenca)}. Confira a
          tabela; os totais não foram alterados.
        </p>
      )}
      {modo === "COMPARAR" && !comparavel && (
        <p className="ev-notice">
          Sem base comparável: faltam registros ou distribuição conferida em um
          dos períodos. Os valores disponíveis continuam na tabela.
        </p>
      )}
      {modo === "PARETO" &&
        (d.negativos || !d.fecha || analise.indicadores.despesa <= 0) && (
          <p className="ev-notice">
            Pareto percentual indisponível neste recorte. Consulte os ajustes e
            valores na tabela.
          </p>
        )}
      {analise.registros?.despesasAtual === 0 && (
        <p className="ev-empty">
          Sem registros de despesas neste recorte. Os dados anteriores
          permanecem disponíveis na tabela.
        </p>
      )}
      {modo !== "PARETO" &&
        d.finito &&
        d.fecha &&
        analise.registros?.despesasAtual !== 0 &&
        (modo !== "COMPARAR" || comparavel) && (
          <div className="ev-horizontal-list">
            {d.grupos.map((r) => (
              <div className="ev-horizontal-row" key={r.categoria}>
                <div className="ev-horizontal-label">
                  {!r.agrupado && r.categoria.trim() ? (
                    <button
                      className="ev-category-link"
                      type="button"
                      title={r.categoria}
                      onClick={() => escolher(r.categoria)}
                    >
                      {resumo(r.categoria)}
                    </button>
                  ) : (
                    <span>{r.categoria || "Sem categoria"}</span>
                  )}
                  <strong>{brl(r.atual)}</strong>
                </div>
                {modo === "COMPARAR" && (
                  <div
                    className="ev-bar-track ev-prior"
                    title={`Anterior: ${brl(r.anterior)}`}
                  >
                    <i
                      style={{
                        width: `${(Math.abs(r.anterior) / maior) * 100}%`,
                      }}
                    />
                  </div>
                )}
                <div
                  className={`ev-bar-track ${r.atual < 0 ? "ev-adjustment" : ""}`}
                  title={`Atual: ${brl(r.atual)}`}
                >
                  <i
                    style={{ width: `${(Math.abs(r.atual) / maior) * 100}%` }}
                  />
                </div>
                {modo === "COMPARAR" && (
                  <small>
                    Anterior {brl(r.anterior)} · diferença {brl(r.diferenca)}
                  </small>
                )}
              </div>
            ))}
            {d.grupos.length === 0 && (
              <p className="ev-empty">
                Sem registros de despesas neste recorte.
              </p>
            )}
          </div>
        )}
      {modo === "PARETO" &&
        d.finito &&
        d.fecha &&
        !d.negativos &&
        analise.indicadores.despesa > 0 && (
          <div
            className="ev-chart"
            tabIndex={0}
            aria-label="Pareto com rolagem horizontal"
          >
            <ResponsiveContainer
              width="100%"
              height="100%"
              minWidth={Math.max(480, dados.length * 85)}
            >
              <ComposedChart
                data={d.pareto.slice(0, todas ? undefined : 5)}
                margin={{ top: 12, left: 0, right: 12, bottom: 10 }}
                accessibilityLayer
              >
                <CartesianGrid stroke="var(--ev-line)" vertical={false} />
                <XAxis
                  dataKey="categoria"
                  tickFormatter={(n) => resumo(String(n)).slice(0, 15)}
                  tick={{ fontSize: 10, fill: "var(--ev-muted)" }}
                />
                <YAxis
                  yAxisId="valor"
                  width={50}
                  tickFormatter={compact}
                  tick={{ fontSize: 10, fill: "var(--ev-muted)" }}
                />
                <YAxis
                  yAxisId="percentual"
                  orientation="right"
                  domain={[0, 100]}
                  width={32}
                  tickFormatter={(n) => `${n}%`}
                />
                <Tooltip
                  contentStyle={{
                    background: "var(--ev-surface)",
                    border: "1px solid var(--ev-line)",
                    color: "var(--ev-text)",
                  }}
                  formatter={(v, n) =>
                    n === "Acumulado"
                      ? `${Number(v).toLocaleString("pt-BR", { maximumFractionDigits: 1 })}%`
                      : brl(Number(v))
                  }
                />
                <Bar
                  yAxisId="valor"
                  dataKey="atual"
                  name="Atual"
                  fill="#f28c28"
                  radius={[4, 4, 0, 0]}
                  isAnimationActive={false}
                />
                <Line
                  yAxisId="percentual"
                  dataKey="acumulado"
                  name="Acumulado"
                  stroke="#409afa"
                  strokeWidth={2}
                  isAnimationActive={false}
                />
                <Legend wrapperStyle={{ fontSize: 11 }} />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
        )}
      {modo === "COMPARAR" && (
        <p className="ev-caption">
          Anterior: {dataBR(analise.inicioAnterior)} a{" "}
          {dataBR(analise.fimAnterior)}. Azul: anterior · laranja: atual.
        </p>
      )}
      <footer className="ev-category-footer">
        <button
          type="button"
          className="ev-link"
          onClick={() => setTodas((v) => !v)}
        >
          {todas ? "Resumir gráfico" : "Explorar todas as categorias →"}
        </button>
        <span>{d.categorias.length} categorias</span>
      </footer>
      <details className="ev-all">
        <summary>Tabela completa, valores e filtro por categoria</summary>
        <div className="ev-table">
          <table>
            <thead>
              <tr>
                <th>Categoria</th>
                <th>Atual</th>
                <th>Anterior disponível</th>
                <th>Diferença disponível</th>
                <th>Participação</th>
                <th>Acumulado</th>
              </tr>
            </thead>
            <tbody>
              {d.pareto.map((r) => (
                <tr key={r.categoria}>
                  <th>
                    {r.categoria.trim() ? (
                      <button
                        type="button"
                        className="ev-link"
                        onClick={() => escolher(r.categoria)}
                      >
                        {r.categoria}
                      </button>
                    ) : (
                      "Sem categoria"
                    )}
                  </th>
                  <td>{brl(r.atual)}</td>
                  <td>{brl(r.anterior)}</td>
                  <td>
                    {brl(r.diferenca)}
                    {!comparavel && <small> · sem conclusão comparativa</small>}
                  </td>
                  <td>
                    {r.percentual === null
                      ? "—"
                      : `${r.percentual.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}%`}
                  </td>
                  <td>
                    {r.acumulado === null
                      ? "—"
                      : `${r.acumulado.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}%`}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </article>
  );
}

function ExecutiveComposition({ analise, caixa, onCategoria }: ExecutiveProps) {
  if (
    analise.registros?.receitasAtual === 0 &&
    analise.registros?.despesasAtual === 0
  )
    return (
      <article className="executive-panel ev-composition">
        <header className="ev-heading">
          <h2>Como chegamos ao resultado</h2>
        </header>
        <p className="ev-empty">
          Sem registros neste recorte. Não há composição financeira disponível
          para apresentar.
        </p>
      </article>
    );
  const d = prepararComposicaoExecutiva(
    analise.variacoes,
    analise.indicadores.receita,
    analise.indicadores.despesa,
    analise.indicadores.despesaMesAnterior ?? 0,
  );
  const bars = d.barras.map((b, i) => ({
    ...b,
    nome: i === 0 ? (caixa ? "Recebimentos" : "Receitas") : b.nome,
  }));
  const min = Math.min(0, ...bars.flatMap((b) => [b.inicio, b.fim])),
    max = Math.max(0, ...bars.flatMap((b) => [b.inicio, b.fim]));
  const span = max - min || 1,
    width = Math.max(780, bars.length * 120),
    left = 70,
    top = 35,
    height = 220,
    step = (width - left - 24) / bars.length;
  const y = (value: number) => top + ((max - value) / span) * height;
  return (
    <article className="executive-panel ev-composition">
      <header className="ev-heading">
        <div>
          <span className="executive-eyebrow">COMPOSIÇÃO DO RESULTADO</span>
          <h2>Como chegamos ao resultado</h2>
        </div>
        <strong
          className={
            analise.indicadores.resultado < 0 ? "ev-negative" : "ev-positive"
          }
        >
          {brl(analise.indicadores.resultado)}
        </strong>
      </header>
      <p className="ev-caption">
        {caixa ? "Recebimentos menos pagamentos" : "Receitas menos despesas"} do
        mesmo recorte. Categorias representam classificações da origem, não
        custos fixos ou variáveis.
      </p>
      {!d.fecha && (
        <p className="ev-notice">
          Distribuição por categoria não conferida: a cascata usa a despesa
          total, sem estimar rateios.
        </p>
      )}
      {d.finito && (
        <div
          className="ev-waterfall-scroll"
          tabIndex={0}
          aria-label="Composição do resultado; role horizontalmente para ver todas as barras"
        >
          <svg
            viewBox={`0 0 ${width} 335`}
            style={{ minWidth: width }}
            role="img"
            aria-label="Receitas menos despesas, chegando ao resultado. Os valores exatos estão na tabela abaixo."
          >
            {[0, 1, 2, 3, 4].map((i) => {
              const v = min + (span * i) / 4;
              return (
                <g key={i}>
                  <line
                    x1={left}
                    x2={width - 10}
                    y1={y(v)}
                    y2={y(v)}
                    stroke="var(--ev-line)"
                  />
                  <text
                    x={left - 10}
                    y={y(v) + 4}
                    textAnchor="end"
                    fill="var(--ev-muted)"
                    fontSize="11"
                  >
                    {compact(v)}
                  </text>
                </g>
              );
            })}
            {bars.map((b, i) => {
              const x = left + i * step + step * 0.18,
                w = step * 0.64,
                yy = y(Math.max(b.inicio, b.fim)),
                hh = Math.abs(y(b.inicio) - y(b.fim));
              return (
                <g key={i}>
                  {i < bars.length - 1 && (
                    <line
                      x1={x + w}
                      x2={x + step}
                      y1={y(b.fim)}
                      y2={y(b.fim)}
                      stroke="var(--ev-muted)"
                      strokeDasharray="3 4"
                    />
                  )}
                  <rect
                    x={x}
                    y={yy}
                    width={w}
                    height={Math.max(hh, 1)}
                    rx="3"
                    fill={
                      i === 0
                        ? "#3698f4"
                        : b.total
                          ? b.valor < 0
                            ? "#f16b7c"
                            : "#26c6a4"
                          : b.valor > 0
                            ? "#26c6a4"
                            : "#f28c28"
                    }
                  >
                    <title>{`${b.nome}: ${brl(b.valor)}`}</title>
                  </rect>
                  <text
                    x={x + w / 2}
                    y={Math.max(16, yy - 8)}
                    textAnchor="middle"
                    fill="var(--ev-text)"
                    fontSize="12"
                    fontWeight="600"
                  >
                    {compact(b.valor)}
                  </text>
                  <text
                    x={x + w / 2}
                    y={282}
                    textAnchor="middle"
                    fill="var(--ev-muted)"
                    fontSize="11"
                  >
                    <title>{b.nome}</title>
                    {b.nome.length > 17 ? b.nome.slice(0, 16) + "…" : b.nome}
                  </text>
                </g>
              );
            })}
          </svg>
        </div>
      )}
      <details className="ev-all">
        <summary>Ver composição em tabela e lançamentos por categoria</summary>
        <div className="ev-table">
          <table>
            <thead>
              <tr>
                <th>Etapa</th>
                <th>Impacto</th>
                <th>Saldo após etapa</th>
              </tr>
            </thead>
            <tbody>
              {bars.map((b, i) => (
                <tr key={i}>
                  <th>
                    {b.categoria ? (
                      <button
                        type="button"
                        className="ev-link"
                        onClick={() =>
                          onCategoria(
                            analise.categoria === b.categoria
                              ? ""
                              : b.categoria,
                          )
                        }
                      >
                        {b.nome}
                      </button>
                    ) : (
                      b.nome
                    )}
                  </th>
                  <td>{brl(b.valor)}</td>
                  <td>{brl(b.fim)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </article>
  );
}

function ExecutiveHighlights({
  analise,
  caixa,
  onDetalhar,
  onCategoria,
}: Omit<ExecutiveProps, "onCategoria"> & {
  onDetalhar: (tipo: "RECEITA" | "DESPESA") => void;
  onCategoria: (nome: string) => void;
}) {
  if (
    analise.registros?.receitasAtual === 0 &&
    analise.registros?.despesasAtual === 0
  )
    return (
      <article className="executive-panel ev-highlights">
        <h2>Destaques do período</h2>
        <p className="ev-empty">
          Sem registros atuais para concluir resultado ou variação.
        </p>
        <button type="button" onClick={() => onDetalhar("RECEITA")}>
          Consultar receitas do recorte ↗
        </button>
        <button type="button" onClick={() => onDetalhar("DESPESA")}>
          Consultar despesas do recorte ↗
        </button>
      </article>
    );
  const d = prepararComposicaoExecutiva(
    analise.variacoes,
    analise.indicadores.receita,
    analise.indicadores.despesa,
    analise.indicadores.despesaMesAnterior ?? 0,
  );
  const maior = d.fecha && !d.negativos ? d.maior : null;
  const mudanca =
    analise.registros &&
    analise.registros.despesasAtual > 0 &&
    analise.registros.despesasAnterior > 0 &&
    d.fecha &&
    d.fechaAnterior
      ? [...d.categorias].sort(
          (a, b) => Math.abs(b.diferenca) - Math.abs(a.diferenca),
        )[0]
      : null;
  return (
    <article className="executive-panel ev-highlights">
      <span className="executive-eyebrow">LEITURA DO RECORTE</span>
      <h2>Destaques do período</h2>
      {maior ? (
        <button
          type="button"
          onClick={() =>
            maior.categoria.trim()
              ? onCategoria(
                  analise.categoria === maior.categoria ? "" : maior.categoria,
                )
              : onDetalhar("DESPESA")
          }
        >
          <span>
            {maior.categoria.trim() || "Sem categoria"} representa{" "}
            <b>
              {(
                (maior.atual / analise.indicadores.despesa) *
                100
              ).toLocaleString("pt-BR", { maximumFractionDigits: 1 })}
              %
            </b>{" "}
            {caixa ? "dos pagamentos" : "das despesas"}.
          </span>
          <span aria-hidden="true">↗</span>
        </button>
      ) : (
        <button type="button" onClick={() => onDetalhar("DESPESA")}>
          <span>
            Confira a distribuição e os registros de despesas do recorte.
          </span>
          <span aria-hidden="true">↗</span>
        </button>
      )}
      <button type="button" onClick={() => onDetalhar("RECEITA")}>
        <span>
          Resultado {caixa ? "dos movimentos" : "do período"}:{" "}
          <b>{brl(analise.indicadores.resultado)}</b>. Consulte a base de
          receitas.
        </span>
        <span aria-hidden="true">↗</span>
      </button>
      {mudanca && mudanca.diferenca !== 0 ? (
        <button
          type="button"
          onClick={() =>
            mudanca.categoria.trim()
              ? onCategoria(
                  analise.categoria === mudanca.categoria
                    ? ""
                    : mudanca.categoria,
                )
              : onDetalhar("DESPESA")
          }
        >
          <span>
            Maior variação em valor absoluto:{" "}
            {mudanca.categoria.trim() || "Sem categoria"},{" "}
            <b>{brl(mudanca.diferenca)}</b>.
          </span>
          <span aria-hidden="true">↗</span>
        </button>
      ) : (
        <button type="button" onClick={() => onDetalhar("DESPESA")}>
          <span>
            {analise.registros?.despesasAnterior === 0
              ? "Sem registros anteriores para concluir aumento ou redução."
              : "Consulte os lançamentos para entender a composição dos gastos."}
          </span>
          <span aria-hidden="true">↗</span>
        </button>
      )}
      <p className="ev-caption">
        Variações descrevem os registros disponíveis; não comprovam economia ou
        cobertura completa. Simulações ficam na página Projeção.
      </p>
    </article>
  );
}

function resumoComparacao(
  atual: number,
  anterior: number | null | undefined,
  registrosAtual: number | undefined,
  registrosAnterior: number | undefined,
  pontos = false,
) {
  if (registrosAtual === 0 || registrosAnterior === 0)
    return "Sem registros para comparar";
  if (
    registrosAtual == null ||
    registrosAnterior == null ||
    anterior == null ||
    !Number.isFinite(atual) ||
    !Number.isFinite(anterior)
  )
    return "Comparação não confirmada";
  const delta = Math.round((atual - anterior) * 100) / 100;
  if (delta === 0) return "Sem variação";
  const direcao = delta > 0 ? "↑" : "↓";
  return pontos
    ? `${direcao} ${Math.abs(delta).toLocaleString("pt-BR", { maximumFractionDigits: 2 })} p.p. · anterior`
    : anterior > 0
      ? `${direcao} ${Math.abs((delta / anterior) * 100).toLocaleString("pt-BR", { maximumFractionDigits: 1 })}% · anterior`
      : `${direcao} ${brl(Math.abs(delta))} · anterior`;
}

export default Dashboard;
