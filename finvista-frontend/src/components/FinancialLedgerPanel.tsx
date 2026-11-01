import { useEffect, useState } from "react";
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
import { API_URL } from "../config/api";
import {
  getFinancialView,
  todayInBrazil,
  type ViewFilter,
} from "../services/financialViewService";
import { brl, exportarCSV } from "../services/dashboardAnalysisService";
import {
  eixoFinanceiro,
  compactoFinanceiro,
} from "../services/financialVisualization";
import { FinancialChartPaint } from "./FinancialChartPaint";
import { useChartAppearance } from "../services/chartAppearance";
type Account = {
  id: string;
  nome: string;
  saldoInicial: number;
  recebimentos: number;
  pagamentos: number;
  transferenciasEntrada: number;
  transferenciasSaida: number;
  ajustesAbertura: number;
  saldoFinal: number;
  semSaldoInicial: boolean;
};
type Move = {
  id: string;
  origem: string;
  tipo: string;
  data: string;
  contaId: string;
  conta: string;
  valor: number;
};
type Ledger = {
  meses: {
    mes: string;
    saldoInicial: number;
    entradas: number;
    saidas: number;
    saldoFinal: number;
  }[];
  clienteId: number;
  inicio: string;
  fim: string;
  execucaoId: string | null;
  capturadoEm: string | null;
  contas: Account[];
  movimentos: Move[];
  parcial: boolean;
  verificacao?: {
    detalhesConferidos: boolean;
    saldoContas: number;
    saldoMensal: number;
    coberturaConfirmada: boolean;
  };
  avisos: string[];
};
const cents = (n: number) => Math.round(Number(n) * 100);
const dateBR = (s: string) => s.split("-").reverse().join("/");
const accountName = (a: Account) =>
  a.nome === a.id ? "Nome da conta não informado" : a.nome;
const movementName = (m: Move) =>
  m.tipo === "RECEITA"
    ? "Recebimento"
    : m.tipo === "DESPESA"
      ? "Pagamento"
      : m.tipo === "SALDO_INICIAL"
        ? "Abertura"
        : m.tipo === "TRANSFERENCIA"
          ? m.valor < 0
            ? "Transferência de saída"
            : "Transferência de entrada"
          : m.tipo;

function ledgerRows(data: Ledger, conta: string, diario: boolean) {
  const accounts = data.contas.filter((a) => !conta || a.id === conta);
  const moves = data.movimentos.filter((m) => !conta || m.contaId === conta);
  const rows = new Map<
    string,
    {
      periodo: string;
      recebido: number;
      pago: number;
      transferenciaEntrada: number;
      transferenciaSaida: number;
      abertura: number;
      liquido: number;
      saldo: number;
      movimentos: Move[];
    }
  >();
  const add = (key: string) => {
    if (!rows.has(key))
      rows.set(key, {
        periodo: key,
        recebido: 0,
        pago: 0,
        transferenciaEntrada: 0,
        transferenciaSaida: 0,
        abertura: 0,
        liquido: 0,
        saldo: 0,
        movimentos: [],
      });
    return rows.get(key)!;
  };
  const start = new Date(data.inicio + "T12:00:00Z"),
    end = new Date(data.fim + "T12:00:00Z");
  if (
    !Number.isFinite(start.getTime()) ||
    !Number.isFinite(end.getTime()) ||
    end < start
  )
    throw new Error("Intervalo de caixa inválido.");
  if (diario && (end.getTime() - start.getTime()) / 86400000 > 3660)
    throw new Error("Selecione até dez anos para visualizar por dia.");
  if (!diario) start.setUTCDate(1);
  for (
    const d = start;
    d <= end;
    diario
      ? d.setUTCDate(d.getUTCDate() + 1)
      : d.setUTCMonth(d.getUTCMonth() + 1)
  )
    add(d.toISOString().slice(0, diario ? 10 : 7));
  for (const m of moves) {
    if (
      m.data < data.inicio ||
      m.data > data.fim ||
      !Number.isFinite(Number(m.valor))
    )
      throw new Error("Movimentação incompatível com o recorte.");
    const row = add(m.data.slice(0, diario ? 10 : 7)),
      v = cents(m.valor);
    row.liquido += v;
    if (m.tipo === "RECEITA") row.recebido += v;
    else if (m.tipo === "DESPESA") row.pago -= v;
    else if (m.tipo === "SALDO_INICIAL") row.abertura += v;
    else if (m.tipo === "TRANSFERENCIA") {
      if (v >= 0) row.transferenciaEntrada += v;
      else row.transferenciaSaida -= v;
    } else
      throw new Error(
        "Tipo de movimento não reconhecido. Revise a atualização do servidor.",
      );
    row.movimentos.push(m);
  }
  let saldo = accounts.reduce((s, a) => s + cents(a.saldoInicial), 0);
  return [...rows.values()]
    .sort((a, b) => a.periodo.localeCompare(b.periodo))
    .map((r) => {
      saldo += r.liquido;
      return {
        ...r,
        recebido: r.recebido / 100,
        pago: r.pago / 100,
        transferenciaEntrada: r.transferenciaEntrada / 100,
        transferenciaSaida: r.transferenciaSaida / 100,
        abertura: r.abertura / 100,
        liquido: r.liquido / 100,
        saldo: saldo / 100,
        pagamentoVisual: -r.pago / 100,
        transferenciaSaidaVisual: -r.transferenciaSaida / 100,
      };
    });
}

export default function FinancialLedgerPanel({
  filter,
  revision = 0,
}: {
  filter: ViewFilter;
  revision?: number;
}) {
  const [response, setResponse] = useState<{
    key: string;
    data: Ledger | null;
    error: string;
  } | null>(null);
  const [conta, setConta] = useState("");
  const [agrupamento, setAgrupamento] = useState<"diario" | "mensal">("mensal");
  const [pagina, setPagina] = useState(0);
  const paint = useChartAppearance();
  const requestKey = JSON.stringify([filter, revision]);
  const data = response?.key === requestKey ? response.data : null;
  const error = response?.key === requestKey ? response.error : "";
  useEffect(() => {
    const c = new AbortController();
    void (async () => {
      try {
        const range = await getFinancialView(
          { ...filter, categoria: "", centro: "" },
          c.signal,
        );
        const q = new URLSearchParams(range.intervalo);
        const r = await fetch(`${API_URL}/api/analises/financeira/caixa?${q}`, {
          credentials: "include",
          cache: "no-store",
          signal: c.signal,
        });
        if (!r.ok)
          throw new Error("Não foi possível calcular os movimentos por conta.");
        const next: Ledger = await r.json();
        if (
          next.clienteId !== range.clienteId ||
          (filter.clienteId != null && next.clienteId !== filter.clienteId) ||
          next.inicio !== range.intervalo.inicio ||
          next.fim !== range.intervalo.fim
        )
          throw new Error("A empresa ou período mudou. Consulte novamente.");
        if (
          !Array.isArray(next.contas) ||
          !Array.isArray(next.movimentos) ||
          !Array.isArray(next.avisos)
        )
          throw new Error("Resposta de caixa incompatível.");
        if (!c.signal.aborted)
          setResponse({ key: requestKey, data: next, error: "" });
      } catch (e) {
        if (!c.signal.aborted)
          setResponse({
            key: requestKey,
            data: null,
            error: e instanceof Error ? e.message : "Falha no caixa.",
          });
      }
    })();
    return () => c.abort();
  }, [filter, revision, requestKey]);
  const contaSelecionada = data?.contas.some((a) => a.id === conta)
    ? conta
    : "";
  const accounts =
    data?.contas.filter(
      (a) => !contaSelecionada || a.id === contaSelecionada,
    ) ?? [];
  const diarioDisponivel = data
    ? (Date.parse(data.fim) - Date.parse(data.inicio)) / 86400000 <= 3660
    : false;
  const diario = agrupamento === "diario" && diarioDisponivel;
  const hoje = todayInBrazil();
  let rows: ReturnType<typeof ledgerRows> = [],
    rowError = "";
  try {
    if (data) rows = ledgerRows(data, contaSelecionada, diario);
  } catch (e) {
    rowError =
      e instanceof Error
        ? e.message
        : "Não foi possível agrupar os movimentos.";
  }
  const chartRows = rows.map((r) => ({
    ...r,
    saldoVisual: r.periodo <= hoje.slice(0, diario ? 10 : 7) ? r.saldo : null,
  }));
  const saldoEsperado = accounts.reduce((s, a) => s + cents(a.saldoFinal), 0);
  const detalhesConferidos =
    !!data?.verificacao?.detalhesConferidos &&
    Math.round(
      (rows.at(-1)?.saldo ?? accounts.reduce((s, a) => s + a.saldoInicial, 0)) *
        100,
    ) === saldoEsperado;
  const semAbertura = accounts.some((a) => a.semSaldoInicial);
  const page = Math.min(pagina, Math.max(0, Math.ceil(rows.length / 31) - 1));
  const dateLabel = (s: string) =>
    diario ? dateBR(s) : s.split("-").reverse().join("/");
  const lineName = semAbertura
    ? "Acumulado calculado · abertura incompleta"
    : "Saldo calculado";
  const eixo = eixoFinanceiro(
    rows.flatMap((r) => [
      r.recebido,
      r.pagamentoVisual,
      r.transferenciaEntrada,
      r.transferenciaSaidaVisual,
      r.abertura,
      r.saldo,
    ]),
  );
  const exportRows = () =>
    exportarCSV("fluxo-por-conta.csv", [
      [
        "Empresa",
        "Conta",
        "Período",
        "Recebimentos",
        "Pagamentos",
        "Transferências entrada",
        "Transferências saída",
        "Aberturas",
        "Movimento líquido",
        lineName,
      ],
      ...rows.map((r) => [
        data!.clienteId,
        contaSelecionada || "Todas",
        r.periodo,
        r.recebido,
        r.pago,
        r.transferenciaEntrada,
        r.transferenciaSaida,
        r.abertura,
        r.liquido,
        r.periodo > hoje.slice(0, diario ? 10 : 7) ? "Não projetado" : r.saldo,
      ]),
    ]);
  const exportAccounts = () =>
    data &&
    exportarCSV("saldos-por-conta.csv", [
      [
        "Empresa",
        "Início",
        "Fim",
        "ID da conta",
        "Conta",
        "Anterior ao recorte",
        "Recebido",
        "Pago",
        "Transferências entrada",
        "Transferências saída",
        "Aberturas",
        "Saldo calculado",
        "Abertura anterior",
        "Detalhes conferidos",
        "Cobertura certificada",
      ],
      ...accounts.map((a) => [
        data.clienteId,
        data.inicio,
        data.fim,
        a.id,
        a.nome,
        a.saldoInicial,
        a.recebimentos,
        a.pagamentos,
        a.transferenciasEntrada,
        a.transferenciasSaida,
        a.ajustesAbertura,
        a.saldoFinal,
        a.semSaldoInicial ? "Não" : "Sim",
        detalhesConferidos ? "Sim" : "Não",
        "Não",
      ]),
    ]);
  const exportMoves = () =>
    data &&
    exportarCSV("movimentos-por-conta.csv", [
      ["ID", "Origem", "Tipo", "Data", "ID da conta", "Conta", "Valor"],
      ...data.movimentos
        .filter((m) => !contaSelecionada || m.contaId === contaSelecionada)
        .map((m) => [
          m.id,
          m.origem,
          m.tipo,
          m.data,
          m.contaId,
          m.conta,
          m.valor,
        ]),
    ]);
  return (
    <section className="fe-card cf-ledger" aria-labelledby="cf-ledger-title">
      <FinancialChartPaint id={paint.id} />
      <div className="fe-heading">
        <div>
          <span className="fe-eyebrow">Caixa por conta · API</span>
          <h2 id="cf-ledger-title">Para onde foi cada valor?</h2>
          <p>
            Inclui transferências e aberturas, separadas dos recebimentos e
            pagamentos.
          </p>
        </div>
      </div>
      <p className="cf-scope">
        Este bloco usa apenas a empresa e o intervalo do filtro global.
        Categoria, centro e situação não restringem o saldo por conta. O seletor
        de conta abaixo afeta somente este bloco.
      </p>
      {error && <p role="alert">{error}</p>}
      {!data && !error && <p role="status">Calculando movimentos por conta…</p>}
      {data && (
        <>
          <div className="cf-toolbar">
            <p className="fe-note">
              {dateBR(data.inicio)} a {dateBR(data.fim)}
            </p>
            <div className="fe-actions">
              <div role="group" aria-label="Agrupamento dos movimentos">
                <button
                  type="button"
                  disabled={!diarioDisponivel}
                  aria-pressed={diario}
                  onClick={() => {
                    setAgrupamento("diario");
                    setPagina(0);
                  }}
                >
                  Diário
                </button>
                <button
                  type="button"
                  aria-pressed={!diario}
                  onClick={() => {
                    setAgrupamento("mensal");
                    setPagina(0);
                  }}
                >
                  Mensal
                </button>
              </div>
              <label className="cf-account">
                Conta financeira
                <select
                  value={contaSelecionada}
                  onChange={(e) => {
                    setConta(e.target.value);
                    setPagina(0);
                  }}
                >
                  <option value="">Todas as contas</option>
                  {data.contas.map((a) => (
                    <option key={a.id} value={a.id}>
                      {accountName(a)}
                    </option>
                  ))}
                </select>
              </label>
              <button type="button" disabled={!!rowError} onClick={exportRows}>
                CSV do fluxo
              </button>
            </div>
          </div>
          {!diarioDisponivel && (
            <p className="fe-note">
              O intervalo está em visão mensal. Para visualizar por dia,
              selecione até dez anos no filtro global; todos os movimentos
              continuam na exportação.
            </p>
          )}
          <details className="cf-details">
            <summary>
              {data.parcial || !detalhesConferidos
                ? "Cobertura e conferência precisam de atenção"
                : "Fontes processadas · conferir saldo com o banco"}
            </summary>
            <p>
              Última captura ampla concluída:{" "}
              {data.capturadoEm
                ? new Date(data.capturadoEm).toLocaleString("pt-BR")
                : "não disponível"}
              . Os saldos calculados não certificam cobertura integral nem
              igualdade com o banco.
            </p>
            <p>
              {detalhesConferidos
                ? "Soma dos movimentos e dos saldos calculados conferida."
                : "A conferência dos detalhes não foi aprovada ou está indisponível. Revise antes de usar o saldo."}
            </p>
            {data.avisos.map((a, i) => (
              <p key={i}>{a}</p>
            ))}
          </details>
          {semAbertura && (
            <p className="cf-scope">
              Há conta sem abertura anterior ao recorte. A linha mostra o
              acumulado dos dados disponíveis; não representa saldo bancário
              confirmado.
            </p>
          )}
          {rowError ? (
            <p role="alert">{rowError}</p>
          ) : (
            <>
              <div
                className="cf-chart"
                role="img"
                aria-label="Recebimentos e transferências de entrada acima de zero, pagamentos e transferências de saída abaixo de zero, aberturas separadas e saldo calculado em linha. Consulte todos os valores na tabela abaixo."
              >
                <ResponsiveContainer width="100%" height="100%" minWidth={0}>
                  <ComposedChart
                    data={chartRows}
                    margin={{ top: 20, right: 12, left: 0, bottom: 4 }}
                    accessibilityLayer
                  >
                    <CartesianGrid
                      stroke="var(--fv-dark-border, #dbe4ef)"
                      vertical={false}
                      strokeDasharray="3 4"
                    />
                    <XAxis
                      dataKey="periodo"
                      tickFormatter={dateLabel}
                      minTickGap={24}
                      tick={{
                        fill: "var(--fv-dark-muted, #64748b)",
                        fontSize: 11,
                      }}
                    />
                    <YAxis
                      width={62}
                      domain={eixo.domain}
                      ticks={eixo.ticks}
                      tickFormatter={compactoFinanceiro}
                      tick={{
                        fill: "var(--fv-dark-muted, #64748b)",
                        fontSize: 11,
                      }}
                    />
                    <Tooltip
                      labelFormatter={(s) => dateLabel(String(s))}
                      contentStyle={{
                        background: "var(--fv-dark-surface, #fff)",
                        borderColor: "var(--fv-dark-border, #dbe4ef)",
                        borderRadius: 10,
                      }}
                      formatter={(v, n) => [
                        brl(
                          n === "Pagamentos" || n === "Transferências de saída"
                            ? -Number(v)
                            : Number(v),
                        ),
                        n,
                      ]}
                    />
                    <ReferenceLine
                      y={0}
                      stroke="var(--fv-dark-muted, #64748b)"
                    />
                    <Bar
                      dataKey="recebido"
                      name="Recebimentos"
                      fill={paint.fill("#22b987")}
                      maxBarSize={28}
                      isAnimationActive={false}
                    />
                    <Bar
                      dataKey="pagamentoVisual"
                      name="Pagamentos"
                      fill={paint.fill("#f04467")}
                      maxBarSize={28}
                      isAnimationActive={false}
                    />
                    <Bar
                      dataKey="transferenciaEntrada"
                      name="Transferências de entrada"
                      fill={paint.fill("#9b71ef")}
                      maxBarSize={22}
                      isAnimationActive={false}
                    />
                    <Bar
                      dataKey="transferenciaSaidaVisual"
                      name="Transferências de saída"
                      fill="#b34f89"
                      maxBarSize={22}
                      isAnimationActive={false}
                    />
                    <Bar
                      dataKey="abertura"
                      name="Aberturas no recorte"
                      fill={paint.fill("#f59e0b")}
                      maxBarSize={22}
                      isAnimationActive={false}
                    />
                    <Line
                      type="linear"
                      dataKey="saldoVisual"
                      name={lineName}
                      connectNulls={false}
                      stroke="#60a5fa"
                      strokeWidth={2.5}
                      dot={rows.length <= 32 ? { r: 2.5 } : false}
                      isAnimationActive={false}
                    />
                    {rows.length > 40 && (
                      <Brush
                        dataKey="periodo"
                        startIndex={Math.max(0, rows.length - 31)}
                        endIndex={rows.length - 1}
                        tickFormatter={dateLabel}
                        height={24}
                        stroke="#60a5fa"
                        fill="var(--fv-dark-surface, #fff)"
                      />
                    )}
                  </ComposedChart>
                </ResponsiveContainer>
              </div>
              <div className="cf-legend">
                {[
                  ["#22b987", "Recebimentos"],
                  ["#f04467", "Pagamentos"],
                  ["#9b71ef", "Transferências de entrada"],
                  ["#b34f89", "Transferências de saída"],
                  ["#f59e0b", "Aberturas"],
                  ["#60a5fa", lineName],
                ].map(([color, label]) => (
                  <span key={label}>
                    <i style={{ background: color }} />
                    {label}
                  </span>
                ))}
              </div>
              <p className="fe-note">
                Expanda um dia ou mês na tabela para conferir os movimentos por
                conta. Dias sem registros mantêm o acumulado disponível; isso
                não certifica ausência de movimentos. A linha termina na data
                atual: datas futuras não são uma projeção. Em intervalos longos,
                o gráfico abre nos últimos 31 períodos; amplie pelas alças na
                base. A tabela e o CSV mantêm o recorte completo,
                independentemente do zoom.
              </p>
              <div
                className="fe-table cf-table"
                tabIndex={0}
                role="region"
                aria-label="Fluxo por conta, role para ver todas as colunas"
              >
                <table>
                  <caption>
                    Fluxo {diario ? "diário" : "mensal"} ·{" "}
                    {contaSelecionada
                      ? accountName(accounts[0])
                      : "Todas as contas"}{" "}
                    · {rows.length} períodos
                  </caption>
                  <thead>
                    <tr>
                      {[
                        "Data / movimentos",
                        "Recebimentos",
                        "Pagamentos",
                        "Transferências +",
                        "Transferências −",
                        "Aberturas",
                        lineName,
                      ].map((h) => (
                        <th key={h} scope="col">
                          {h}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {rows.slice(page * 31, (page + 1) * 31).map((r) => (
                      <tr key={r.periodo}>
                        <th scope="row">
                          <details>
                            <summary>
                              {dateLabel(r.periodo)} · {r.movimentos.length}{" "}
                              movimentos
                            </summary>
                            <ul className="cf-moves">
                              {r.movimentos.map((m) => (
                                <li key={m.id}>
                                  <strong>
                                    {movementName(m)} · {brl(m.valor)}
                                  </strong>
                                  <span>
                                    {dateBR(m.data)} ·{" "}
                                    {m.conta === m.contaId
                                      ? "Nome da conta não informado"
                                      : m.conta}
                                  </span>
                                </li>
                              ))}
                            </ul>
                            {!r.movimentos.length && (
                              <p>Sem movimentos disponíveis nesta data.</p>
                            )}
                          </details>
                        </th>
                        {[
                          r.recebido,
                          r.pago,
                          r.transferenciaEntrada,
                          r.transferenciaSaida,
                          r.abertura,
                          r.saldo,
                        ].map((v, i) => (
                          <td key={i}>
                            {i === 5 &&
                            r.periodo > hoje.slice(0, diario ? 10 : 7)
                              ? "—"
                              : brl(v)}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <th scope="row">Total / acumulado final</th>
                      {[
                        rows.reduce((s, r) => s + cents(r.recebido), 0) / 100,
                        rows.reduce((s, r) => s + cents(r.pago), 0) / 100,
                        rows.reduce(
                          (s, r) => s + cents(r.transferenciaEntrada),
                          0,
                        ) / 100,
                        rows.reduce(
                          (s, r) => s + cents(r.transferenciaSaida),
                          0,
                        ) / 100,
                        rows.reduce((s, r) => s + cents(r.abertura), 0) / 100,
                        rows.at(-1)?.saldo ?? 0,
                      ].map((v, i) => (
                        <td key={i}>{brl(v)}</td>
                      ))}
                    </tr>
                  </tfoot>
                </table>
              </div>
              {rows.length > 31 && (
                <div className="cf-pagination">
                  <button
                    type="button"
                    disabled={page === 0}
                    onClick={() => setPagina(page - 1)}
                  >
                    Anterior
                  </button>
                  <span>
                    Página {page + 1} de {Math.ceil(rows.length / 31)}
                  </span>
                  <button
                    type="button"
                    disabled={(page + 1) * 31 >= rows.length}
                    onClick={() => setPagina(page + 1)}
                  >
                    Próxima
                  </button>
                </div>
              )}
            </>
          )}
          <details className="cf-details">
            <summary>
              Saldos e composição de cada conta ({accounts.length})
            </summary>
            <div className="fe-actions">
              <button type="button" onClick={exportAccounts}>
                CSV dos saldos
              </button>
              <button type="button" onClick={exportMoves}>
                CSV de todos os movimentos{" "}
                {contaSelecionada ? "desta conta" : "das contas"}
              </button>
            </div>
            <div
              className="fe-table cf-table"
              tabIndex={0}
              role="region"
              aria-label="Composição dos saldos"
            >
              <table>
                <thead>
                  <tr>
                    {[
                      "Conta",
                      "Anterior ao recorte",
                      "Recebido",
                      "Pago",
                      "Transferências +",
                      "Transferências −",
                      "Aberturas",
                      "Saldo calculado",
                    ].map((h) => (
                      <th key={h} scope="col">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {accounts.map((a) => (
                    <tr key={a.id}>
                      <th scope="row">
                        {accountName(a)}
                        {a.semSaldoInicial && " · abertura incompleta"}
                      </th>
                      {[
                        a.saldoInicial,
                        a.recebimentos,
                        a.pagamentos,
                        a.transferenciasEntrada,
                        a.transferenciasSaida,
                        a.ajustesAbertura,
                        a.saldoFinal,
                      ].map((v, i) => (
                        <td key={i}>{brl(v)}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
          <p className="fe-note">
            A resposta atual informa data, natureza, conta e valor de cada
            movimento. Ela não fornece descrição e categoria neste bloco; esses
            campos não são inferidos nem certificados por semelhança.
          </p>
        </>
      )}
    </section>
  );
}
