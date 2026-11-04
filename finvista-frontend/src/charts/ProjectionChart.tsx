import { FinancialChartPaint } from "../components/FinancialChartPaint";
import { useChartAppearance } from "../services/chartAppearance";
import { useState } from "react";
import {
  Area,
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { ProjectionPlanning } from "../services/projectionPlanningService";
import {
  compactoFinanceiro,
  eixoFinanceiro,
  exportarFinanceiro,
  moedaFinanceira,
} from "../services/financialVisualization";
import "../styles/financial-evolution.css";
export default function ProjectionChart({
  dados,
}: {
  dados: ProjectionPlanning;
}) {
  const appearance = useChartAppearance();
  const [tabela, setTabela] = useState(false);
  const [visual, setVisual] = useState<"cenarios" | "mensal">("cenarios");
  const ultimo = dados.meses.at(-1);
  const mensal = eixoFinanceiro(
    dados.meses.flatMap((m) => [
      m.receitaSimulada,
      m.receitaRegistrada,
      m.receitaComplementar,
      m.despesaRegistrada,
      m.resultadoMensal,
    ]),
  );
  const saldo = eixoFinanceiro([
    dados.saldoInicial,
    ...dados.meses.flatMap((m) => [
      m.saldoConservador,
      m.saldoOtimista,
      m.saldoBase,
    ]),
  ]);
  const faixa = [
    {
      periodo: "Início",
      saldoBase: dados.saldoInicial,
      saldoConservador: dados.saldoInicial,
      saldoOtimista: dados.saldoInicial,
      faixa: [dados.saldoInicial, dados.saldoInicial],
    },
    ...dados.meses.map((m) => ({
      ...m,
      faixa: [m.saldoConservador, m.saldoOtimista],
    })),
  ];
  const receita = dados.meses.reduce((s, m) => s + m.receitaSimulada, 0);
  const complemento = dados.meses.reduce(
    (s, m) => s + m.receitaComplementar,
    0,
  );
  const exportar = () =>
    exportarFinanceiro("projecao-cenarios.csv", [
      [
        "Período",
        "Receita registrada",
        "Receita complementar simulada",
        "Receita simulada total",
        "Despesa registrada",
        "Resultado mensal simulado",
        "Resultado acumulado simulado",
        "Saldo base",
        "Saldo conservador",
        "Saldo otimista",
        "Saldo inicial informado",
        "Variação receita (%)",
      ],
      ...dados.meses.map((m) => [
        m.periodo,
        m.receitaRegistrada,
        m.receitaComplementar,
        m.receitaSimulada,
        m.despesaRegistrada,
        m.resultadoMensal,
        m.resultadoAcumulado,
        m.saldoBase,
        m.saldoConservador,
        m.saldoOtimista,
        dados.saldoInicial,
        dados.variacaoPercentual,
      ]),
    ]);
  return (
    <section className="fe-card projection-results">
      <FinancialChartPaint id={appearance.id} />
      <div className="fe-heading">
        <div>
          <h2>Resultados da simulação</h2>
          <p>
            {dados.inicio.split("-").reverse().join("/")} —{" "}
            {dados.fim.split("-").reverse().join("/")} · meses da simulação no
            recorte selecionado.
          </p>
        </div>
        <div className="fe-actions">
          <button type="button" onClick={() => setTabela(!tabela)}>
            {tabela ? "Ver gráficos" : "Ver como tabela"}
          </button>
          <button type="button" onClick={exportar}>
            Exportar CSV
          </button>
        </div>
      </div>
      <div className="fe-summary">
        <div>
          Receita total simulada<strong>{moedaFinanceira(receita)}</strong>
        </div>
        <div>
          Resultado acumulado simulado
          <strong>{moedaFinanceira(ultimo?.resultadoAcumulado ?? 0)}</strong>
        </div>
        <div>
          Saldo final do cenário base
          <strong>
            {moedaFinanceira(ultimo?.saldoBase ?? dados.saldoInicial)}
          </strong>
        </div>
      </div>
      <p className="fe-notice">
        {complemento > 0
          ? `Receita complementar hipotética: ${moedaFinanceira(complemento)}. O alvo mensal completa a receita cadastrada até o valor esperado; não é somado integralmente outra vez.`
          : "Sem receita complementar projetada: os resultados consideram somente receitas e despesas cadastradas."}{" "}
        Despesas ainda não cadastradas não entram nesta simulação.
      </p>
      <div
        className="fe-actions projection-view"
        role="group"
        aria-label="Visualização da simulação"
      >
        <button
          type="button"
          aria-pressed={!tabela && visual === "cenarios"}
          onClick={() => {
            setVisual("cenarios");
            setTabela(false);
          }}
        >
          Cenários de saldo
        </button>
        <button
          type="button"
          aria-pressed={!tabela && visual === "mensal"}
          onClick={() => {
            setVisual("mensal");
            setTabela(false);
          }}
        >
          Composição mensal
        </button>
      </div>
      {tabela ? (
        <div
          className="fe-table"
          tabIndex={0}
          role="region"
          aria-label="Simulação mensal, role horizontalmente para ver todas as colunas"
        >
          <table>
            <caption>Simulação por mês e por cenário</caption>
            <thead>
              <tr>
                <th>Mês</th>
                <th>Receita registrada</th>
                <th>Complemento</th>
                <th>Despesa registrada</th>
                <th>Resultado mensal</th>
                <th>Acumulado no período</th>
                <th>Saldo base</th>
                <th>Conservador</th>
                <th>Otimista</th>
              </tr>
            </thead>
            <tbody>
              {dados.meses.map((m) => (
                <tr key={m.periodo}>
                  <td>{m.periodo}</td>
                  <td>{moedaFinanceira(m.receitaRegistrada)}</td>
                  <td>{moedaFinanceira(m.receitaComplementar)}</td>
                  <td>{moedaFinanceira(m.despesaRegistrada)}</td>
                  <td>{moedaFinanceira(m.resultadoMensal)}</td>
                  <td>{moedaFinanceira(m.resultadoAcumulado)}</td>
                  <td>{moedaFinanceira(m.saldoBase)}</td>
                  <td>{moedaFinanceira(m.saldoConservador)}</td>
                  <td>{moedaFinanceira(m.saldoOtimista)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <>
          {visual === "mensal" && (
            <>
              <h2>Composição mensal simulada</h2>
              <div className="fe-chart">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart
                    data={dados.meses}
                    margin={{ top: 15, right: 15, left: 4, bottom: 5 }}
                  >
                    <CartesianGrid
                      stroke="var(--fv-chart-grid, #E5E2DC)"
                      vertical={false}
                      strokeDasharray="3 4"
                    />
                    <XAxis dataKey="periodo" minTickGap={30} />
                    <YAxis
                      width={65}
                      domain={mensal.domain}
                      ticks={mensal.ticks}
                      tickFormatter={compactoFinanceiro}
                    />
                    <Tooltip
                      contentStyle={{
                        background: "var(--fv-surface, #fff)",
                        borderColor: "var(--fv-border, #E5E2DC)",
                        color: "var(--fv-text, #172327)",
                      }}
                      formatter={(v) => moedaFinanceira(Number(v))}
                    />
                    <ReferenceLine y={0} stroke="var(--fv-muted, #596660)" />
                    <Bar
                      isAnimationActive={false}
                      dataKey="receitaRegistrada"
                      name="Receita registrada"
                      stackId="receita"
                      fill={appearance.fill("#3f806d")}
                      maxBarSize={32}
                    />
                    <Bar
                      isAnimationActive={false}
                      dataKey="receitaComplementar"
                      name="Receita complementar simulada"
                      stackId="receita"
                      fill={appearance.fill("#b1d5c9")}
                      maxBarSize={32}
                    />
                    <Bar
                      isAnimationActive={false}
                      dataKey="despesaRegistrada"
                      name="Despesa registrada"
                      fill={appearance.fill("#c77d31")}
                      maxBarSize={32}
                    />
                    <Line
                      isAnimationActive={false}
                      type="linear"
                      dataKey="resultadoMensal"
                      name="Resultado mensal simulado"
                      stroke="var(--fv-chart-blue, #3786dc)"
                      strokeDasharray="5 4"
                      dot={false}
                      strokeWidth={2}
                    />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
              <div className="fe-legend">
                <span>▰ Receita registrada</span>
                <span>▱ Complemento hipotético</span>
                <span>▼ Despesa registrada</span>
                <span>┄ Resultado mensal simulado</span>
              </div>
            </>
          )}
          {visual === "cenarios" && (
            <>
              <h2>Saldo simulado por cenário</h2>
              <div className="fe-chart">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart
                    data={faixa}
                    margin={{ top: 20, right: 15, left: 4, bottom: 5 }}
                  >
                    <CartesianGrid
                      stroke="var(--fv-chart-grid, #E5E2DC)"
                      vertical={false}
                      strokeDasharray="3 4"
                    />
                    <XAxis dataKey="periodo" minTickGap={30} />
                    <YAxis
                      width={65}
                      domain={saldo.domain}
                      ticks={saldo.ticks}
                      tickFormatter={compactoFinanceiro}
                    />
                    <Tooltip
                      contentStyle={{
                        background: "var(--fv-surface, #fff)",
                        borderColor: "var(--fv-border, #E5E2DC)",
                        color: "var(--fv-text, #172327)",
                      }}
                      formatter={(v) =>
                        Array.isArray(v)
                          ? `${moedaFinanceira(Number(v[0]))} — ${moedaFinanceira(Number(v[1]))}`
                          : moedaFinanceira(Number(v))
                      }
                    />
                    <ReferenceLine y={0} stroke="var(--fv-muted, #596660)" />
                    <Area
                      isAnimationActive={false}
                      type="linear"
                      dataKey="faixa"
                      name="Faixa conservador → otimista"
                      stroke="none"
                      fill={appearance.area("#bfd5f4")}
                      fillOpacity={0.6}
                    />
                    <Line
                      isAnimationActive={false}
                      type="linear"
                      dataKey="saldoBase"
                      name="Saldo simulado base"
                      stroke="var(--fv-chart-blue, #3786dc)"
                      strokeWidth={2}
                      dot={false}
                    />
                    <Line
                      isAnimationActive={false}
                      type="linear"
                      dataKey="saldoConservador"
                      name="Saldo conservador"
                      stroke="var(--fv-chart-orange, #e58a2f)"
                      strokeDasharray="8 5"
                      strokeWidth={2}
                      dot={false}
                    />
                    <Line
                      isAnimationActive={false}
                      type="linear"
                      dataKey="saldoOtimista"
                      name="Saldo otimista"
                      stroke="var(--fv-chart-success, #2c9474)"
                      strokeDasharray="2 5"
                      strokeWidth={2}
                      dot={false}
                    />
                    <ReferenceLine
                      x="Início"
                      stroke="var(--fv-muted, #596660)"
                      label="Base informada"
                    />
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
              <div
                className="fe-legend projection-legend"
                aria-label="Legenda dos cenários"
              >
                <span>
                  <i className="projection-line base" aria-hidden="true" />
                  Base · linha contínua
                </span>
                <span>
                  <i
                    className="projection-line conservative"
                    aria-hidden="true"
                  />
                  Conservador · tracejada
                </span>
                <span>
                  <i
                    className="projection-line optimistic"
                    aria-hidden="true"
                  />
                  Otimista · pontilhada
                </span>
              </div>
            </>
          )}
        </>
      )}
      <div className="fe-bases">
        <div>
          Saldo inicial informado
          <strong>{moedaFinanceira(dados.saldoInicial)}</strong>
        </div>
        <div>
          Final conservador
          <strong>
            {moedaFinanceira(ultimo?.saldoConservador ?? dados.saldoInicial)}
          </strong>
        </div>
        <div>
          Final otimista
          <strong>
            {moedaFinanceira(ultimo?.saldoOtimista ?? dados.saldoInicial)}
          </strong>
        </div>
      </div>
    </section>
  );
}
