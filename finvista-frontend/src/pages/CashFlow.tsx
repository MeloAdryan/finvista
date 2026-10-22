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
  const [modo, setModo] = useState<"realizado" | "competencia">("realizado");
  const [cobertura, setCobertura] = useState<RealizedCashFlow | null>(null);
  const [dados, setDados] = useState<CashFlowData[]>([]);
  const [periodo, setPeriodo] = useState<PeriodoFluxoCaixa>(6);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");
  const [tentativa, setTentativa] = useState(0);
  useEffect(() => {
    let ativo = true;
    const controller = new AbortController();
    async function carregar() {
      setCarregando(true);
      setErro("");
      setCobertura(null);
      setDados([]);
      try {
        const caixa =
          modo === "realizado"
            ? await getRealizedCashFlow(periodo, controller.signal)
            : null;
        const resposta = caixa ? caixa.meses : await getCashFlow(periodo);
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
  }, [periodo, tentativa, modo]);
  return (
    <div className="dashboard fe-page">
      <div
        className="fe-actions cash-flow-basis"
        role="group"
        aria-label="Base do fluxo financeiro"
      >
        <button
          type="button"
          aria-pressed={modo === "realizado"}
          onClick={() => setModo("realizado")}
        >
          Caixa realizado
        </button>
        <button
          type="button"
          aria-pressed={modo === "competencia"}
          onClick={() => setModo("competencia")}
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
              : "Baixas locais conferidas"}
          </strong>
          <p>
            Na base local: {cobertura.lancamentosConferidos} lançamentos com
            composição conferida; {cobertura.lancamentosSemDetalhamento} sem
            detalhamento utilizável. No período selecionado:{" "}
            {cobertura.baixasNoPeriodo} baixas.
          </p>
          <p>
            Os totais cobrem apenas as baixas conferidas da base local, sem
            saldo bancário inicial. Não certificam que todas as parcelas do
            Conta Azul foram importadas. Registros sem detalhamento e
            cancelamentos com pagamentos precisam de revisão.
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
          realizado={modo === "realizado"}
          dados={dados}
          periodo={periodo}
          carregando={carregando}
          onPeriodoChange={setPeriodo}
        />
      )}
    </div>
  );
}
