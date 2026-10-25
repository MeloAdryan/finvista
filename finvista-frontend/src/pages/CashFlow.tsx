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
    <div className="dashboard fe-page">
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
          Baixas financeiras
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
        <aside className="fe-card" role="status">
          <strong>
            {cobertura.parcial
              ? "Caixa com detalhamento parcial"
              : "Movimentos financeiros no critério selecionado"}
          </strong>
          <p>
            Na base local: {cobertura.lancamentosConferidos} lançamentos com
            composição conferida; {cobertura.lancamentosSemDetalhamento} sem
            detalhamento utilizável. No período selecionado:{" "}
            {cobertura.baixasNoPeriodo} baixas.
          </p>
          <p>
            O gráfico usa a fonte e o critério selecionados e não inclui saldo
            bancário inicial ou transferências. Não certifica que todas as
            parcelas do Conta Azul foram importadas. Registros sem detalhamento
            e cancelamentos com pagamentos precisam de revisão.
          </p>
        </aside>
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
    </div>
  );
}
