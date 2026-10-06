import { useState } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { HistoryData } from "../services/historyService";
import "../styles/history.css";

const series = [
  { key: "receita", nome: "Receita", cor: "#3e806c" },
  { key: "despesa", nome: "Despesa", cor: "#c47c32" },
  { key: "resultado", nome: "Resultado", cor: "#3869c8" },
] as const;
export default function HistoryChart({ dados }: { dados: HistoryData[] }) {
  const [visiveis, setVisiveis] = useState<string[]>(series.map((s) => s.key));
  function alternar(chave: string) {
    setVisiveis((atual) =>
      atual.includes(chave)
        ? atual.length === 1
          ? atual
          : atual.filter((c) => c !== chave)
        : [...atual, chave],
    );
  }
  return (
    <section className="fv-history-panel" aria-labelledby="history-chart-title">
      <div className="fv-history-panel-heading">
        <div>
          <h2 id="history-chart-title">Evolução por mês</h2>
          <p>Compare receitas, despesas e resultado no período exibido.</p>
        </div>
        <span className="fv-history-badge">{dados.length} meses</span>
      </div>
      <div className="fv-history-legend" aria-label="Séries do gráfico">
        {series.map((s) => (
          <button
            type="button"
            key={s.key}
            aria-pressed={visiveis.includes(s.key)}
            onClick={() => alternar(s.key)}
          >
            <span style={{ background: s.cor }} aria-hidden="true" />
            {s.nome}
          </button>
        ))}
      </div>
      <p className="fv-history-note">
        Selecione as séries acima. Pelo menos uma permanece visível. Os valores
        também estão na lista mensal abaixo.
      </p>
      <div
        className="fv-history-plot"
        role="group"
        aria-label="Gráfico de evolução financeira mensal"
      >
        <ResponsiveContainer width="100%" height="100%" minWidth={0}>
          <LineChart
            data={dados}
            margin={{ top: 16, right: 16, left: 0, bottom: 12 }}
            accessibilityLayer
          >
            <CartesianGrid
              stroke="#e4eaf2"
              strokeDasharray="4 4"
              vertical={false}
            />
            <XAxis
              dataKey="periodo"
              tick={{ fontSize: 11, fill: "#64748b" }}
              minTickGap={32}
              tickLine={false}
              axisLine={false}
            />
            <YAxis
              width={64}
              tick={{ fontSize: 11, fill: "#64748b" }}
              tickLine={false}
              axisLine={false}
              tickFormatter={(v) =>
                new Intl.NumberFormat("pt-BR", { notation: "compact" }).format(
                  Number(v),
                )
              }
            />
            <ReferenceLine y={0} stroke="#aab8cb" />
            <Tooltip
              contentStyle={{
                borderRadius: 12,
                borderColor: "#dce5f1",
                fontSize: 13,
              }}
              formatter={(v) =>
                Number(v).toLocaleString("pt-BR", {
                  style: "currency",
                  currency: "BRL",
                })
              }
            />
            {series.map((s) => (
              <Line
                key={s.key}
                hide={!visiveis.includes(s.key)}
                type="linear"
                dataKey={s.key}
                name={s.nome}
                stroke={s.cor}
                strokeWidth={2.5}
                dot={dados.length <= 24 ? { r: 3, strokeWidth: 2 } : false}
                activeDot={{ r: 5 }}
                isAnimationActive={false}
              />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </section>
  );
}
