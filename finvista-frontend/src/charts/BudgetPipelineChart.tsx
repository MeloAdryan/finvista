import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import type { BudgetPipelineData } from "../services/budgetPipelineService";

interface BudgetPipelineChartProps {
  dados: BudgetPipelineData[];
}

function BudgetPipelineChart({ dados }: BudgetPipelineChartProps) {
  const formatarMoeda = (valor: number) => {
    return new Intl.NumberFormat("pt-BR", {
      style: "currency",
      currency: "BRL",
    }).format(valor);
  };

  const formatarValorCompacto = (valor: number) => {
    return new Intl.NumberFormat("pt-BR", {
      notation: "compact",
      compactDisplay: "short",
      maximumFractionDigits: 1,
    }).format(valor);
  };

  const totalPlanejado = dados.reduce(
    (total, item) => total + Number(item.valorPlanejado),
    0,
  );

  const totalUtilizado = dados.reduce(
    (total, item) => total + Number(item.valorUtilizado),
    0,
  );

  const totalDisponivel = dados.reduce(
    (total, item) => total + Number(item.valorDisponivel),
    0,
  );

  const percentualUtilizado =
    totalPlanejado > 0
      ? (totalUtilizado / totalPlanejado) * 100
      : 0;

  const maiorConsumo =
    dados.length > 0
      ? dados.reduce((maior, atual) =>
          Number(atual.percentualUtilizado) >
          Number(maior.percentualUtilizado)
            ? atual
            : maior,
        )
      : null;

  return (
    <div className="budget-pipeline-card">
      <div className="budget-pipeline-header">
        <div>
          <span className="budget-pipeline-eyebrow">
            CONTROLE ORÇAMENTÁRIO
          </span>

          <h2>Planejado × Utilizado</h2>

          <p>
            Acompanhamento dos recursos planejados e do consumo real
            identificado nas despesas da empresa.
          </p>
        </div>

        <div className="budget-pipeline-status">
          <span className="budget-pipeline-status-dot" />
          Atualizado
        </div>
      </div>

      {dados.length > 0 ? (
        <>
          <div className="budget-pipeline-metrics">
            <div className="budget-pipeline-metric">
              <span>Total planejado</span>

              <strong>{formatarMoeda(totalPlanejado)}</strong>

              <small>{dados.length} orçamentos</small>
            </div>

            <div className="budget-pipeline-metric">
              <span>Total utilizado</span>

              <strong>{formatarMoeda(totalUtilizado)}</strong>

              <small>Despesas vinculadas aos orçamentos</small>
            </div>

            <div className="budget-pipeline-metric">
              <span>Total disponível</span>

              <strong>{formatarMoeda(totalDisponivel)}</strong>

              <small>
                {percentualUtilizado.toLocaleString("pt-BR", {
                  minimumFractionDigits: 1,
                  maximumFractionDigits: 1,
                })}
                % do orçamento utilizado
              </small>
            </div>
          </div>

          <div className="budget-pipeline-chart-box">
            <div className="budget-pipeline-chart-header">
              <div>
                <span>COMPARATIVO</span>

                <strong>Planejado × utilizado</strong>
              </div>

              {maiorConsumo && (
                <div className="budget-pipeline-highlight">
                  <span>Maior utilização</span>

                  <strong>{maiorConsumo.nome}</strong>
                </div>
              )}
            </div>

            <div className="budget-pipeline-legend">
              <div>
                <span className="budget-legend-dot budget-legend-total" />
                Valor planejado
              </div>

              <div>
                <span className="budget-legend-dot budget-legend-weighted" />
                Valor utilizado
              </div>
            </div>

            <div className="budget-pipeline-chart-container">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart
                  data={dados}
                  margin={{
                    top: 12,
                    right: 12,
                    left: 0,
                    bottom: 8,
                  }}
                  barGap={5}
                >
                  <CartesianGrid
                    stroke="#e7edf3"
                    strokeDasharray="4 4"
                    vertical={false}
                  />

                  <XAxis
                    dataKey="nome"
                    axisLine={false}
                    tickLine={false}
                    tick={{
                      fill: "#64748b",
                      fontSize: 12,
                    }}
                    interval={0}
                    height={55}
                  />

                  <YAxis
                    axisLine={false}
                    tickLine={false}
                    tick={{
                      fill: "#64748b",
                      fontSize: 12,
                    }}
                    tickFormatter={formatarValorCompacto}
                    width={65}
                  />

                  <Tooltip
                    cursor={{
                      fill: "rgba(15, 42, 68, 0.04)",
                    }}
                    contentStyle={{
                      border: "1px solid #dfe6ee",
                      borderRadius: "12px",
                      boxShadow:
                        "0 10px 30px rgba(15, 42, 68, 0.12)",
                    }}
                    formatter={(value) =>
                      formatarMoeda(Number(value))
                    }
                  />

                  <Bar
                    dataKey="valorPlanejado"
                    name="Valor planejado"
                    fill="#1e4068"
                    radius={[7, 7, 0, 0]}
                    maxBarSize={48}
                  />

                  <Bar
                    dataKey="valorUtilizado"
                    name="Valor utilizado"
                    fill="#b8763d"
                    radius={[7, 7, 0, 0]}
                    maxBarSize={48}
                  />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </>
      ) : (
        <div className="budget-pipeline-empty">
          Nenhum orçamento financeiro disponível.
        </div>
      )}
    </div>
  );
}

export default BudgetPipelineChart;