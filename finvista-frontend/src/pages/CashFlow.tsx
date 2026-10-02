import { useEffect, useState } from "react";

import {
  getCashFlow,
  type CashFlowData,
  type PeriodoFluxoCaixa,
} from "../services/cashFlowService";

import CashFlowChart from "../charts/CashFlowChart";

import "../styles/dashboard.css";

function CashFlow() {
  const [fluxoCaixa, setFluxoCaixa] = useState<CashFlowData[]>([]);

  const [periodo, setPeriodo] = useState<PeriodoFluxoCaixa>(6);

  const [carregando, setCarregando] = useState(true);

  const [erro, setErro] = useState<string | null>(null);

const carregarFluxoCaixa = async (
  periodoSelecionado: PeriodoFluxoCaixa,
) => {
  try {
    setCarregando(true);
    setErro(null);

    const dadosFluxoCaixa = await getCashFlow(
      periodoSelecionado,
    );

    setFluxoCaixa(dadosFluxoCaixa);
  } catch (error) {
    console.error(
      "Erro ao carregar fluxo de caixa:",
      error,
    );

    setErro(
      error instanceof Error
        ? error.message
        : "Não foi possível carregar o fluxo de caixa.",
    );
  } finally {
    setCarregando(false);
  }
};

useEffect(() => {
  let ativo = true;

  const carregarInicial = async () => {
    try {
      const dadosFluxoCaixa = await getCashFlow(6);

      if (ativo) {
        setFluxoCaixa(dadosFluxoCaixa);
        setErro(null);
      }
    } catch (error) {
      console.error(
        "Erro ao carregar fluxo de caixa:",
        error,
      );

      if (ativo) {
        setErro(
          error instanceof Error
            ? error.message
            : "Não foi possível carregar o fluxo de caixa.",
        );
      }
    } finally {
      if (ativo) {
        setCarregando(false);
      }
    }
  };

  void carregarInicial();

  return () => {
    ativo = false;
  };
}, []);

const alterarPeriodo = (
  novoPeriodo: PeriodoFluxoCaixa,
) => {
  if (novoPeriodo === periodo) {
    return;
  }

  setPeriodo(novoPeriodo);
  void carregarFluxoCaixa(novoPeriodo);
};

  const formatarMoeda = (valor: number) => {
    return Number(valor).toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });
  };

  return (
    <div className="dashboard">
      <section
        id="fluxo-caixa"
        className="cashflow-section"
      >
        {erro ? (
          <div
            className="cashflow-error"
            role="alert"
          >
            <strong>
              Não foi possível carregar o fluxo de caixa.
            </strong>

            <p>{erro}</p>

            <button
              type="button"
              onClick={() => {
                void carregarFluxoCaixa(periodo);
              }}
            >
              Tentar novamente
            </button>
          </div>
        ) : (
          <CashFlowChart
            dados={fluxoCaixa}
            periodo={periodo}
            carregando={carregando}
            onPeriodoChange={alterarPeriodo}
          />
        )}
      </section>

      {!erro && (
        <section className="cashflow-table-card">
          <h2>Detalhamento do fluxo de caixa</h2>

          {carregando ? (
            <div className="cashflow-table-loading">
              Atualizando detalhamento...
            </div>
          ) : fluxoCaixa.length === 0 ? (
            <div className="cashflow-table-loading">
              Nenhum dado financeiro encontrado para o período
              selecionado.
            </div>
          ) : (
            <div className="table-wrapper">
              <table className="cashflow-table">
                <thead>
                  <tr>
                    <th>Mês</th>
                    <th>Saldo inicial</th>
                    <th>Entradas</th>
                    <th>Saídas</th>
                    <th>Saldo final</th>
                  </tr>
                </thead>

                <tbody>
                  {fluxoCaixa.map((item) => (
                    <tr key={item.mes}>
                      <td>{item.mes}</td>

                      <td>
                        {formatarMoeda(
                          item.saldoInicial,
                        )}
                      </td>

                      <td>
                        {formatarMoeda(
                          item.entradas,
                        )}
                      </td>

                      <td>
                        {formatarMoeda(
                          item.saidas,
                        )}
                      </td>

                      <td>
                        {formatarMoeda(
                          item.saldoFinal,
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </div>
  );
}

export default CashFlow;