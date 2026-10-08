import { useState } from "react";
import {
  Bar,
  Cell,
  ComposedChart,
  CartesianGrid,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  brl,
  exportarCSV,
  type DashboardAnalysis,
} from "../services/dashboardAnalysisService";
function escala(dados: DashboardAnalysis["meses"]) {
  const values = dados.flatMap((m) => [m.receita, m.despesa, m.resultado]);
  const min = Math.min(0, ...values),
    max = Math.max(0, ...values);
  const raw = (max - min || 100) / 4;
  const power = 10 ** Math.floor(Math.log10(raw));
  const step = [1, 2, 5, 10].find((n) => n * power >= raw)! * power;
  const baixo = Math.floor(min / step) * step,
    alto = Math.ceil(max / step) * step || step;
  const ticks: number[] = [];
  for (let n = baixo; n <= alto + step / 100; n += step)
    ticks.push(Math.round(n * 100) / 100);
  return { domain: [baixo, alto] as [number, number], ticks };
}
export default function DashboardMonthlyChart({
  analise,
}: {
  analise: DashboardAnalysis;
}) {
  const [tabela, setTabela] = useState(false);
  const axis = escala(analise.meses);
  const csv = () =>
    exportarCSV("painel-mensal.csv", [
      [
        "Mês",
        "Receita BRL",
        "Despesa BRL",
        "Resultado BRL",
        "Mês em andamento",
      ],
      ...analise.meses.map((m) => [
        m.periodo,
        m.receita,
        m.despesa,
        m.resultado,
        m.emAndamento ? "Sim" : "Não",
      ]),
    ]);
  return (
    <article className="executive-panel df-monthly">
      <header>
        <div>
          <span className="executive-eyebrow">
            DESEMPENHO NO PERÍODO APLICADO
          </span>
          <h2>Receita, despesa e resultado</h2>
        </div>
        <div>
          <button type="button" onClick={() => setTabela((v) => !v)}>
            {tabela ? "Ver gráfico" : "Ver como tabela"}
          </button>
          <button type="button" onClick={csv}>
            CSV
          </button>
        </div>
      </header>
      <p>
        Receita e despesa em colunas; resultado em linha. Todos os valores em
        R$.
      </p>
      {analise.indicadores.receita === 0 && (
        <p className="df-note">
          Sem receita lançada no período e categoria selecionados.
        </p>
      )}
      {tabela ? (
        <div className="df-table-scroll">
          <table>
            <thead>
              <tr>
                <th>Mês</th>
                <th>Receita</th>
                <th>Despesa</th>
                <th>Resultado</th>
              </tr>
            </thead>
            <tbody>
              {analise.meses.map((m) => (
                <tr key={m.periodo}>
                  <th>
                    {m.periodo}
                    {m.emAndamento ? " · em andamento" : ""}
                  </th>
                  <td>{brl(m.receita)}</td>
                  <td>{brl(m.despesa)}</td>
                  <td>{brl(m.resultado)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="df-chart">
          <ResponsiveContainer width="100%" height="100%" minWidth={0}>
            <ComposedChart
              data={analise.meses}
              margin={{ top: 16, right: 12, left: 0, bottom: 8 }}
              accessibilityLayer
            >
              <CartesianGrid stroke="#e4eaf2" vertical={false} />
              <XAxis
                dataKey="periodo"
                tickLine={false}
                minTickGap={24}
                tick={{ fontSize: 11 }}
              />
              <YAxis
                domain={axis.domain}
                ticks={axis.ticks}
                width={64}
                tickLine={false}
                tick={{ fontSize: 11 }}
                tickFormatter={(v) =>
                  new Intl.NumberFormat("pt-BR", {
                    notation: "compact",
                  }).format(Number(v))
                }
              />
              <ReferenceLine y={0} stroke="#96a6bc" />
              <Tooltip formatter={(value) => brl(Number(value))} />
              <Bar
                dataKey="receita"
                name="Receita"
                fill="#3e806c"
                maxBarSize={44}
              >
                {analise.meses.map((m) => (
                  <Cell
                    key={m.periodo}
                    fillOpacity={m.emAndamento ? 0.55 : 1}
                  />
                ))}
              </Bar>
              <Bar
                dataKey="despesa"
                name="Despesa"
                fill="#c47c32"
                maxBarSize={44}
              >
                {analise.meses.map((m) => (
                  <Cell
                    key={m.periodo}
                    fillOpacity={m.emAndamento ? 0.55 : 1}
                  />
                ))}
              </Bar>
              <Line
                type="linear"
                dataKey="resultado"
                name="Resultado"
                stroke="#3869c8"
                strokeWidth={2}
                dot={{ r: 3 }}
                isAnimationActive={false}
              />
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      )}
      <p className="df-note">
        Verde: receita · Laranja: despesa · Azul: resultado. Colunas claras
        indicam mês em andamento, não dados completos. Use o atalho de seis
        meses para ampliar o intervalo.
      </p>
    </article>
  );
}
