import {
  dateLabel,
  managementGet,
  money,
  type OpenPosition,
} from "../services/financialManagementService";
import FinancialLedgerPanel from "../components/FinancialLedgerPanel";
import { useContext } from "react";
import {
  FinancialViewContext,
  FinancialViewUpdateContext,
} from "../contexts/financialViewContext";
import { criterionNames, viewRange } from "../services/financialViewService";
import { useEffect, useState } from "react";
import {
  getCashFlow,
  getRealizedCashFlow,
  type RealizedCashFlow,
  type CashFlowData,
  type PeriodoFluxoCaixa,
} from "../services/cashFlowService";
import CashFlowChart from "../charts/CashFlowChart";
import "../styles/dashboard.css";
import "../styles/financial-evolution.css";
import "../styles/financial-integrity.css";

export default function CashFlow() {
  const viewFilter = useContext(FinancialViewContext);
  const updateView = useContext(FinancialViewUpdateContext);
  const [modo, setModo] = useState<"realizado" | "competencia">("realizado");
  const [cobertura, setCobertura] = useState<RealizedCashFlow | null>(null);
  const [dados, setDados] = useState<CashFlowData[]>([]);
  const [periodo, setPeriodo] = useState<PeriodoFluxoCaixa>(6);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");
  const [tentativa, setTentativa] = useState(0);
  const realized = viewFilter
    ? viewFilter.criterio === "BAIXA" || viewFilter.criterio === "BAIXA_EXTRATO"
    : modo === "realizado";
  function setBasis(next: "realizado" | "competencia") {
    setModo(next);
    if (viewFilter && updateView)
      updateView({
        ...viewFilter,
        criterio: next === "realizado" ? "BAIXA" : "COMPETENCIA",
        situacao: "TODAS",
      });
  }
  function setWindow(next: PeriodoFluxoCaixa) {
    setPeriodo(next);
    if (!viewFilter || !updateView) return;
    if (next === "todos") {
      updateView({ ...viewFilter, periodo: "TODOS" });
      return;
    }
    const end =
      viewRange(viewFilter)?.fim ?? new Date().toISOString().slice(0, 10);
    const [year, month] = end.split("-").map(Number);
    const start = new Date(Date.UTC(year, month - Number(next), 1, 12))
      .toISOString()
      .slice(0, 10);
    updateView({
      ...viewFilter,
      periodo: "PERSONALIZADO",
      inicio: start,
      fim: end,
    });
  }
  useEffect(() => {
    let ativo = true;
    const controller = new AbortController();
    async function carregar() {
      setCarregando(true);
      setErro("");
      setCobertura(null);
      setDados([]);
      try {
        const caixa = realized
          ? await getRealizedCashFlow(
              periodo,
              controller.signal,
              viewFilter ?? undefined,
            )
          : null;
        const resposta = caixa
          ? caixa.meses
          : await getCashFlow(periodo, viewFilter ?? undefined);
        if (ativo) {
          setDados(resposta);
          setCobertura(caixa);
          setErro("");
        }
      } catch (e) {
        if (ativo)
          setErro(
            e instanceof Error
              ? e.message
              : "Não foi possível carregar a evolução.",
          );
      } finally {
        if (ativo) setCarregando(false);
      }
    }
    void carregar();
    return () => {
      ativo = false;
      controller.abort();
    };
  }, [periodo, tentativa, modo, realized, viewFilter]);
  return (
    <div className="dashboard fe-page cash-flow-page">
      <div
        className="fe-actions cash-flow-basis"
        role="group"
        aria-label="Base do fluxo financeiro"
      >
        <button
          type="button"
          aria-pressed={realized}
          onClick={() => setBasis("realizado")}
        >
          Caixa realizado
        </button>
        <button
          type="button"
          aria-pressed={
            viewFilter
              ? viewFilter.criterio === "COMPETENCIA"
              : modo === "competencia"
          }
          onClick={() => setBasis("competencia")}
        >
          Competência
        </button>
        <button
          type="button"
          disabled={carregando}
          onClick={() => setTentativa((v) => v + 1)}
        >
          Atualizar fluxo
        </button>
      </div>
      {cobertura && !carregando && !erro && (
        <details className="fe-card cf-coverage">
          <summary>
            Cobertura das baixas · {cobertura.baixasNoPeriodo} no período
            {cobertura.parcial ? " · detalhamento parcial" : " · ver detalhes"}
          </summary>
          <strong>
            {cobertura.parcial
              ? "Caixa com detalhamento parcial"
              : "Movimentos financeiros no critério selecionado"}
          </strong>
          <p>
            Nos dados armazenados no FinVista: {cobertura.lancamentosConferidos}{" "}
            lançamentos com composição conferida;{" "}
            {cobertura.lancamentosSemDetalhamento} sem detalhamento utilizável.
            No período selecionado: {cobertura.baixasNoPeriodo} baixas.
          </p>
          <p>
            O gráfico usa a fonte e o critério selecionados e não inclui saldo
            bancário inicial ou transferências. Não certifica que todas as
            parcelas do Conta Azul foram importadas. Registros sem detalhamento
            e cancelamentos com pagamentos precisam de revisão.
          </p>
        </details>
      )}
      {erro ? (
        <div className="fe-card" role="alert">
          <p>{erro}</p>
          <button type="button" onClick={() => setTentativa(tentativa + 1)}>
            Tentar novamente
          </button>
        </div>
      ) : cobertura && !carregando && cobertura.lancamentosConferidos === 0 ? (
        <div className="fe-card" role="status">
          Nenhuma baixa utilizável para compor esse fluxo. O movimento total não
          foi calculado; atualize a sincronização e confira os registros sem
          detalhamento.
        </div>
      ) : (
        <CashFlowChart
          key={modo}
          realizado={realized}
          dados={dados}
          periodo={periodo}
          carregando={carregando}
          onPeriodoChange={setWindow}
          filtroAplicado={!!viewFilter}
          criterioLabel={
            viewFilter ? criterionNames[viewFilter.criterio] : undefined
          }
        />
      )}
      {viewFilter?.criterio === "BAIXA" && (
        <FinancialLedgerPanel
          key={JSON.stringify(viewFilter) + tentativa}
          filter={viewFilter}
          revision={tentativa}
        />
      )}
      {viewFilter?.clienteId != null && (
        <OpenDueItems
          key={JSON.stringify(viewFilter) + tentativa}
          clienteId={viewFilter.clienteId}
          range={viewRange(viewFilter)}
        />
      )}
    </div>
  );
}

const DUE_PAGE_SIZE = 15;

function OpenDueItems({
  clienteId,
  range,
}: {
  clienteId: number;
  range: ReturnType<typeof viewRange>;
}) {
  const [position, setPosition] = useState<OpenPosition | null>(null);
  const [error, setError] = useState("");
  const [page, setPage] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    void managementGet<OpenPosition>(
      "posicao",
      clienteId,
      {},
      controller.signal,
    )
      .then((result) => {
        if (!controller.signal.aborted) setPosition(result);
      })
      .catch(() => {
        if (!controller.signal.aborted)
          setError(
            "Não foi possível consultar os vencimentos. Use Atualizar fluxo para tentar novamente.",
          );
      });
    return () => controller.abort();
  }, [clienteId]);

  const rows =
    position?.itens.filter(
      ({ vencimento, aberto }) =>
        vencimento != null &&
        aberto != null &&
        aberto > 0 &&
        (!range || (vencimento >= range.inicio && vencimento <= range.fim)),
    ) ?? [];
  const omitted =
    position?.itens.filter(
      ({ vencimento, aberto }) => vencimento == null || aberto == null,
    ).length ?? 0;
  const pageCount = Math.ceil(rows.length / DUE_PAGE_SIZE);

  return (
    <section className="fe-card" aria-labelledby="cash-flow-open-due">
      <h2 id="cash-flow-open-due">Vencimentos em aberto</h2>
      <p>
        {range
          ? `Vencimentos de ${dateLabel(range.inicio)} a ${dateLabel(range.fim)}`
          : "Todos os vencimentos"}
        {position && ` · Posição em ${dateLabel(position.referencia)}`}. O
        cartão usa o período selecionado pela data de vencimento e mostra o
        saldo restante de cada título.
      </p>
      {error && <p role="alert">{error}</p>}
      {!error && !position && <p role="status">Consultando vencimentos…</p>}
      {omitted > 0 && (
        <p>
          {omitted} título(s) da empresa sem saldo ou vencimento válido ficaram
          fora da tabela.
        </p>
      )}
      {position && rows.length === 0 && (
        <p>
          Nenhum título com saldo em aberto e vencimento informado neste
          período.
        </p>
      )}
      {position && rows.length > 0 && (
        <>
          <p>Em telas pequenas, deslize a tabela para ver todas as colunas.</p>
          <div
            className="fe-table"
            role="region"
            aria-label="Títulos em aberto"
            tabIndex={0}
          >
            <table>
              <caption>
                {rows.length} título(s) · Página {page + 1} de {pageCount}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Descrição</th>
                  <th scope="col">Tipo</th>
                  <th scope="col">Vencimento</th>
                  <th scope="col">Situação</th>
                  <th scope="col">Saldo em aberto</th>
                </tr>
              </thead>
              <tbody>
                {rows
                  .slice(page * DUE_PAGE_SIZE, (page + 1) * DUE_PAGE_SIZE)
                  .map((item) => (
                    <tr key={item.id}>
                      <th scope="row">{item.descricao || "Sem descrição"}</th>
                      <td>
                        {item.tipo === "DESPESA" ? "A pagar" : "A receber"}
                      </td>
                      <td>{dateLabel(item.vencimento)}</td>
                      <td>
                        {item.faixa === "HOJE"
                          ? "Vence hoje"
                          : item.faixa === "A_VENCER"
                            ? "A vencer"
                            : "Vencido"}
                      </td>
                      <td>
                        {money(
                          item.tipo === "DESPESA"
                            ? -(item.aberto ?? 0)
                            : item.aberto,
                        )}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
          {pageCount > 1 && (
            <div
              className="fe-actions"
              aria-label="Paginação dos vencimentos"
              role="group"
            >
              <button
                type="button"
                disabled={page === 0}
                onClick={() => setPage(page - 1)}
              >
                Anterior
              </button>
              <button
                type="button"
                disabled={page + 1 === pageCount}
                onClick={() => setPage(page + 1)}
              >
                Próxima
              </button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
