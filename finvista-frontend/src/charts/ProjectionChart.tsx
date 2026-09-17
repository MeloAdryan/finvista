import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import type {
  ProjectionData,
} from '../services/projectionService'

interface ProjectionChartProps {
  dados: ProjectionData[]
}

function ProjectionChart({
  dados,
}: ProjectionChartProps) {

  const formatarMoeda = (valor: number) => {
    return Number(valor).toLocaleString(
      'pt-BR',
      {
        style: 'currency',
        currency: 'BRL',
      },
    )
  }

  const formatarValorCompacto = (
    valor: number,
  ) => {
    return new Intl.NumberFormat(
      'pt-BR',
      {
        notation: 'compact',
        compactDisplay: 'short',
      },
    ).format(valor)
  }

  const receitaTotal = dados.reduce(
    (total, item) =>
      total + Number(item.receita),
    0,
  )

  const resultadoTotal = dados.reduce(
    (total, item) =>
      total + Number(item.resultado),
    0,
  )

  const saldoFinal =
    dados.length > 0
      ? Number(
          dados[dados.length - 1].saldo,
        )
      : 0

  return (
    <div className="projection-card">

      <div className="projection-header">

        <div>
          <span className="projection-eyebrow">
            PLANEJAMENTO FINANCEIRO
          </span>

          <h2>
            Projeção de 6 meses
          </h2>

          <p>
            Evolução prevista de receita,
            despesa, resultado e saldo
            acumulado.
          </p>
        </div>

        <div className="projection-status">
          <span className="projection-status-dot" />

          Projetado
        </div>

      </div>

      <div className="projection-metrics">

        <div className="projection-metric">
          <span>
            Receita no período
          </span>

          <strong>
            {formatarMoeda(receitaTotal)}
          </strong>

          <small>
            Soma dos 6 meses
          </small>
        </div>

        <div className="projection-metric projection-metric-result">
          <span>
            Resultado acumulado
          </span>

          <strong>
            {formatarMoeda(resultadoTotal)}
          </strong>

          <small>
            Receita menos despesas
          </small>
        </div>

        <div className="projection-metric projection-metric-balance">
          <span>
            Saldo projetado
          </span>

          <strong>
            {formatarMoeda(saldoFinal)}
          </strong>

          <small>
            Ao final do período
          </small>
        </div>

      </div>

      <div className="projection-chart-box">

        <div className="projection-chart-header">

          <div>
            <span>
              EVOLUÇÃO MENSAL
            </span>

            <strong>
              Desempenho projetado
            </strong>
          </div>

          <div className="projection-chart-highlight">
            <span>
              Saldo final
            </span>

            <strong>
              {formatarMoeda(saldoFinal)}
            </strong>
          </div>

        </div>

        <div className="projection-custom-legend">

          <span>
            <i className="projection-legend-dot projection-legend-revenue" />
            Receita
          </span>

          <span>
            <i className="projection-legend-dot projection-legend-expense" />
            Despesa
          </span>

          <span>
            <i className="projection-legend-dot projection-legend-result" />
            Resultado
          </span>

          <span>
            <i className="projection-legend-dot projection-legend-balance" />
            Saldo projetado
          </span>

        </div>

        {dados.length > 0 ? (

          <div className="projection-chart-container">

            <ResponsiveContainer
              width="100%"
              height="100%"
            >
              <LineChart
                data={dados}
                margin={{
                  top: 12,
                  right: 16,
                  left: 4,
                  bottom: 4,
                }}
              >

                <CartesianGrid
                  stroke="#e7edf3"
                  strokeDasharray="4 4"
                  vertical={false}
                />

                <XAxis
                  dataKey="mes"
                  axisLine={false}
                  tickLine={false}
                  tick={{
                    fill: '#64748b',
                    fontSize: 12,
                  }}
                  dy={8}
                />

                <YAxis
                  tickFormatter={
                    formatarValorCompacto
                  }
                  axisLine={false}
                  tickLine={false}
                  tick={{
                    fill: '#64748b',
                    fontSize: 11,
                  }}
                  width={62}
                />

                <Tooltip
                  formatter={(value, name) => [
                    formatarMoeda(
                      Number(value),
                    ),
                    String(name),
                  ]}
                  contentStyle={{
                    borderRadius: '12px',
                    border:
                      '1px solid #dfe6ee',
                    boxShadow:
                      '0 10px 30px rgba(15, 42, 68, 0.12)',
                  }}
                />

                <Line
                  type="monotone"
                  dataKey="receita"
                  name="Receita"
                  stroke="#1e4068"
                  strokeWidth={3}
                  dot={{
                    r: 4,
                    fill: '#ffffff',
                    strokeWidth: 2,
                  }}
                  activeDot={{
                    r: 6,
                  }}
                />

                <Line
                  type="monotone"
                  dataKey="despesa"
                  name="Despesa"
                  stroke="#ef4444"
                  strokeWidth={2.5}
                  dot={{
                    r: 3,
                    fill: '#ffffff',
                    strokeWidth: 2,
                  }}
                  activeDot={{
                    r: 5,
                  }}
                />

                <Line
                  type="monotone"
                  dataKey="resultado"
                  name="Resultado"
                  stroke="#2f7ed8"
                  strokeWidth={2.5}
                  dot={{
                    r: 3,
                    fill: '#ffffff',
                    strokeWidth: 2,
                  }}
                  activeDot={{
                    r: 5,
                  }}
                />

                <Line
                  type="monotone"
                  dataKey="saldo"
                  name="Saldo projetado"
                  stroke="#b8763d"
                  strokeWidth={3.5}
                  dot={{
                    r: 4,
                    fill: '#ffffff',
                    strokeWidth: 2,
                  }}
                  activeDot={{
                    r: 6,
                  }}
                />

              </LineChart>
            </ResponsiveContainer>

          </div>

        ) : (

          <div className="projection-empty">
            Nenhuma projeção financeira
            disponível.
          </div>

        )}

      </div>

    </div>
  )
}

export default ProjectionChart