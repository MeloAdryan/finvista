import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

interface RevenueExpenseChartProps {
  receita: number
  despesa: number
}

function RevenueExpenseChart({
  receita,
  despesa,
}: RevenueExpenseChartProps) {
  const dados = [
    {
      periodo: 'Atual',
      receita,
      despesa,
    },
  ]

  const formatarValor = (valor: number) => {
    return new Intl.NumberFormat('pt-BR', {
      notation: 'compact',
      compactDisplay: 'short',
    }).format(valor)
  }

  return (
    <div className="chart-card">
      <div className="chart-header">
        <div>
          <h2>Receita × Despesa</h2>
          <p>Comparativo financeiro do período</p>
        </div>
      </div>

      <div className="chart-container">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={dados}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />

            <XAxis dataKey="periodo" />

            <YAxis tickFormatter={formatarValor} />

            <Tooltip
              formatter={(value) =>
                Number(value).toLocaleString('pt-BR', {
                  style: 'currency',
                  currency: 'BRL',
                })
              }
            />

            <Legend />

            <Bar
              dataKey="receita"
              name="Receita"
              fill="#16a34a"
              radius={[6, 6, 0, 0]}
            />

            <Bar
              dataKey="despesa"
              name="Despesa"
              fill="#dc2626"
              radius={[6, 6, 0, 0]}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

export default RevenueExpenseChart