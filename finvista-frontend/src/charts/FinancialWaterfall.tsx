import { useId, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { DashboardAnalysis } from "../services/dashboardAnalysisService";
import { prepararCascata } from "../services/analysisChartData";
import {
  eixoFinanceiro,
  exportarFinanceiro,
  moedaFinanceira,
  compactoFinanceiro,
} from "../services/financialVisualization";
import "../styles/financial-waterfall.css";
export default function FinancialWaterfall({
  analise,
  onCategoria,
}: {
  analise: DashboardAnalysis;
  onCategoria: (nome: string) => void;
}) {
  const [todas, setTodas] = useState(false);
  const id = useId();
  const anterior = analise.indicadores.despesaMesAnterior ?? 0,
    atual = analise.indicadores.despesa;
  const cascata = prepararCascata(anterior, atual, analise.variacoes, todas);
  const barras = cascata.barras.map((b) => ({
    ...b,
    texto: `${!b.total && b.valor > 0 ? "+" : ""}${moedaFinanceira(b.valor)}`,
  }));
  const eixo = eixoFinanceiro(barras.flatMap((b) => b.intervalo));
  const data = (s: string) => s.split("-").reverse().join("/");
  return (
    <section
      className="executive-panel wf-panel"
      aria-labelledby={`wf-title-${id}`}
    >
      <div className="wf-heading">
        <div>
          <h2 id={`wf-title-${id}`}>O que mudou nas despesas?</h2>
          <p>
            {data(analise.inicioAnterior)} — {data(analise.fimAnterior)} →{" "}
            {data(analise.inicio)} — {data(analise.fim)} ·{" "}
            {analise.categoria || "Todas as categorias"}
          </p>
          <p>
            De {moedaFinanceira(anterior)} para {moedaFinanceira(atual)}.
            Diferença: {moedaFinanceira(atual - anterior)}.
          </p>
        </div>
        <div className="wf-actions">
          <button type="button" onClick={() => setTodas(!todas)}>
            {todas ? "Agrupar menores mudanças" : "Abrir todas as mudanças"}
          </button>
          <button
            type="button"
            onClick={() =>
              exportarFinanceiro("variacoes-despesas.csv", [
                ["Categoria", "Anterior", "Atual", "Diferença"],
                ...analise.variacoes.map((v) => [
                  v.categoria,
                  v.anterior,
                  v.atual,
                  v.diferenca,
                ]),
              ])
            }
          >
            CSV
          </button>
          {analise.categoria && (
            <button type="button" onClick={() => onCategoria("")}>
              Limpar categoria
            </button>
          )}
        </div>
      </div>
      {!cascata.conciliado ? (
        <p role="alert">
          Os dados da comparação não conciliam com o total. Confira o
          detalhamento antes de usar a cascata.
        </p>
      ) : (
        <div
          className="wf-scroll"
          tabIndex={0}
          aria-label="Cascata de despesas, com rolagem horizontal"
        >
          <div
            className="wf-chart"
            style={{ minWidth: Math.max(400, barras.length * 155) }}
          >
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={barras}
                margin={{ top: 35, right: 20, left: 5, bottom: 10 }}
              >
                <CartesianGrid vertical={false} stroke="#e4eaf1" />
                <XAxis
                  dataKey="nome"
                  height={65}
                  interval={0}
                  tick={{ fontSize: 10, fill: "#536c86" }}
                  tickFormatter={(v) =>
                    String(v).length > 22
                      ? String(v).slice(0, 21) + "…"
                      : String(v)
                  }
                />
                <YAxis
                  width={70}
                  domain={eixo.domain}
                  ticks={eixo.ticks}
                  tickFormatter={compactoFinanceiro}
                  tick={{ fontSize: 11 }}
                />
                <ReferenceLine y={0} stroke="#94a3b8" />
                <Tooltip
                  formatter={(_v, _n, p) => {
                    const b = p.payload as { valor: number; total: boolean };
                    return [
                      moedaFinanceira(b.valor),
                      b.total ? "Total" : "Variação",
                    ];
                  }}
                />
                <Bar
                  dataKey="intervalo"
                  name="Despesa"
                  maxBarSize={64}
                  onClick={(_entry, index) => {
                    const b = barras[index];
                    if (b?.categoria)
                      onCategoria(
                        analise.categoria === b.categoria ? "" : b.categoria,
                      );
                  }}
                >
                  {barras.map((b, i) => (
                    <Cell
                      key={`${b.nome}-${i}`}
                      fill={
                        b.total
                          ? "#153856"
                          : b.delta >= 0
                            ? "#c77d31"
                            : "#3f806d"
                      }
                      cursor={b.categoria ? "pointer" : "default"}
                    />
                  ))}
                  <LabelList
                    dataKey="texto"
                    position="top"
                    fill="#153856"
                    fontSize={10}
                  />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      )}
      <p className="wf-note">
        Azul: totais · laranja: aumento de despesas · verde: redução. A
        sequência segue as maiores mudanças em valor absoluto, não a ordem
        cronológica. Clique numa categoria para aplicar o filtro ao painel.
        “Outras” preserva a soma das categorias agrupadas.
      </p>
      <details>
        <summary>
          Ver todas as categorias e valores ({analise.variacoes.length})
        </summary>
        <div className="wf-table">
          <table>
            <thead>
              <tr>
                <th>Categoria</th>
                <th>Anterior</th>
                <th>Atual</th>
                <th>Diferença</th>
              </tr>
            </thead>
            <tbody>
              {analise.variacoes.map((v) => (
                <tr key={v.categoria}>
                  <th>
                    <button
                      type="button"
                      onClick={() =>
                        onCategoria(
                          analise.categoria === v.categoria ? "" : v.categoria,
                        )
                      }
                    >
                      {v.categoria}
                    </button>
                  </th>
                  <td>{moedaFinanceira(v.anterior)}</td>
                  <td>{moedaFinanceira(v.atual)}</td>
                  <td>{moedaFinanceira(v.diferenca)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}
