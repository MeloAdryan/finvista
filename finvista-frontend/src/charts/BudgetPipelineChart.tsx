import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import type {
  BudgetPipelineData,
} from '../services/budgetPipelineService'

interface BudgetPipelineChartProps {
  dados: BudgetPipelineData[]
}

function BudgetPipelineChart({
  dados,
}: BudgetPipelineChartProps) {

  const formatarMoeda = (valor: number) => {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(valor)
  }

  const formatarValorCompacto = (valor: number) => {
    return new Intl.NumberFormat('pt-BR', {
      notation: 'compact',
      compactDisplay: 'short',
      maximumFractionDigits: 1,
    }).format(valor)
  }

  const totalPipeline = dados.reduce(
    (total, item) => total + Number(item.valor),
    0,
  )

  const totalPonderado = dados.reduce(
    (total, item) =>
      total + Number(item.valorPonderado),
    0,
  )

  const taxaPonderada =
    totalPipeline > 0
      ? (totalPonderado / totalPipeline) * 100
      : 0

  const maiorOportunidade =
    dados.length > 0
      ? dados.reduce((maior, atual) =>
          Number(atual.valor) > Number(maior.valor)
            ? atual
            : maior,
        )
      : null

  return (
    <div className="budget-pipeline-card">

      <div className="budget-pipeline-header">
        <div>
          <span className="budget-pipeline-eyebrow">
            PERFORMANCE COMERCIAL
          </span>

          <h2>Orçamentos / Pipeline</h2>

          <p>
            Valor potencial e receita ponderada das
            oportunidades comerciais.
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
              <span>Pipeline total</span>

              <strong>
                {formatarMoeda(totalPipeline)}
              </strong>

              <small>
                {dados.length} oportunidades
              </small>
            </div>

            <div className="budget-pipeline-metric">
              <span>Receita ponderada</span>

              <strong>
                {formatarMoeda(totalPonderado)}
              </strong>

              <small>
                Considerando probabilidades
              </small>
            </div>

            <div className="budget-pipeline-metric">
              <span>Taxa ponderada</span>

              <strong>
                {taxaPonderada.toLocaleString(
                  'pt-BR',
                  {
                    minimumFractionDigits: 1,
                    maximumFractionDigits: 1,
                  },
                )}
                %
              </strong>

              <small>
                Conversão potencial do pipeline
              </small>
            </div>

          </div>

          <div className="budget-pipeline-chart-box">

            <div className="budget-pipeline-chart-header">
              <div>
                <span>COMPARATIVO</span>

                <strong>
                  Valor potencial × ponderado
                </strong>
              </div>

              {maiorOportunidade && (
                <div className="budget-pipeline-highlight">
                  <span>Maior oportunidade</span>

                  <strong>
                    {maiorOportunidade.cliente}
                  </strong>
                </div>
              )}
            </div>

            <div className="budget-pipeline-legend">

              <div>
                <span className="budget-legend-dot budget-legend-total" />
                Valor do orçamento
              </div>

              <div>
                <span className="budget-legend-dot budget-legend-weighted" />
                Receita ponderada
              </div>

            </div>

            <div className="budget-pipeline-chart-container">

              <ResponsiveContainer
                width="100%"
                height="100%"
              >
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
                    dataKey="cliente"
                    axisLine={false}
                    tickLine={false}
                    tick={{
                      fill: '#64748b',
                      fontSize: 12,
                    }}
                    interval={0}
                    height={55}
                  />

                  <YAxis
                    axisLine={false}
                    tickLine={false}
                    tick={{
                      fill: '#64748b',
                      fontSize: 12,
                    }}
                    tickFormatter={formatarValorCompacto}
                    width={65}
                  />

                  <Tooltip
                    cursor={{
                      fill: 'rgba(15, 42, 68, 0.04)',
                    }}
                    contentStyle={{
                      border: '1px solid #dfe6ee',
                      borderRadius: '12px',
                      boxShadow:
                        '0 10px 30px rgba(15, 42, 68, 0.12)',
                    }}
                    formatter={(value) =>
                      formatarMoeda(Number(value))
                    }
                  />

                  <Bar
                    dataKey="valor"
                    name="Valor do orçamento"
                    fill="#1e4068"
                    radius={[7, 7, 0, 0]}
                    maxBarSize={48}
                  />

                  <Bar
                    dataKey="valorPonderado"
                    name="Receita ponderada"
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
          Nenhuma oportunidade comercial disponível.
        </div>
      )}

    </div>
  )
}

export default BudgetPipelineChart