import { useEffect, useState } from "react";
import {
  getCashFlow,
  type CashFlowData,
  type PeriodoFluxoCaixa,
} from "../services/cashFlowService";
import CashFlowChart from "../charts/CashFlowChart";
import "../styles/dashboard.css";
import "../styles/financial-evolution.css";

export default function CashFlow() {
  const [dados, setDados] = useState<CashFlowData[]>([]);
  const [periodo, setPeriodo] = useState<PeriodoFluxoCaixa>(6);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");
  const [tentativa, setTentativa] = useState(0);
  useEffect(() => {
    let ativo = true;
    async function carregar() {
      setCarregando(true);
      try {
        const resposta = await getCashFlow(periodo);
        if (ativo) {
          setDados(resposta);
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
    };
  }, [periodo, tentativa]);
  return (
    <div className="dashboard fe-page">
      {erro ? (
        <div className="fe-card" role="alert">
          <p>{erro}</p>
          <button type="button" onClick={() => setTentativa(tentativa + 1)}>
            Tentar novamente
          </button>
        </div>
      ) : (
        <CashFlowChart
          dados={dados}
          periodo={periodo}
          carregando={carregando}
          onPeriodoChange={setPeriodo}
        />
      )}
    </div>
  );
}
