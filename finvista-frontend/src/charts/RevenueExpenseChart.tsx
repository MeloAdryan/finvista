import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

interface RevenueExpenseChartProps {
  receita: number;
  despesa: number;
}

function RevenueExpenseChart({
  receita,
  despesa,
}: RevenueExpenseChartProps) {
  const dados = [
    {
      periodo: "Período atual",
      receita,
      despesa,
    },
  ];

  const formatarCompacto = (valor: number) =>
    new Intl.NumberFormat("pt-BR", {
      notation: "compact",
      maximumFractionDigits: 1,
    }).format(valor);

  const formatarMoeda = (valor: number) =>
    valor.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });

  return (
    <article className="executive-panel revenue-expense-panel">
      <div className="executive-panel-header">
        <div>
          <span className="executive-eyebrow">
            Desempenho
          </span>

          <h2>Receita × Despesa</h2>

          <p>
            Comparativo financeiro do período atual
          </p>
        </div>

        <div className="chart-legend">
          <span>
            <i className="legend-dot legend-revenue" />
            Receita
          </span>

          <span>
            <i className="legend-dot legend-expense" />
            Despesa
          </span>
        </div>
      </div>

      <div className="executive-chart-container">
        <ResponsiveContainer
          width="100%"
          height="100%"
        >
          <BarChart
            data={dados}
            barGap={14}
            margin={{
              top: 18,
              right: 12,
              left: 0,
              bottom: 0,
            }}
          >
            <CartesianGrid
              stroke="#e8edf3"
              strokeDasharray="3 5"
              vertical={false}
            />

            <XAxis
              dataKey="periodo"
              axisLine={false}
              tickLine={false}
              tick={{
                fill: "#64748b",
                fontSize: 12,
              }}
              dy={8}
            />

            <YAxis
              axisLine={false}
              tickLine={false}
              tick={{
                fill: "#64748b",
                fontSize: 12,
              }}
              tickFormatter={formatarCompacto}
              width={58}
            />

            <Tooltip
              cursor={{
                fill: "rgba(15, 42, 68, 0.035)",
              }}
              contentStyle={{
                border: "1px solid #dfe6ee",
                borderRadius: "10px",
                boxShadow:
                  "0 10px 28px rgba(15, 42, 68, 0.10)",
                fontSize: "13px",
              }}
              formatter={(value, name) => [
                formatarMoeda(Number(value)),
                name === "receita"
                  ? "Receita"
                  : "Despesa",
              ]}
            />

            <Bar
              dataKey="receita"
              fill="#1e4068"
              radius={[7, 7, 2, 2]}
              maxBarSize={72}
              animationDuration={650}
            />

            <Bar
              dataKey="despesa"
              fill="#8ba4ba"
              radius={[7, 7, 2, 2]}
              maxBarSize={72}
              animationDuration={650}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </article>
  );
}

export default RevenueExpenseChart;