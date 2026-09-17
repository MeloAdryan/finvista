import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import type {
  HistoryData,
} from '../services/historyService'

interface HistoryChartProps {
  dados: HistoryData[]
}

function HistoryChart({
  dados,
}: HistoryChartProps) {

  const formatarValor = (
    valor: number
  ) => {
    return new Intl.NumberFormat(
      'pt-BR',
      {
        notation: 'compact',
        compactDisplay: 'short',
      }
    ).format(valor)
  }

  return (
    <div className="history-card">

      <div className="history-header">

        <div>
          <h2>
            Histórico Financeiro
          </h2>

          <p>
            Evolução das receitas,
            despesas e resultados
          </p>
        </div>

      </div>

      <div className="history-chart-container">

        <ResponsiveContainer
          width="100%"
          height="100%"
        >

          <LineChart data={dados}>

            <CartesianGrid
              strokeDasharray="3 3"
              vertical={false}
            />

            <XAxis
              dataKey="periodo"
            />

            <YAxis
              tickFormatter={
                formatarValor
              }
            />

            <Tooltip
              formatter={(value) =>
                Number(value).toLocaleString(
                  'pt-BR',
                  {
                    style: 'currency',
                    currency: 'BRL',
                  }
                )
              }
            />

            <Legend />

            <Line
              type="monotone"
              dataKey="receita"
              name="Receita"
              stroke="#16a34a"
              strokeWidth={3}
            />

            <Line
              type="monotone"
              dataKey="despesa"
              name="Despesa"
              stroke="#dc2626"
              strokeWidth={3}
            />

            <Line
              type="monotone"
              dataKey="resultado"
              name="Resultado"
              stroke="#2563eb"
              strokeWidth={3}
            />

          </LineChart>

        </ResponsiveContainer>

      </div>

    </div>
  )
}

export default HistoryChart