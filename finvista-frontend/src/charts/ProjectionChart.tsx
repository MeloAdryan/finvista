import { useState } from "react";
import {
  Area,
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceArea,
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
  const [tabela, setTabela] = useState(false);
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
    <section className="fe-card">
      <div className="fe-heading">
        <div>
          <h2>Resultados da simulação</h2>
          <p>
            {dados.inicio.split("-").reverse().join("/")} —{" "}
            {dados.fim.split("-").reverse().join("/")} · meses completos,
            incluindo o mês atual.
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
      {tabela ? (
        <div className="fe-table">
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
          <h2>Resultado mensal simulado</h2>
          <div className="fe-chart">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart
                data={dados.meses}
                margin={{ top: 15, right: 15, left: 4, bottom: 5 }}
              >
                <CartesianGrid stroke="#e4eaf1" />
                <XAxis dataKey="periodo" minTickGap={30} />
                <YAxis
                  width={65}
                  domain={mensal.domain}
                  ticks={mensal.ticks}
                  tickFormatter={compactoFinanceiro}
                />
                <Tooltip formatter={(v) => moedaFinanceira(Number(v))} />
                <ReferenceLine y={0} stroke="#94a3b8" />
                <Bar
                  dataKey="receitaRegistrada"
                  name="Receita registrada"
                  stackId="receita"
                  fill="#3f806d"
                  maxBarSize={32}
                />
                <Bar
                  dataKey="receitaComplementar"
                  name="Receita complementar simulada"
                  stackId="receita"
                  fill="#b1d5c9"
                  maxBarSize={32}
                />
                <Bar
                  dataKey="despesaRegistrada"
                  name="Despesa registrada"
                  fill="#c77d31"
                  maxBarSize={32}
                />
                <Line
                  type="linear"
                  dataKey="resultadoMensal"
                  name="Resultado mensal simulado"
                  stroke="#2563eb"
                  strokeDasharray="5 4"
                  dot={false}
                  strokeWidth={2}
                />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
          <div className="fe-legend">
            <span>Verde: receita registrada</span>
            <span>Verde claro: complemento hipotético</span>
            <span>Laranja: despesa registrada</span>
            <span>Azul tracejado: resultado simulado</span>
          </div>
          <h2>Saldo simulado e faixa dos cenários</h2>
          <div className="fe-chart">
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart
                data={faixa}
                margin={{ top: 20, right: 15, left: 4, bottom: 5 }}
              >
                <CartesianGrid stroke="#e4eaf1" />
                <XAxis dataKey="periodo" minTickGap={30} />
                <YAxis
                  width={65}
                  domain={saldo.domain}
                  ticks={saldo.ticks}
                  tickFormatter={compactoFinanceiro}
                />
                <Tooltip
                  formatter={(v) =>
                    Array.isArray(v)
                      ? `${moedaFinanceira(Number(v[0]))} — ${moedaFinanceira(Number(v[1]))}`
                      : moedaFinanceira(Number(v))
                  }
                />
                <ReferenceArea
                  x1={dados.meses[0]?.periodo}
                  x2={ultimo?.periodo}
                  fill="#f1f5fb"
                  fillOpacity={0.5}
                />
                <ReferenceLine y={0} stroke="#94a3b8" />
                <Area
                  type="linear"
                  dataKey="faixa"
                  name="Faixa conservador → otimista"
                  stroke="none"
                  fill="#bfd5f4"
                  fillOpacity={0.6}
                />
                <Line
                  type="linear"
                  dataKey="saldoBase"
                  name="Saldo simulado base"
                  stroke="#153856"
                  strokeDasharray="5 4"
                  strokeWidth={2}
                  dot={false}
                />
                <ReferenceLine
                  x="Início"
                  stroke="#94a3b8"
                  label="Base informada"
                />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
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
      <p className="fe-note">
        Saldo simulado = saldo inicial informado + resultado acumulado simulado.
        O histórico anterior não é acrescentado automaticamente. A faixa varia
        apenas o alvo de receita em ±{dados.variacaoPercentual}%; despesas
        registradas permanecem iguais. É uma análise de sensibilidade, sem
        probabilidade associada. Receita já registrada nunca é reduzida pelo
        cenário. Os registros são pela data de análise; não há separação
        confirmada entre valores pagos e em aberto.
      </p>
    </section>
  );
}
