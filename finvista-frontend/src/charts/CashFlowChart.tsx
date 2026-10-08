import { useState } from "react";
import {
  Bar,
  Cell,
  ComposedChart,
  CartesianGrid,
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
import {
  compactoFinanceiro,
  eixoFinanceiro,
  exportarFinanceiro,
  mesAtualSaoPaulo,
  moedaFinanceira,
  periodoISO,
  prepararEvolucao,
} from "../services/financialVisualization";
import "../styles/financial-evolution.css";
interface Props {
  dados: CashFlowData[];
  periodo: PeriodoFluxoCaixa;
  carregando: boolean;
  onPeriodoChange: (periodo: PeriodoFluxoCaixa) => void;
}
export default function CashFlowChart({
  dados,
  periodo,
  carregando,
  onPeriodoChange,
}: Props) {
  const [historico, setHistorico] = useState(false);
  const [tabela, setTabela] = useState(false);
  const meses = prepararEvolucao(dados);
  const entradas = dados.reduce((s, m) => s + m.entradas, 0),
    saidas = dados.reduce((s, m) => s + m.saidas, 0);
  const base = dados[0]?.saldoInicial ?? 0,
    ultimo = meses.at(-1);
  const eixoMensal = eixoFinanceiro(
    meses.flatMap((m) => [m.entradas, m.saidas, m.resultado]),
  );
  const eixoAcumulado = eixoFinanceiro(
    meses.map((m) => (historico ? m.acumuladoHistorico : m.acumuladoPeriodo)),
  );
  const atual = mesAtualSaoPaulo();
  const mesAtual = dados.find((m) => periodoISO(m.mes) === atual)?.mes;
  const exportar = () =>
    exportarFinanceiro("evolucao-financeira.csv", [
      [
        "Mês",
        "Receita registrada",
        "Despesa registrada",
        "Resultado mensal",
        "Resultado acumulado no período",
        "Acumulado com histórico",
        "Base inicial do mês",
      ],
      ...meses.map((m) => [
        m.mes,
        m.entradas,
        m.saidas,
        m.resultado,
        m.acumuladoPeriodo,
        m.acumuladoHistorico,
        m.saldoInicial,
      ]),
    ]);
  return (
    <section className="fe-card" aria-labelledby="fe-cash-title">
      <div className="fe-heading">
        <div>
          <span className="fe-eyebrow">Evolução financeira</span>
          <h1 id="fe-cash-title">Receitas, despesas e resultados</h1>
          <p>
            Valores registrados pela data de análise, incluindo lançamentos em
            aberto. Não representa somente pagamentos e recebimentos.
          </p>
        </div>
        <div className="fe-actions">
          {([6, 12, "todos"] as const).map((p) => (
            <button
              type="button"
              key={p}
              aria-pressed={periodo === p}
              disabled={carregando}
              onClick={() => onPeriodoChange(p)}
            >
              {p === "todos" ? "Todo o período" : `${p} meses`}
            </button>
          ))}
        </div>
      </div>
      {carregando ? (
        <p role="status">Atualizando evolução…</p>
      ) : !dados.length ? (
        <p>Nenhum lançamento encontrado no período.</p>
      ) : (
        <>
          <p className="fe-note">
            Período exibido: {dados[0].mes} — {dados.at(-1)?.mes}.
          </p>
          <div className="fe-summary">
            <div>
              Receita registrada<strong>{moedaFinanceira(entradas)}</strong>
            </div>
            <div>
              Despesa registrada<strong>{moedaFinanceira(saidas)}</strong>
            </div>
            <div>
              Resultado no período
              <strong>{moedaFinanceira(entradas - saidas)}</strong>
            </div>
          </div>
          <div className="fe-actions">
            <button type="button" onClick={() => setTabela(!tabela)}>
              {tabela ? "Ver gráficos" : "Ver como tabela"}
            </button>
            <button type="button" onClick={exportar}>
              Exportar CSV
            </button>
          </div>
          {tabela ? (
            <div className="fe-table">
              <table>
                <caption>
                  Resultados registrados; acumulados com bases distintas
                </caption>
                <thead>
                  <tr>
                    <th>Mês</th>
                    <th>Receita</th>
                    <th>Despesa</th>
                    <th>Resultado mensal</th>
                    <th>Acumulado no período</th>
                    <th>Acumulado com histórico</th>
                  </tr>
                </thead>
                <tbody>
                  {meses.map((m) => (
                    <tr key={m.mes}>
                      <td>{m.mes}</td>
                      <td>{moedaFinanceira(m.entradas)}</td>
                      <td>{moedaFinanceira(m.saidas)}</td>
                      <td>{moedaFinanceira(m.resultado)}</td>
                      <td>{moedaFinanceira(m.acumuladoPeriodo)}</td>
                      <td>{moedaFinanceira(m.acumuladoHistorico)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <>
              <h2>Resultado mensal</h2>
              <div className="fe-chart">
                <ResponsiveContainer width="100%" height="100%">
                  <ComposedChart
                    data={meses}
                    margin={{ top: 20, right: 15, left: 4, bottom: 5 }}
                  >
                    <CartesianGrid stroke="#e4eaf1" />
                    <XAxis dataKey="mes" minTickGap={35} />
                    <YAxis
                      width={65}
                      domain={eixoMensal.domain}
                      ticks={eixoMensal.ticks}
                      tickFormatter={compactoFinanceiro}
                    />
                    <Tooltip formatter={(v) => moedaFinanceira(Number(v))} />
                    <ReferenceLine y={0} stroke="#94a3b8" />
                    <Bar
                      dataKey="entradas"
                      name="Receita registrada"
                      fill="#3f806d"
                      maxBarSize={34}
                    >
                      {meses.map((m) => (
                        <Cell
                          key={m.mes}
                          fillOpacity={periodoISO(m.mes) >= atual ? 0.5 : 1}
                        />
                      ))}
                    </Bar>
                    <Bar
                      dataKey="saidas"
                      name="Despesa registrada"
                      fill="#c77d31"
                      maxBarSize={34}
                    >
                      {meses.map((m) => (
                        <Cell
                          key={m.mes}
                          fillOpacity={periodoISO(m.mes) >= atual ? 0.5 : 1}
                        />
                      ))}
                    </Bar>
                    <Line
                      type="linear"
                      dataKey="resultado"
                      name="Resultado mensal"
                      stroke="#2563eb"
                      dot={false}
                      strokeWidth={2}
                    />
                    {mesAtual && (
                      <ReferenceLine
                        x={mesAtual}
                        stroke="#94a3b8"
                        label="Mês atual"
                      />
                    )}
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
              <div className="fe-legend">
                <span>Verde: receita registrada</span>
                <span>Laranja: despesa registrada</span>
                <span>Azul: resultado mensal</span>
              </div>
              <div className="fe-heading">
                <h2>
                  {historico
                    ? "Acumulado incluindo o histórico anterior"
                    : "Resultado acumulado no período"}
                </h2>
                <label className="fe-check">
                  <input
                    type="checkbox"
                    checked={historico}
                    onChange={(e) => setHistorico(e.target.checked)}
                  />
                  Incluir histórico anterior
                </label>
              </div>
              <div className="fe-chart">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart
                    data={meses}
                    margin={{ top: 15, right: 15, left: 4, bottom: 5 }}
                  >
                    <CartesianGrid stroke="#e4eaf1" />
                    <XAxis dataKey="mes" minTickGap={35} />
                    <YAxis
                      width={65}
                      domain={eixoAcumulado.domain}
                      ticks={eixoAcumulado.ticks}
                      tickFormatter={compactoFinanceiro}
                    />
                    <Tooltip formatter={(v) => moedaFinanceira(Number(v))} />
                    <ReferenceLine y={0} stroke="#94a3b8" />
                    <Line
                      type="linear"
                      dataKey={
                        historico ? "acumuladoHistorico" : "acumuladoPeriodo"
                      }
                      name={
                        historico
                          ? "Acumulado com histórico"
                          : "Acumulado no período"
                      }
                      stroke="#153856"
                      dot={false}
                      strokeWidth={2}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </>
          )}
          <div className="fe-bases">
            <div>
              Resultado registrado antes do intervalo
              <strong>{moedaFinanceira(base)}</strong>
            </div>
            <div>
              Resultado acumulado no intervalo
              <strong>{moedaFinanceira(ultimo?.acumuladoPeriodo ?? 0)}</strong>
            </div>
            <div>
              Acumulado incluindo o histórico
              <strong>
                {moedaFinanceira(ultimo?.acumuladoHistorico ?? 0)}
              </strong>
            </div>
          </div>
          <p className="fe-note">
            O acumulado no intervalo parte de zero. O acumulado com histórico
            inclui o resultado registrado antes do intervalo; nenhum dos dois é
            um saldo bancário confirmado. Colunas claras identificam o mês em
            andamento ou meses futuros cadastrados e incluem o mês completo.
            “Todo o período” pode incluir lançamentos futuros.
          </p>
        </>
      )}
    </section>
  );
}
