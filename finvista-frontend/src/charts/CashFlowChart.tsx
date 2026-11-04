import { useState } from "react";
import {
  Bar,
  Brush,
  CartesianGrid,
  ComposedChart,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { FinancialChartPaint } from "../components/FinancialChartPaint";
import { useChartAppearance } from "../services/chartAppearance";
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
  filtroAplicado?: boolean;
  criterioLabel?: string;
  realizado?: boolean;
  dados: CashFlowData[];
  periodo: PeriodoFluxoCaixa;
  carregando: boolean;
  onPeriodoChange: (periodo: PeriodoFluxoCaixa) => void;
}

export default function CashFlowChart({
  filtroAplicado = false,
  criterioLabel,
  realizado = false,
  dados,
  periodo,
  carregando,
  onPeriodoChange,
}: Props) {
  const paint = useChartAppearance();
  const [historico, setHistorico] = useState(false);
  const [linhaMensal, setLinhaMensal] = useState(false);
  const [pagina, setPagina] = useState(0);
  const meses = prepararEvolucao(dados).map((m) => ({
    ...m,
    pagamentoVisual: -m.saidas,
  }));
  const entradas = dados.reduce((s, m) => s + m.entradas, 0);
  const saidas = dados.reduce((s, m) => s + m.saidas, 0);
  const base = dados[0]?.saldoInicial ?? 0;
  const ultimo = meses.at(-1);
  const lineKey = linhaMensal
    ? "resultado"
    : historico
      ? "acumuladoHistorico"
      : "acumuladoPeriodo";
  const lineName = linhaMensal
    ? "Resultado mensal"
    : historico
      ? "Acumulado com histórico"
      : "Movimento líquido acumulado no recorte";
  const eixo = eixoFinanceiro(
    meses.flatMap((m) => [m.entradas, m.pagamentoVisual, m[lineKey]]),
  );
  const recebimentos = realizado ? "Recebimentos" : "Receitas registradas";
  const pagamentos = realizado ? "Pagamentos" : "Despesas registradas";
  const page = Math.min(pagina, Math.max(0, Math.ceil(meses.length / 24) - 1));
  const exportar = () =>
    exportarFinanceiro("fluxo-financeiro-mensal.csv", [
      [
        "Mês",
        recebimentos,
        pagamentos,
        "Resultado mensal",
        "Acumulado no recorte",
        "Acumulado com histórico",
        "Base anterior do mês",
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
  const atual = mesAtualSaoPaulo();
  return (
    <section
      className="fe-card cf-overview"
      aria-labelledby="cf-title"
      aria-busy={carregando}
    >
      <FinancialChartPaint id={paint.id} />
      <div className="fe-heading">
        <div>
          <span className="fe-eyebrow">Fluxo de caixa</span>
          <h1 id="cf-title">
            {realizado ? "Recebimentos e pagamentos" : "Evolução financeira"}
          </h1>
          <p>
            {criterioLabel ??
              (realizado
                ? "Baixas financeiras conferidas"
                : "Competência")}{" "}
            · Agrupamento mensal · Valores em R$
          </p>
        </div>
        <div
          className="fe-actions"
          role="group"
          aria-label="Atalhos de período"
        >
          {([6, 12, "todos"] as const).map((p) => (
            <button
              key={p}
              type="button"
              disabled={carregando}
              aria-pressed={!filtroAplicado && periodo === p}
              onClick={() => {
                setPagina(0);
                onPeriodoChange(p);
              }}
            >
              {p === "todos" ? "Todo o período" : `${p} meses`}
            </button>
          ))}
        </div>
      </div>
      {carregando ? (
        <p role="status">Atualizando fluxo…</p>
      ) : !dados.length ? (
        <p role="status">Nenhum registro disponível neste recorte.</p>
      ) : (
        <>
          <div className="fe-summary">
            <div>
              {recebimentos}
              <strong>{moedaFinanceira(entradas)}</strong>
            </div>
            <div>
              {pagamentos}
              <strong>{moedaFinanceira(saidas)}</strong>
            </div>
            <div>
              {realizado
                ? "Movimento líquido no recorte"
                : "Resultado no recorte"}
              <strong className={entradas - saidas < 0 ? "cf-negative" : ""}>
                {moedaFinanceira(entradas - saidas)}
              </strong>
            </div>
          </div>
          <div className="cf-toolbar">
            <p className="fe-note">
              {dados[0].mes} — {dados.at(-1)?.mes}. Os meses das extremidades
              podem estar parciais.
            </p>
            <div className="fe-actions">
              <label className="fe-check">
                <input
                  type="checkbox"
                  checked={linhaMensal}
                  onChange={(e) => setLinhaMensal(e.target.checked)}
                />
                Linha de resultado mensal
              </label>
              <label className="fe-check">
                <input
                  type="checkbox"
                  checked={historico}
                  disabled={linhaMensal}
                  onChange={(e) => setHistorico(e.target.checked)}
                />
                Incluir histórico na linha
              </label>
              <button type="button" onClick={exportar}>
                CSV completo
              </button>
            </div>
          </div>
          <div
            className="cf-chart"
            role="img"
            aria-label={`${recebimentos} em colunas positivas, ${pagamentos.toLowerCase()} em colunas negativas e ${lineName.toLowerCase()} em linha. Valores disponíveis na tabela abaixo.`}
          >
            <ResponsiveContainer width="100%" height="100%" minWidth={0}>
              <ComposedChart
                data={meses}
                margin={{ top: 20, right: 12, left: 0, bottom: 4 }}
                accessibilityLayer
              >
                <CartesianGrid
                  stroke="var(--fv-dark-border, #dbe4ef)"
                  vertical={false}
                  strokeDasharray="3 4"
                />
                <XAxis
                  dataKey="mes"
                  minTickGap={25}
                  tick={{ fill: "var(--fv-dark-muted, #64748b)", fontSize: 11 }}
                />
                <YAxis
                  width={62}
                  domain={eixo.domain}
                  ticks={eixo.ticks}
                  tickFormatter={compactoFinanceiro}
                  tick={{ fill: "var(--fv-dark-muted, #64748b)", fontSize: 11 }}
                />
                <Tooltip
                  contentStyle={{
                    background: "var(--fv-dark-surface, #fff)",
                    borderColor: "var(--fv-dark-border, #dbe4ef)",
                    borderRadius: 10,
                    color: "var(--fv-dark-text, #153856)",
                  }}
                  formatter={(value, name) => [
                    moedaFinanceira(
                      name === pagamentos ? -Number(value) : Number(value),
                    ),
                    name,
                  ]}
                />
                <ReferenceLine y={0} stroke="var(--fv-dark-muted, #64748b)" />
                <Bar
                  dataKey="entradas"
                  name={recebimentos}
                  fill={paint.fill("#3786dc")}
                  radius={[3, 3, 0, 0]}
                  maxBarSize={32}
                  isAnimationActive={false}
                />
                <Bar
                  dataKey="pagamentoVisual"
                  name={pagamentos}
                  fill={paint.fill("#e58a2f")}
                  maxBarSize={32}
                  isAnimationActive={false}
                />
                <Line
                  type="linear"
                  dataKey={lineKey}
                  name={lineName}
                  stroke="var(--fv-chart-blue, #3786dc)"
                  strokeWidth={2.5}
                  dot={meses.length <= 18 ? { r: 3 } : false}
                  activeDot={{ r: 5 }}
                  isAnimationActive={false}
                />
                {meses.length > 18 && (
                  <Brush
                    dataKey="mes"
                    startIndex={Math.max(0, meses.length - 18)}
                    endIndex={meses.length - 1}
                    height={24}
                    stroke="var(--fv-chart-blue, #3786dc)"
                    fill="var(--fv-dark-surface, #fff)"
                  />
                )}
              </ComposedChart>
            </ResponsiveContainer>
          </div>
          {meses.length > 18 && (
            <p className="fe-note">
              O gráfico abre nos últimos 18 meses. Amplie o zoom pelas alças na
              base; a tabela e o CSV mantêm todo o recorte.
            </p>
          )}
          <div className="cf-legend">
            <span>
              <i className="cf-key incoming" aria-hidden="true">
                ▲
              </i>
              {recebimentos}
            </span>
            <span>
              <i className="cf-key outgoing" aria-hidden="true">
                ▼
              </i>
              {pagamentos}
            </span>
            <span>
              <i className="cf-key result-line" aria-hidden="true">
                ━
              </i>
              {lineName}
            </span>
          </div>
          <p className="fe-note">
            Colunas abaixo do zero indicam saídas; a tabela preserva o valor dos
            pagamentos. A linha não é saldo bancário: não inclui transferências
            nem abertura de conta.{" "}
            {realizado
              ? "Os valores seguem a fonte de baixas selecionada, sem somar API e extrato."
              : "Competência e vencimento podem incluir compromissos em aberto; não são pagamentos efetivos."}{" "}
            {meses.some((m) => periodoISO(m.mes) >= atual) &&
              "O mês atual ou futuro pode estar incompleto; não representa previsão automática."}
          </p>
          <p className="fe-note cf-scroll-hint">
            Tabela completa abaixo. Em telas menores, role horizontalmente para
            consultar todas as colunas.
          </p>
          <div
            className="fe-table cf-table"
            tabIndex={0}
            role="region"
            aria-label="Tabela mensal do fluxo, role para ver todas as colunas"
          >
            <table>
              <caption>
                Valores do mesmo recorte do gráfico · {meses.length} meses
              </caption>
              <thead>
                <tr>
                  <th scope="col">Mês</th>
                  <th scope="col">{recebimentos}</th>
                  <th scope="col">{pagamentos}</th>
                  <th scope="col">Resultado mensal</th>
                  <th scope="col">Acumulado no recorte</th>
                  <th scope="col">Com histórico</th>
                </tr>
              </thead>
              <tbody>
                {meses.slice(page * 24, (page + 1) * 24).map((m) => (
                  <tr key={m.mes}>
                    <th scope="row">{m.mes}</th>
                    <td>{moedaFinanceira(m.entradas)}</td>
                    <td>{moedaFinanceira(m.saidas)}</td>
                    <td>{moedaFinanceira(m.resultado)}</td>
                    <td>{moedaFinanceira(m.acumuladoPeriodo)}</td>
                    <td>{moedaFinanceira(m.acumuladoHistorico)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th scope="row">Total do recorte</th>
                  <td>{moedaFinanceira(entradas)}</td>
                  <td>{moedaFinanceira(saidas)}</td>
                  <td>{moedaFinanceira(entradas - saidas)}</td>
                  <td>{moedaFinanceira(ultimo?.acumuladoPeriodo ?? 0)}</td>
                  <td>{moedaFinanceira(ultimo?.acumuladoHistorico ?? 0)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
          {meses.length > 24 && (
            <div className="cf-pagination">
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPagina(page - 1)}
              >
                Anterior
              </button>
              <span>
                Página {page + 1} de {Math.ceil(meses.length / 24)}
              </span>
              <button
                type="button"
                disabled={(page + 1) * 24 >= meses.length}
                onClick={() => setPagina(page + 1)}
              >
                Próxima
              </button>
            </div>
          )}
          <details className="cf-details">
            <summary>Entender as bases do acumulado</summary>
            <p>
              O acumulado no recorte parte de zero. Com histórico inclui o
              resultado anterior retornado para os mesmos filtros:{" "}
              {moedaFinanceira(base)}. Nenhum deles certifica saldo bancário.
              Ausência de registros não comprova ausência de movimento.
            </p>
          </details>
        </>
      )}
    </section>
  );
}
