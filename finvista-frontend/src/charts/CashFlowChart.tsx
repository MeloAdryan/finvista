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

import type { CashFlowData } from '../services/cashFlowService'

interface CashFlowChartProps {
  dados: CashFlowData[]
}

function CashFlowChart({ dados }: CashFlowChartProps) {
  const formatarCompacto = (valor: number) => {
    return new Intl.NumberFormat('pt-BR', {
      notation: 'compact',
      compactDisplay: 'short',
    }).format(valor)
  }

  const formatarMoeda = (valor: number) => {
    return valor.toLocaleString('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    })
  }

  const ultimoMes = dados[dados.length - 1]

  const totalEntradas = dados.reduce(
    (total, item) => total + Number(item.entradas),
    0
  )

  const totalSaidas = dados.reduce(
    (total, item) => total + Number(item.saidas),
    0
  )

  const saldoAtual = ultimoMes
    ? Number(ultimoMes.saldoFinal)
    : 0

  return (
    <section className="cashflow-card">
      <div className="cashflow-header">
        <div>
          <span className="cashflow-eyebrow">
            VISÃO FINANCEIRA
          </span>

          <h2>Fluxo de Caixa</h2>

          <p>
            Acompanhe entradas, saídas e a evolução
            acumulada do saldo.
          </p>
        </div>

        <div className="cashflow-status">
          <span className="cashflow-status-dot" />
          Atualizado
        </div>
      </div>

      <div className="cashflow-summary">
        <div className="cashflow-summary-item">
          <span>Entradas no período</span>

          <strong>
            {formatarMoeda(totalEntradas)}
          </strong>

          <small className="cashflow-positive">
            Receita acumulada
          </small>
        </div>

        <div className="cashflow-summary-item">
          <span>Saídas no período</span>

          <strong>
            {formatarMoeda(totalSaidas)}
          </strong>

          <small className="cashflow-negative">
            Despesas acumuladas
          </small>
        </div>

        <div className="cashflow-summary-item cashflow-balance">
          <span>Saldo projetado</span>

          <strong>
            {formatarMoeda(saldoAtual)}
          </strong>

          <small>
            Ao final de {ultimoMes?.mes ?? '—'}
          </small>
        </div>
      </div>

      <div className="cashflow-chart-header">
        <div>
          <h3>Evolução financeira</h3>

          <p>
            Comparativo mensal do período
          </p>
        </div>

        <span className="cashflow-period">
          6 meses
        </span>
      </div>

      <div className="cashflow-chart-container">
        <ResponsiveContainer
          width="100%"
          height="100%"
        >
          <LineChart
            data={dados}
            margin={{
              top: 10,
              right: 15,
              left: 5,
              bottom: 5,
            }}
          >
            <CartesianGrid
              strokeDasharray="4 4"
              vertical={false}
              stroke="#e8edf3"
            />

            <XAxis
              dataKey="mes"
              axisLine={false}
              tickLine={false}
              tick={{
                fill: '#64748b',
                fontSize: 12,
              }}
              dy={10}
            />

            <YAxis
              tickFormatter={formatarCompacto}
              axisLine={false}
              tickLine={false}
              tick={{
                fill: '#94a3b8',
                fontSize: 12,
              }}
              width={60}
            />

            <Tooltip
              cursor={{
                stroke: '#cbd5e1',
                strokeDasharray: '4 4',
              }}
              contentStyle={{
                border: '1px solid #e2e8f0',
                borderRadius: '12px',
                boxShadow:
                  '0 12px 30px rgba(15, 23, 42, 0.12)',
                padding: '12px 14px',
              }}
              labelStyle={{
                fontWeight: 700,
                marginBottom: '8px',
                color: '#0f172a',
              }}
              formatter={(value) =>
                formatarMoeda(Number(value))
              }
            />

            <Legend
              verticalAlign="top"
              align="right"
              height={45}
              iconType="circle"
              wrapperStyle={{
                fontSize: '13px',
              }}
            />

            <Line
              type="monotone"
              dataKey="entradas"
              name="Entradas"
              stroke="#16a34a"
              strokeWidth={3}
              dot={{
                r: 4,
                strokeWidth: 2,
                fill: '#ffffff',
              }}
              activeDot={{
                r: 7,
                strokeWidth: 3,
              }}
            />

            <Line
              type="monotone"
              dataKey="saidas"
              name="Saídas"
              stroke="#dc2626"
              strokeWidth={3}
              dot={{
                r: 4,
                strokeWidth: 2,
                fill: '#ffffff',
              }}
              activeDot={{
                r: 7,
                strokeWidth: 3,
              }}
            />

            <Line
              type="monotone"
              dataKey="saldoFinal"
              name="Saldo final"
              stroke="#2563eb"
              strokeWidth={4}
              dot={{
                r: 4,
                strokeWidth: 2,
                fill: '#ffffff',
              }}
              activeDot={{
                r: 7,
                strokeWidth: 3,
              }}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </section>
  )
}

export default CashFlowChart