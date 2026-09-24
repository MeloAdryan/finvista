import { useEffect, useState } from "react";
import { getDashboard, type DashboardData } from "../services/dashboardService";
import RevenueExpenseChart from "../charts/RevenueExpenseChart";
import ResultMarginChart from "../charts/ResultMarginChart";

import "../styles/dashboard.css";

function Dashboard() {
  const [dados, setDados] = useState<DashboardData | null>(null);

  const [carregando, setCarregando] = useState(true);

  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    async function carregarDashboard() {
      try {
        const resultado = await getDashboard();

        setDados(resultado);
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

  const formatarMoeda = (valor: number) => {
    return valor.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });
  };

  const formatarPercentual = (valor: number) => {
    return valor.toLocaleString("pt-BR", {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  };

  if (carregando) {
    return <p>Carregando dados...</p>;
  }

  if (erro) {
    return <p>{erro}</p>;
  }

  if (!dados) {
    return <p>Nenhum dado disponível.</p>;
  }

  return (
    <div id="dashboard" className="dashboard">
      {/* CABEÇALHO */}

      <header className="dashboard-header">
        <div>
          <h1>FinVista</h1>

          <p>Visão geral financeira</p>
        </div>
      </header>

      {/* INDICADORES PRINCIPAIS */}

      <section className="cards-grid">
        <article className="card">
          <span className="card-label">Receita</span>

          <strong className="card-value receita">
            {formatarMoeda(dados.receita)}
          </strong>

          <span className="card-description">Receita total do período</span>
        </article>

        <article className="card">
          <span className="card-label">Despesa</span>

          <strong className="card-value despesa">
            {formatarMoeda(dados.despesa)}
          </strong>

          <span className="card-description">Despesas totais do período</span>
        </article>

        <article className="card">
          <span className="card-label">Resultado</span>

          <strong className="card-value resultado">
            {formatarMoeda(dados.resultado)}
          </strong>

          <span className="card-description">Receita menos despesas</span>
        </article>

        <article className="card">
          <span className="card-label">Margem</span>

          <strong className="card-value margem">
            {formatarPercentual(dados.margem)}%
          </strong>

          <span className="card-description">Margem operacional</span>
        </article>
      </section>

      {/* GRÁFICOS PRINCIPAIS */}

      <section className="charts-grid">
        <RevenueExpenseChart receita={dados.receita} despesa={dados.despesa} />

        <ResultMarginChart resultado={dados.resultado} margem={dados.margem} />
      </section>

      {/* PROJEÇÃO DE 6 MESES */}
    </div>
  );
}

export default Dashboard;
