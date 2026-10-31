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
import KpiComparison from "../components/KpiComparison";

import SpendingGoalGauge from "../components/SpendingGoalGauge";

import "../styles/dashboard.css";
import "../styles/dashboard-polish.css";

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
        centro: "",
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

  return (
    <div id="dashboard" className="dashboard executive-dashboard">
      <button
        type="button"
        disabled={carregando}
        onClick={() => void aplicar(undefined, true)}
      >
        Ver seis meses
      </button>
      <p className="df-applied" aria-live="polite">
        Aplicado: {dataBR(analise.inicio)} até {dataBR(analise.fim)} ·{" "}
        {analise.categoria ?? "Todas as categorias"}. Comparação:{" "}
        {dataBR(analise.inicioAnterior)} até {dataBR(analise.fimAnterior)}.
      </p>
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
      {erro && (
        <p role="alert" className="df-error">
          {erro}
        </p>
      )}
      {carregando && <p role="status"></p>}

      <section className="executive-kpi-grid">
        <article className="executive-kpi">
          <div className="executive-kpi-top">
            <span>Receita</span>
            <FinancialHelp termo="receita" />
          </div>

          <strong>{formatarMoeda(dados.receita)}</strong>
          <p>Receita total do período</p>

          <KpiComparison
            rotulo="período anterior"
            registrosAtual={analise.registros?.receitasAtual}
            registrosAnterior={analise.registros?.receitasAnterior}
            atual={dados.receita}
            anterior={dados.receitaMesAnterior}
            variacao={dados.variacaoReceita}
          />
          <button
            type="button"
            className="financial-detail-trigger"
            aria-haspopup="dialog"
            aria-label="Ver lançamentos de receita"
            onClick={() => setDetalhamento("RECEITA")}
          >
            Ver lançamentos
          </button>
        </article>

        <article className="executive-kpi">
          <div className="executive-kpi-top">
            <span>Despesa</span>
            <FinancialHelp termo="despesa" />
          </div>

          <strong>{formatarMoeda(dados.despesa)}</strong>
          <p>Despesas totais do período</p>

          <KpiComparison
            rotulo="período anterior"
            registrosAtual={analise.registros?.despesasAtual}
            registrosAnterior={analise.registros?.despesasAnterior}
            atual={dados.despesa}
            anterior={dados.despesaMesAnterior}
            variacao={dados.variacaoDespesa}
            despesa
          />
          <button
            type="button"
            className="financial-detail-trigger"
            aria-haspopup="dialog"
            aria-label="Ver lançamentos de despesa"
            onClick={() => setDetalhamento("DESPESA")}
          >
            Ver lançamentos
          </button>
        </article>

        <article className="executive-kpi">
          <div className="executive-kpi-top">
            <span>Resultado</span>
            <FinancialHelp termo="resultado" />
          </div>

          <strong>{formatarMoeda(dados.resultado)}</strong>
          <p>Receita menos despesas</p>

          <KpiComparison
            rotulo="período anterior"
            registrosAtual={
              analise.registros
                ? analise.registros.receitasAtual +
                  analise.registros.despesasAtual
                : undefined
            }
            registrosAnterior={
              analise.registros
                ? analise.registros.receitasAnterior +
                  analise.registros.despesasAnterior
                : undefined
            }
            atual={dados.resultado}
            anterior={dados.resultadoMesAnterior}
            variacao={dados.variacaoResultado}
          />
        </article>

        <article className="executive-kpi">
          <div className="executive-kpi-top">
            <span>Margem</span>
            <FinancialHelp termo="margem" />
          </div>

          <strong>
            {dados.receita === 0
              ? "Não calculável"
              : `${formatarPercentual(dados.margem)}%`}
          </strong>
          <p>Margem do resultado sobre a receita</p>

          <KpiComparison
            rotulo="período anterior"
            registrosAtual={analise.registros?.receitasAtual}
            registrosAnterior={analise.registros?.receitasAnterior}
            atual={dados.margem}
            anterior={dados.margemMesAnterior}
            margem
            margemComparavel={
              dados.receita > 0 && (dados.receitaMesAnterior ?? 0) > 0
            }
          />
        </article>
      </section>

      <aside className="fp-coverage-warning" role="note">
        <strong>Cobertura dos dados:</strong> os gráficos seguem a fonte, o
        critério e o período selecionados. Concluir uma varredura não certifica
        a cobertura integral. Revise pendências e a conferência individual em
        Importar dados. As contas em aberto abaixo usam o intervalo de
        vencimento selecionado e o saldo ainda aberto hoje. Os saldos bancários
        têm posição atual própria.
      </aside>
      <FinancialPositionPanel
        inicio={viewFilter ? positionRange?.inicio : analise.inicio}
        fim={viewFilter ? positionRange?.fim : analise.fim}
        key={analise.clienteId}
        clienteId={analise.clienteId}
      />

      <FinancialAlertsPanel
        key={`${analise.clienteId}:${analise.inicio}:${analise.fim}:${analise.categoria ?? ""}`}
        clienteId={analise.clienteId}
        inicio={analise.inicio}
        fim={analise.fim}
        categoria={analise.categoria}
      />

      <section className="executive-overview-grid">
        <DashboardMonthlyChart analise={analise} />

        <SpendingGoalGauge
          meta={metaSelecionada}
          metas={metasCompativeis}
          metaSelecionadaId={metaSelecionada?.id ?? null}
          onSelecionarMeta={setMetaSelecionadaId}
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
      <FinancialWaterfall
        analise={analise}
        onCategoria={(nome) => void aplicar(undefined, false, nome)}
      />

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

export default Dashboard;
