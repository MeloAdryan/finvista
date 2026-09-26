import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";

import type { ExpenseDistributionData } from "../services/expenseDistributionService";

interface ExpenseDistributionChartProps {
  dados: ExpenseDistributionData[];
}

function ExpenseDistributionChart({ dados }: ExpenseDistributionChartProps) {
  const cores = [
    "#0f2a44",
    "#1e4068",
    "#2f7ed8",
    "#7cb5ef",
    "#b8763d",
    "#94a3b8",
  ];

  const formatarMoeda = (valor: number) =>
    valor.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });

  const totalDespesas = dados.reduce(
    (total, item) => total + Number(item.valor),
    0,
  );

  const maiorCategoria = dados.reduce<ExpenseDistributionData | undefined>(
    (maior, item) => {
      if (!maior || Number(item.valor) > Number(maior.valor)) {
        return item;
      }

      return maior;
    },
    undefined,
  );

  const percentualMaior =
    maiorCategoria && totalDespesas > 0
      ? (Number(maiorCategoria.valor) / totalDespesas) * 100
      : 0;

  const formatarPercentual = (valor: number) =>
    valor.toLocaleString("pt-BR", {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    });

  return (
    <section className="expense-distribution-card">
      <div className="expense-distribution-header">
        <div>
          <span className="expense-distribution-eyebrow">
            ANÁLISE DE DESPESAS
          </span>

          <h2>Distribuição das Despesas</h2>

          <p>Participação de cada categoria nas despesas totais do período.</p>
        </div>

        <span className="expense-distribution-status">
          <span className="expense-distribution-status-dot" />
          Atualizado
        </span>
      </div>

      <div className="expense-distribution-summary">
        <div className="expense-distribution-summary-item">
          <span>Total de despesas</span>
          <strong>{formatarMoeda(totalDespesas)}</strong>
          <small>Valor consolidado no período</small>
        </div>

        <div className="expense-distribution-summary-item">
          <span>Maior categoria</span>
          <strong>{maiorCategoria?.categoria ?? "—"}</strong>
          <small>
            {maiorCategoria
              ? formatarMoeda(Number(maiorCategoria.valor))
              : "Sem dados disponíveis"}
          </small>
        </div>

        <div className="expense-distribution-summary-item">
          <span>Maior participação</span>
          <strong>{formatarPercentual(percentualMaior)}%</strong>
          <small>Participação sobre as despesas totais</small>
        </div>
      </div>

      <div className="expense-distribution-chart-box">
        <div className="expense-distribution-chart-header">
          <div>
            <span>COMPOSIÇÃO</span>
            <h3>Participação por categoria</h3>
          </div>

          <span className="expense-distribution-chart-badge">
            {dados.length} categorias
          </span>
        </div>

        {dados.length > 0 ? (
          <div className="expense-distribution-chart-content">
            <div className="expense-distribution-chart-container">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={dados}
                    dataKey="valor"
                    nameKey="categoria"
                    cx="50%"
                    cy="50%"
                    innerRadius={72}
                    outerRadius={116}
                    paddingAngle={3}
                    stroke="none"
                  >
                    {dados.map((item, index) => (
                      <Cell
                        key={item.categoria}
                        fill={cores[index % cores.length]}
                      />
                    ))}
                  </Pie>

                  <Tooltip
                    formatter={(value) => formatarMoeda(Number(value))}
                  />
                </PieChart>
              </ResponsiveContainer>

              <div className="expense-distribution-chart-center">
                <span>Total</span>
                <strong>{formatarMoeda(totalDespesas)}</strong>
              </div>
            </div>

            <div className="expense-distribution-legend">
              {dados.map((item, index) => {
                const percentual =
                  totalDespesas > 0
                    ? (Number(item.valor) / totalDespesas) * 100
                    : 0;

                return (
                  <div
                    className="expense-distribution-legend-item"
                    key={item.categoria}
                  >
                    <div className="expense-distribution-legend-main">
                      <span
                        className="expense-distribution-legend-color"
                        style={{
                          backgroundColor: cores[index % cores.length],
                        }}
                      />

                      <div>
                        <strong>{item.categoria}</strong>
                        <small>{formatarMoeda(Number(item.valor))}</small>
                      </div>
                    </div>

                    <span className="expense-distribution-legend-percent">
                      {formatarPercentual(percentual)}%
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        ) : (
          <div className="expense-distribution-empty">
            <strong>Nenhuma despesa encontrada</strong>
            <span>Os dados aparecerão aqui quando estiverem disponíveis.</span>
          </div>
        )}
      </div>
    </section>
  );
}

export default ExpenseDistributionChart;
