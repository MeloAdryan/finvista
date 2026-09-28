import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import type { ExpenseDistributionData } from "../services/expenseDistributionService";

interface ExpenseDistributionChartProps {
  dados: ExpenseDistributionData[];
}

interface ExpenseChartData extends ExpenseDistributionData {
  valor: number;
  percentual: number;
}

function ExpenseDistributionChart({
  dados,
}: ExpenseDistributionChartProps) {
  const formatarMoeda = (valor: number) =>
    valor.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });

  const formatarMoedaCompacta = (valor: number) =>
    new Intl.NumberFormat("pt-BR", {
      notation: "compact",
      compactDisplay: "short",
      maximumFractionDigits: 1,
    }).format(valor);

  const formatarPercentual = (valor: number) =>
    valor.toLocaleString("pt-BR", {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    });

  const dadosNormalizados = dados.map((item) => ({
    ...item,
    valor: Number(item.valor),
  }));

  const totalDespesas = dadosNormalizados.reduce(
    (total, item) => total + item.valor,
    0,
  );

  const dadosOrdenados: ExpenseChartData[] =
    dadosNormalizados
      .map((item) => ({
        ...item,
        percentual:
          totalDespesas > 0
            ? (item.valor / totalDespesas) * 100
            : 0,
      }))
      .sort((a, b) => b.valor - a.valor);

  const maiorCategoria = dadosOrdenados[0];

  const percentualMaior =
    maiorCategoria?.percentual ?? 0;

  const alturaGrafico = Math.max(
    380,
    dadosOrdenados.length * 52,
  );

  const obterCorBarra = (index: number) => {
    if (index === 0) return "#173a5e";
    if (index === 1) return "#315f87";
    if (index === 2) return "#5c82a2";

    return "#89a4bb";
  };

  return (
    <section className="expense-distribution-card">
      <div className="expense-distribution-header">
        <div>
          <span className="expense-distribution-eyebrow">
            ANÁLISE DE DESPESAS
          </span>

          <h2>Distribuição das Despesas</h2>

          <p>
            Compare as categorias de maior impacto
            financeiro no período.
          </p>
        </div>

        <span className="expense-distribution-status">
          <span className="expense-distribution-status-dot" />
          Atualizado
        </span>
      </div>

      <div className="expense-distribution-summary">
        <div className="expense-distribution-summary-item">
          <span>Total de despesas</span>

          <strong>
            {formatarMoeda(totalDespesas)}
          </strong>

          <small>
            Valor consolidado no período
          </small>
        </div>

        <div className="expense-distribution-summary-item">
          <span>Maior categoria</span>

          <strong title={maiorCategoria?.categoria}>
            {maiorCategoria?.categoria ?? "—"}
          </strong>

          <small>
            {maiorCategoria
              ? formatarMoeda(maiorCategoria.valor)
              : "Sem dados disponíveis"}
          </small>
        </div>

        <div className="expense-distribution-summary-item">
          <span>Maior participação</span>

          <strong>
            {formatarPercentual(percentualMaior)}%
          </strong>

          <small>
            Participação nas despesas totais
          </small>
        </div>
      </div>

      <div className="expense-distribution-chart-box">
        <div className="expense-distribution-chart-header">
          <div>
            <span>COMPOSIÇÃO</span>

            <h3>Despesas por categoria</h3>

            <p>
              Categorias ordenadas do maior para o
              menor valor.
            </p>
          </div>

          <span className="expense-distribution-chart-badge">
            {dadosOrdenados.length}{" "}
            {dadosOrdenados.length === 1
              ? "categoria"
              : "categorias"}
          </span>
        </div>

        {dadosOrdenados.length > 0 ? (
          <>
            {/* DESKTOP / TABLET */}
            <div className="expense-chart-desktop">
              <ResponsiveContainer
                width="100%"
                height={alturaGrafico}
              >
                <BarChart
                  data={dadosOrdenados}
                  layout="vertical"
                  margin={{
                    top: 10,
                    right: 30,
                    bottom: 10,
                    left: 10,
                  }}
                  barCategoryGap="25%"
                >
                  <CartesianGrid
                    horizontal={false}
                    stroke="#e8edf3"
                    strokeDasharray="3 3"
                  />

                  <XAxis
                    type="number"
                    axisLine={false}
                    tickLine={false}
                    tick={{
                      fill: "#7c8796",
                      fontSize: 11,
                    }}
                    tickFormatter={
                      formatarMoedaCompacta
                    }
                  />

                  <YAxis
                    type="category"
                    dataKey="categoria"
                    width={190}
                    axisLine={false}
                    tickLine={false}
                    tick={{
                      fill: "#344054",
                      fontSize: 12,
                      fontWeight: 600,
                    }}
                  />

                  <Tooltip
                    cursor={{
                      fill:
                        "rgba(15, 42, 68, 0.035)",
                    }}
                    content={({
                      active,
                      payload,
                    }) => {
                      if (
                        !active ||
                        !payload?.length
                      ) {
                        return null;
                      }

                      const item =
                        payload[0]
                          .payload as ExpenseChartData;

                      return (
                        <div className="expense-chart-tooltip">
                          <span>
                            {item.categoria}
                          </span>

                          <strong>
                            {formatarMoeda(
                              item.valor,
                            )}
                          </strong>

                          <small>
                            {formatarPercentual(
                              item.percentual,
                            )}
                            % do total
                          </small>
                        </div>
                      );
                    }}
                  />

                  <Bar
                    dataKey="valor"
                    radius={[0, 6, 6, 0]}
                    maxBarSize={30}
                    animationDuration={650}
                  >
                    {dadosOrdenados.map(
                      (item, index) => (
                        <Cell
                          key={item.categoria}
                          fill={obterCorBarra(
                            index,
                          )}
                        />
                      ),
                    )}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>

            {/* CELULAR */}
            <div className="expense-chart-mobile">
              {dadosOrdenados.map(
                (item, index) => {
                  const maiorValor =
                    dadosOrdenados[0]?.valor || 1;

                  const largura =
                    (item.valor / maiorValor) *
                    100;

                  return (
                    <div
                      className="expense-mobile-item"
                      key={item.categoria}
                    >
                      <div className="expense-mobile-item-header">
                        <div className="expense-mobile-category">
                          <span className="expense-mobile-rank">
                            {index + 1}
                          </span>

                          <strong>
                            {item.categoria}
                          </strong>
                        </div>

                        <div className="expense-mobile-value">
                          <strong>
                            {formatarMoeda(
                              item.valor,
                            )}
                          </strong>

                          <span>
                            {formatarPercentual(
                              item.percentual,
                            )}
                            %
                          </span>
                        </div>
                      </div>

                      <div className="expense-mobile-track">
                        <div
                          className="expense-mobile-bar"
                          style={{
                            width: `${largura}%`,
                            backgroundColor:
                              obterCorBarra(
                                index,
                              ),
                          }}
                        />
                      </div>
                    </div>
                  );
                },
              )}
            </div>
          </>
        ) : (
          <div className="expense-distribution-empty">
            <strong>
              Nenhuma despesa encontrada
            </strong>

            <span>
              Os dados aparecerão aqui quando
              estiverem disponíveis.
            </span>
          </div>
        )}
      </div>
    </section>
  );
}

export default ExpenseDistributionChart;