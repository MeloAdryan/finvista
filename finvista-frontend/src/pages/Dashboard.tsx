import { useEffect, useMemo, useState } from "react";

import { getDashboard, type DashboardData } from "../services/dashboardService";
import FinancialDetailModal from "../components/FinancialDetailModal";
import type { FinancialDetailKind } from "../services/financialDetailService";

import FinancialChanges from "../components/FinancialChanges";

import {
  listarMetas,
  type SpendingGoal,
} from "../services/spendingGoalService";

import RevenueExpenseChart from "../charts/RevenueExpenseChart";

import KpiComparison from "../components/KpiComparison";

import SpendingGoalGauge from "../components/SpendingGoalGauge";

import "../styles/dashboard.css";

function Dashboard() {
  const [dados, setDados] = useState<DashboardData | null>(null);

  const [detalhamento, setDetalhamento] = useState<FinancialDetailKind | null>(
    null,
  );

  const [metas, setMetas] = useState<SpendingGoal[]>([]);

  const [metaSelecionadaId, setMetaSelecionadaId] = useState<number | null>(
    null,
  );

  const [carregando, setCarregando] = useState(true);

  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    async function carregarDashboard() {
      try {
        const [resultadoDashboard, resultadoMetas] = await Promise.all([
          getDashboard(),
          listarMetas(),
        ]);

        setDados(resultadoDashboard);

        setMetas(resultadoMetas);

        if (resultadoMetas.length > 0) {
          const metasOrdenadas = [...resultadoMetas].sort((a, b) => {
            const prioridade = {
              EXCEDIDA: 3,
              ALERTA: 2,
              NORMAL: 1,
            };

            const diferencaPrioridade =
              prioridade[b.status] - prioridade[a.status];

            if (diferencaPrioridade !== 0) {
              return diferencaPrioridade;
            }

            return b.percentualUtilizado - a.percentualUtilizado;
          });

          setMetaSelecionadaId(metasOrdenadas[0].id);
        }
      } catch (error) {
        console.error("Erro ao carregar dashboard:", error);

        if (error instanceof Error) {
          setErro(error.message);
        } else {
          setErro("Não foi possível carregar os dados financeiros.");
        }
      } finally {
        setCarregando(false);
      }
    }

    carregarDashboard();
  }, []);

  const metaSelecionada = useMemo(() => {
    if (metaSelecionadaId === null || metas.length === 0) {
      return null;
    }

    return metas.find((meta) => meta.id === metaSelecionadaId) ?? null;
  }, [metas, metaSelecionadaId]);

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

  if (carregando) {
    return (
      <div className="dashboard-state">
        <div className="dashboard-loader" />

        <p>Carregando painel executivo...</p>
      </div>
    );
  }

  if (erro) {
    return (
      <div className="dashboard-state dashboard-state-error">
        <strong>Não foi possível carregar o painel.</strong>

        <p>{erro}</p>
      </div>
    );
  }

  if (!dados) {
    return (
      <div className="dashboard-state">
        <p>Nenhum dado disponível.</p>
      </div>
    );
  }

  return (
    <div id="dashboard" className="dashboard executive-dashboard">
      <header className="executive-header">
        <div>
          <span className="executive-page-label">Painel Executivo</span>

          <h1>Visão financeira</h1>

          <p>
            Acompanhe os principais indicadores e limites financeiros em uma
            visão consolidada.
          </p>
        </div>

        <div className="executive-header-badge">
          <span>FinVista</span>
          <strong>Visão Executiva</strong>
        </div>
      </header>

      <section className="executive-kpi-grid">
        <article className="executive-kpi">
          <div className="executive-kpi-top">
            <span>Receita</span>
            <span className="kpi-marker" />
          </div>

          <strong>{formatarMoeda(dados.receita)}</strong>
          <p>Receita total do período</p>

          <KpiComparison
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
            <span className="kpi-marker" />
          </div>

          <strong>{formatarMoeda(dados.despesa)}</strong>
          <p>Despesas totais do período</p>

          <KpiComparison
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
            <span className="kpi-marker" />
          </div>

          <strong>{formatarMoeda(dados.resultado)}</strong>
          <p>Receita menos despesas</p>

          <KpiComparison
            atual={dados.resultado}
            anterior={dados.resultadoMesAnterior}
            variacao={dados.variacaoResultado}
          />
        </article>

        <article className="executive-kpi">
          <div className="executive-kpi-top">
            <span>Margem</span>
            <span className="kpi-marker" />
          </div>

          <strong>{formatarPercentual(dados.margem)}%</strong>
          <p>Margem do resultado sobre a receita</p>

          <KpiComparison
            atual={dados.margem}
            anterior={dados.margemMesAnterior}
            margem
            margemComparavel={
              dados.receita > 0 && (dados.receitaMesAnterior ?? 0) > 0
            }
          />
        </article>
      </section>

      <section className="executive-overview-grid">
        <RevenueExpenseChart receita={dados.receita} despesa={dados.despesa} />

        <SpendingGoalGauge
          meta={metaSelecionada}
          metas={metas}
          metaSelecionadaId={metaSelecionadaId}
          onSelecionarMeta={setMetaSelecionadaId}
        />
      </section>

      <FinancialChanges />

      {detalhamento !== null && (
        <FinancialDetailModal
          key={detalhamento}
          tipo={detalhamento}
          valorDoCartao={
            detalhamento === "RECEITA" ? dados.receita : dados.despesa
          }
          onFechar={() => setDetalhamento(null)}
        />
      )}
    </div>
  );
}

export default Dashboard;
