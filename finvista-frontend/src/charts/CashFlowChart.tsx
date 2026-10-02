import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import type {
  CashFlowData,
  PeriodoFluxoCaixa,
} from "../services/cashFlowService";

interface CashFlowChartProps {
  dados: CashFlowData[];
  periodo: PeriodoFluxoCaixa;
  carregando: boolean;
  onPeriodoChange: (periodo: PeriodoFluxoCaixa) => void;
}

function CashFlowChart({
  dados,
  periodo,
  carregando,
  onPeriodoChange,
}: CashFlowChartProps) {
  const formatarCompacto = (valor: number) => {
    return new Intl.NumberFormat("pt-BR", {
      notation: "compact",
      compactDisplay: "short",
      maximumFractionDigits: 1,
    }).format(valor);
  };

  const formatarMoeda = (valor: number) => {
    return valor.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });
  };

  const primeiroMes = dados[0];
  const ultimoMes = dados[dados.length - 1];

  const totalEntradas = dados.reduce(
    (total, item) => total + Number(item.entradas),
    0,
  );

  const totalSaidas = dados.reduce(
    (total, item) => total + Number(item.saidas),
    0,
  );

  const saldoPeriodo = totalEntradas - totalSaidas;

  const saldoAcumuladoFinal = ultimoMes ? Number(ultimoMes.saldoFinal) : 0;

  const descricaoPeriodo =
    periodo === "todos" ? "Todo o período" : `${periodo} meses`;

  const valoresGrafico = dados.flatMap((item) => [
    Number(item.entradas),
    Number(item.saidas),
    Number(item.saldoFinal),
  ]);

  const menorValor =
    valoresGrafico.length > 0 ? Math.min(...valoresGrafico, 0) : 0;

  const maiorValor =
    valoresGrafico.length > 0 ? Math.max(...valoresGrafico, 0) : 0;

  const amplitude = Math.max(Math.abs(menorValor), Math.abs(maiorValor), 1);

  const margemDominio = amplitude * 0.1;

  const dominioMinimo = menorValor < 0 ? menorValor - margemDominio : 0;

  const dominioMaximo =
    maiorValor > 0 ? maiorValor + margemDominio : margemDominio;

  const classeSaldoFinal =
    saldoAcumuladoFinal > 0
      ? "cashflow-footer-value-positive"
      : saldoAcumuladoFinal < 0
        ? "cashflow-footer-value-negative"
        : "cashflow-footer-value-neutral";

  return (
    <section className="cashflow-card">
      <div className="cashflow-header">
        <div>
          <span className="cashflow-eyebrow">VISÃO FINANCEIRA</span>

          <h2>Fluxo de Caixa</h2>

          <p>Acompanhe entradas, saídas e a evolução acumulada do saldo.</p>
        </div>

        <div
          className={`cashflow-status ${
            carregando ? "cashflow-status-loading" : ""
          }`}
        >
          <span className="cashflow-status-dot" />

          {carregando ? "Atualizando..." : "Atualizado"}
        </div>
      </div>

      <div className="cashflow-summary">
        <div className="cashflow-summary-item">
          <span>Entradas no período</span>

          <strong>{carregando ? "..." : formatarMoeda(totalEntradas)}</strong>

          <small className="cashflow-positive">
            Receita no período selecionado
          </small>
        </div>

        <div className="cashflow-summary-item">
          <span>Saídas no período</span>

          <strong>{carregando ? "..." : formatarMoeda(totalSaidas)}</strong>

          <small className="cashflow-negative">
            Despesas no período selecionado
          </small>
        </div>

        <div className="cashflow-summary-item cashflow-balance">
          <span>Resultado do período</span>

          <strong>{carregando ? "..." : formatarMoeda(saldoPeriodo)}</strong>

          <small>
            {primeiroMes?.mes ?? "—"} até {ultimoMes?.mes ?? "—"}
          </small>
        </div>
      </div>

      <div className="cashflow-chart-header">
        <div className="cashflow-chart-title">
          <h3>Evolução financeira</h3>

          <p>Comparativo mensal do período</p>
        </div>

        <div
          className="cashflow-period-filter"
          role="group"
          aria-label="Período do fluxo de caixa"
        >
          <button
            type="button"
            className={periodo === 6 ? "active" : ""}
            disabled={carregando}
            aria-pressed={periodo === 6}
            onClick={() => onPeriodoChange(6)}
          >
            <span>6</span>
            <small>meses</small>
          </button>

          <button
            type="button"
            className={periodo === 12 ? "active" : ""}
            disabled={carregando}
            aria-pressed={periodo === 12}
            onClick={() => onPeriodoChange(12)}
          >
            <span>12</span>
            <small>meses</small>
          </button>

          <button
            type="button"
            className={periodo === "todos" ? "active" : ""}
            disabled={carregando}
            aria-pressed={periodo === "todos"}
            onClick={() => onPeriodoChange("todos")}
          >
            <span>Todo</span>
            <small>período</small>
          </button>
        </div>
      </div>

      <div className="cashflow-chart-container">
        {carregando && dados.length === 0 ? (
          <div className="cashflow-chart-empty">
            Carregando fluxo de caixa...
          </div>
        ) : dados.length === 0 ? (
          <div className="cashflow-chart-empty">
            Nenhum dado encontrado para o período selecionado.
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <LineChart
              data={dados}
              margin={{
                top: 12,
                right: 18,
                left: 8,
                bottom: 8,
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
                  fill: "#64748b",
                  fontSize: 12,
                }}
                dy={10}
                minTickGap={20}
              />

              <YAxis
                domain={[dominioMinimo, dominioMaximo]}
                tickFormatter={formatarCompacto}
                axisLine={false}
                tickLine={false}
                tick={{
                  fill: "#94a3b8",
                  fontSize: 12,
                }}
                width={70}
              />

              <ReferenceLine y={0} stroke="#94a3b8" strokeWidth={1.5} />

              <Tooltip
                cursor={{
                  stroke: "#cbd5e1",
                  strokeDasharray: "4 4",
                }}
                contentStyle={{
                  border: "1px solid #e2e8f0",
                  borderRadius: "12px",
                  boxShadow: "0 12px 30px rgba(15, 23, 42, 0.12)",
                  padding: "12px 14px",
                }}
                labelStyle={{
                  fontWeight: 700,
                  marginBottom: "8px",
                  color: "#0f172a",
                }}
                formatter={(value) => formatarMoeda(Number(value))}
              />

              <Legend
                verticalAlign="top"
                align="right"
                height={45}
                iconType="circle"
                wrapperStyle={{
                  fontSize: "13px",
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
                  fill: "#ffffff",
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
                  fill: "#ffffff",
                }}
                activeDot={{
                  r: 7,
                  strokeWidth: 3,
                }}
              />

              <Line
                type="monotone"
                dataKey="saldoFinal"
                name="Saldo acumulado"
                stroke="#2563eb"
                strokeWidth={4}
                dot={{
                  r: 4,
                  strokeWidth: 2,
                  fill: "#ffffff",
                }}
                activeDot={{
                  r: 7,
                  strokeWidth: 3,
                }}
              />
            </LineChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="cashflow-chart-footer">
        <div className="cashflow-footer-info">
          <span className="cashflow-footer-label">Período exibido</span>

          <strong className="cashflow-footer-value">{descricaoPeriodo}</strong>
        </div>

        <div className="cashflow-footer-divider" />

        <div className="cashflow-footer-info cashflow-footer-info-right">
          <span className="cashflow-footer-label">
            Saldo acumulado ao final
          </span>

          <strong className={`cashflow-footer-value ${classeSaldoFinal}`}>
            {carregando ? "..." : formatarMoeda(saldoAcumuladoFinal)}
          </strong>
        </div>
      </div>
    </section>
  );
}

export default CashFlowChart;
