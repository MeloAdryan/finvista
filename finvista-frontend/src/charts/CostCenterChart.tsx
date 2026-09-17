import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import type { CostCenterData } from '../services/costCenterService'

interface CostCenterChartProps {
  dados: CostCenterData[]
}

function CostCenterChart({
  dados,
}: CostCenterChartProps) {
  const formatarCompacto = (valor: number) => {
    return new Intl.NumberFormat('pt-BR', {
      notation: 'compact',
      compactDisplay: 'short',
    }).format(valor)
  }

  const formatarMoeda = (valor: number) => {
    return Number(valor).toLocaleString(
      'pt-BR',
      {
        style: 'currency',
        currency: 'BRL',
      }
    )
  }

  const totalCustos = dados.reduce(
    (total, item) =>
      total + Number(item.valor),
    0
  )

  const maiorCentro = dados.reduce<
    CostCenterData | undefined
  >(
    (maior, item) => {
      if (
        !maior ||
        Number(item.valor) >
          Number(maior.valor)
      ) {
        return item
      }

      return maior
    },
    undefined
  )

  const participacaoMaiorCentro =
    maiorCentro && totalCustos > 0
      ? (
          Number(maiorCentro.valor) /
          totalCustos
        ) * 100
      : 0

  const mediaCustos =
    dados.length > 0
      ? totalCustos / dados.length
      : 0

  return (
    <section className="cost-center-card">
      <div className="cost-center-header">
        <div>
          <span className="cost-center-eyebrow">
            ANÁLISE DE CUSTOS
          </span>

          <h2>Centros de Custo</h2>

          <p>
            Acompanhe a distribuição das despesas
            entre as áreas da empresa.
          </p>
        </div>

        <div className="cost-center-status">
          <span className="cost-center-status-dot" />
          Atualizado
        </div>
      </div>

      <div className="cost-center-summary">
        <div className="cost-center-summary-item">
          <span>
            Custo total
          </span>

          <strong>
            {formatarMoeda(totalCustos)}
          </strong>

          <small>
            Soma dos centros de custo
          </small>
        </div>

        <div className="cost-center-summary-item">
          <span>
            Maior centro
          </span>

          <strong>
            {maiorCentro?.nome ?? '—'}
          </strong>

          <small className="cost-center-highlight">
            {maiorCentro
              ? `${participacaoMaiorCentro.toFixed(
                  1
                )}% do total`
              : 'Sem dados'}
          </small>
        </div>

        <div className="cost-center-summary-item">
          <span>
            Média por centro
          </span>

          <strong>
            {formatarMoeda(mediaCustos)}
          </strong>

          <small>
            {dados.length}{' '}
            {dados.length === 1
              ? 'centro analisado'
              : 'centros analisados'}
          </small>
        </div>
      </div>

      <div className="cost-center-chart-header">
        <div>
          <h3>
            Distribuição por área
          </h3>

          <p>
            Comparativo das despesas por
            centro de custo
          </p>
        </div>

        <span className="cost-center-total-badge">
          {dados.length} centros
        </span>
      </div>

      <div className="cost-center-chart-container">
        {dados.length > 0 ? (
          <ResponsiveContainer
            width="100%"
            height="100%"
          >
            <BarChart
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
                dataKey="nome"
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
                  fill: 'rgba(37, 99, 235, 0.05)',
                }}
                contentStyle={{
                  border:
                    '1px solid #e2e8f0',
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
                formatter={(value) => [
                  formatarMoeda(
                    Number(value)
                  ),
                  'Despesa',
                ]}
              />

              <Bar
                dataKey="valor"
                name="Despesa"
                fill="#2563eb"
                radius={[8, 8, 3, 3]}
                maxBarSize={70}
              />
            </BarChart>
          </ResponsiveContainer>
        ) : (
          <div className="cost-center-empty">
            <strong>
              Nenhum centro de custo encontrado
            </strong>

            <span>
              Os dados aparecerão aqui quando
              estiverem disponíveis.
            </span>
          </div>
        )}
      </div>
    </section>
  )
}

export default CostCenterChart