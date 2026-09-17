import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

interface ResultMarginChartProps {
  resultado: number
  margem: number
}

function ResultMarginChart({
  resultado,
  margem,
}: ResultMarginChartProps) {
  const dadosResultado = [
    {
      nome: 'Resultado',
      valor: resultado,
    },
  ]

  return (
    <div className="chart-card">
      <div className="chart-header chart-header-result">
        <div>
          <h2>Resultado e Margem</h2>
          <p>Desempenho financeiro do período</p>
        </div>

        <div className="margin-indicator">
          <span>Margem</span>
          <strong>
            {margem.toLocaleString('pt-BR', {
              minimumFractionDigits: 2,
              maximumFractionDigits: 2,
            })}
            %
          </strong>
        </div>
      </div>

      <div className="chart-container">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={dadosResultado}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />

            <XAxis dataKey="nome" />

            <YAxis />

            <Tooltip
              formatter={(value) =>
                Number(value).toLocaleString('pt-BR', {
                  style: 'currency',
                  currency: 'BRL',
                })
              }
            />

            <Bar
              dataKey="valor"
              name="Resultado"
              fill="#2563eb"
              radius={[6, 6, 0, 0]}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

export default ResultMarginChart