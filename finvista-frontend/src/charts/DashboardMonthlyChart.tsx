import { FinancialChartPaint } from "../components/FinancialChartPaint";
import { useChartAppearance } from "../services/chartAppearance";
import { useId, useState } from "react";
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
import "../styles/monthly-receipts.css";

function escala(dados: DashboardAnalysis["meses"], recebimentos = false) {
  const values = dados.flatMap((m) =>
    recebimentos
      ? [m.receita, m.despesa, m.recebimentos?.semDetalhamento ?? m.receita]
      : [m.receita, m.despesa, m.resultado],
  );
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
  const appearance = useChartAppearance();
  const [tabela, setTabela] = useState(false);
  const [modo, setModo] = useState<"RECEBIMENTOS" | "RESULTADO">(
    "RECEBIMENTOS",
  );
  const padrao = `receipt-${useId().replace(/:/g, "")}`;
  const detalhar = modo === "RECEBIMENTOS";
  const dados = analise.meses.map((m) => {
    const r = m.recebimentos;
    const valido =
      r &&
      [
        r.recebido,
        r.emAberto,
        r.semDetalhamento,
        r.registrosSemDetalhamento,
      ].every(Number.isFinite) &&
      r.recebido >= 0 &&
      r.emAberto >= 0 &&
      r.registrosSemDetalhamento >= 0 &&
      Math.abs(r.recebido + r.emAberto + r.semDetalhamento - m.receita) < 0.005;
    return {
      ...m,
      recebido: valido ? r.recebido : 0,
      emAberto: valido ? r.emAberto : 0,
      semDetalhamento: valido ? r.semDetalhamento : m.receita,
      incompletos: valido ? r.registrosSemDetalhamento : null,
      detalheValido: Boolean(valido),
    };
  });
  const parcial = dados.some(
    (m) => !m.detalheValido || (m.incompletos ?? 0) > 0,
  );

  const axis = escala(analise.meses, detalhar);
  const csv = () =>
    exportarCSV("painel-mensal.csv", [
      [
        "Mês",
        "Receita BRL",
        "Despesa BRL",
        "Resultado BRL",
        "Mês em andamento",
        "Principal recebido conhecido",
        "Principal em aberto conhecido",
        "Receita sem detalhamento",
        "Registros sem detalhamento",
      ],
      ...dados.map((m) => [
        m.periodo,
        m.receita,
        m.despesa,
        m.resultado,
        m.emAndamento ? "Sim" : "Não",
        m.detalheValido ? m.recebido : "Não informado",
        m.detalheValido ? m.emAberto : "Não informado",
        m.semDetalhamento,
        m.incompletos ?? "Não informado",
      ]),
    ]);
  return (
    <article className="executive-panel df-monthly">
      <FinancialChartPaint id={appearance.id} />
      <header>
        <div>
          <span className="executive-eyebrow">
            DESEMPENHO NO PERÍODO APLICADO
          </span>
          <h2>
            {detalhar
              ? "Receita recebida e em aberto × despesa"
              : "Receita, despesa e resultado"}
          </h2>
        </div>
        <div>
          <button
            type="button"
            onClick={() => setModo(detalhar ? "RESULTADO" : "RECEBIMENTOS")}
          >
            {detalhar ? "Ver resultado" : "Ver recebimentos"}
          </button>
          <button type="button" onClick={() => setTabela((v) => !v)}>
            {tabela ? "Ver gráfico" : "Ver como tabela"}
          </button>
          <button type="button" onClick={csv}>
            CSV
          </button>
        </div>
      </header>
      <p>
        {detalhar
          ? "Recebido e em aberto conforme o critério e período selecionados, ao lado da despesa. Valores em R$."
          : "Receita e despesa em colunas; resultado em linha. Todos os valores em R$."}
      </p>
      {detalhar && parcial && (
        <p className="mr-warning" role="status">
          Detalhamento parcial: valores sem divisão conferida entre recebido e
          em aberto aparecem em cinza, sem serem convertidos em zero recebido.
        </p>
      )}
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
                <th>Receita registrada</th>
                {detalhar && (
                  <>
                    <th>Recebido (valor conhecido)</th>
                    <th>Em aberto (valor conhecido)</th>
                    <th>Sem detalhamento</th>
                  </>
                )}
                <th>Despesa</th>
                <th>Resultado</th>
              </tr>
            </thead>
            <tbody>
              {dados.map((m) => (
                <tr key={m.periodo}>
                  <th>
                    {m.periodo}
                    {m.emAndamento ? " · em andamento" : ""}
                  </th>
                  <td>{brl(m.receita)}</td>
                  {detalhar && (
                    <>
                      <td>
                        {m.detalheValido ? brl(m.recebido) : "Não informado"}
                      </td>
                      <td>
                        {m.detalheValido ? brl(m.emAberto) : "Não informado"}
                      </td>
                      <td>
                        {brl(m.semDetalhamento)}
                        {m.incompletos != null
                          ? ` · ${m.incompletos} registro(s)`
                          : ""}
                      </td>
                    </>
                  )}
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
              data={dados}
              stackOffset="sign"
              margin={{ top: 16, right: 12, left: 0, bottom: 8 }}
              accessibilityLayer
            >
              <defs>
                <pattern
                  id={padrao}
                  width="7"
                  height="7"
                  patternUnits="userSpaceOnUse"
                >
                  <rect width="7" height="7" fill="#dceafe" />
                  <path
                    d="M-2 2L2-2M0 7L7 0M5 9L9 5"
                    stroke="#2e75d3"
                    strokeWidth="1.5"
                  />
                </pattern>
              </defs>
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
              {detalhar ? (
                <>
                  <Bar
                    dataKey="recebido"
                    stackId="receita"
                    name="Recebido (valor conhecido)"
                    fill={appearance.fill("#2e75d3")}
                    maxBarSize={44}
                    isAnimationActive={false}
                  ></Bar>
                  <Bar
                    dataKey="emAberto"
                    stackId="receita"
                    name="Em aberto (valor conhecido)"
                    fill={`url(#${padrao})`}
                    stroke="#2e75d3"
                    maxBarSize={44}
                    isAnimationActive={false}
                  />
                  <Bar
                    dataKey="semDetalhamento"
                    stackId="receita"
                    name="Receita sem detalhamento"
                    fill={appearance.fill("#9aa8b7")}
                    maxBarSize={44}
                    isAnimationActive={false}
                  />
                </>
              ) : (
                <Bar
                  isAnimationActive={false}
                  dataKey="receita"
                  name="Receita"
                  fill={appearance.fill("#2e75d3")}
                  maxBarSize={44}
                >
                  {analise.meses.map((m) => (
                    <Cell
                      key={m.periodo}
                      fillOpacity={m.emAndamento ? 0.55 : 1}
                    />
                  ))}
                </Bar>
              )}
              <Bar
                isAnimationActive={false}
                dataKey="despesa"
                name="Despesa"
                fill={appearance.fill("#c47c32")}
                maxBarSize={44}
              >
                {analise.meses.map((m) => (
                  <Cell
                    key={m.periodo}
                    fillOpacity={m.emAndamento ? 0.55 : 1}
                  />
                ))}
              </Bar>
              {!detalhar && (
                <Line
                  type="linear"
                  dataKey="resultado"
                  name="Resultado"
                  stroke="var(--fv-chart-success)"
                  strokeWidth={2}
                  dot={{ r: 3 }}
                  isAnimationActive={false}
                />
              )}
            </ComposedChart>
          </ResponsiveContainer>
        </div>
      )}
      {detalhar ? (
        <>
          <div className="mr-legend">
            <span>
              <i className="mr-paid" />
              Recebido
            </span>
            <span>
              <i className="mr-open" />
              Em aberto
            </span>
            <span>
              <i className="mr-unknown" />
              Sem detalhamento
            </span>
            <span>
              <i className="mr-expense" />
              Despesa registrada
            </span>
          </div>
          {dados.some(
            (m) => m.detalheValido && m.recebido === 0 && m.receita > 0,
          ) && (
            <p className="mr-zero">
              {dados
                .filter(
                  (m) => m.detalheValido && m.recebido === 0 && m.receita > 0,
                )
                .map(
                  (m) =>
                    `${m.periodo}: R$ 0 de valor recebido conhecido; ${brl(m.emAberto)} de valor em aberto conhecido.`,
                )
                .join(" ")}
            </p>
          )}
          <p className="df-note">
            Com cobertura parcial, os valores conhecidos não representam toda a
            receita. A legenda permanece visível mesmo com séries zeradas.
          </p>
          <details className="mr-method">
            <summary>Como interpretar este gráfico</summary>
            <p>
              Competência usa o principal na data do evento. Vencimento usa o
              líquido pago e aberto na data de vencimento. Baixa usa os
              pagamentos/recebimentos efetivos da fonte selecionada. No regime
              de competência ou vencimento, a divisão entre recebido e em aberto
              é a posição atual disponível, não uma reconstrução histórica do
              fechamento de cada mês. Valores sem detalhamento permanecem
              sinalizados.
            </p>
            <p>
              Se os campos não forem informados, não fecharem com a receita ou o
              filtro selecionar só parte do rateio, o valor selecionado fica em
              “Sem detalhamento”. Não distribuímos recebimentos por categoria
              usando estimativas. O período é limitado ao filtro aplicado; use
              “Ver seis meses” para ampliar.
            </p>
          </details>
        </>
      ) : (
        <p className="df-note">
          Azul: receita · Laranja: despesa · Verde: resultado. Colunas claras
          indicam mês em andamento, não dados completos. Use o atalho de seis
          meses para ampliar o intervalo.
        </p>
      )}
    </article>
  );
}
