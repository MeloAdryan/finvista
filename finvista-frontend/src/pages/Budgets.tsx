import { useEffect, useState } from "react";

import {
  getBudgetPipeline,
  type BudgetPipelineData,
} from "../services/budgetPipelineService";

import BudgetPipelineChart from "../charts/BudgetPipelineChart";

import "../styles/dashboard.css";

function Budgets() {
  const [orcamentos, setOrcamentos] = useState<BudgetPipelineData[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  useEffect(() => {
    async function carregarOrcamentos() {
      try {
        setCarregando(true);
        setErro(null);

        const dadosOrcamentos = await getBudgetPipeline();

        setOrcamentos(dadosOrcamentos);
      } catch (error) {
        console.error("Erro ao carregar orçamentos:", error);

        if (error instanceof Error) {
          setErro(error.message);
        } else {
          setErro("Não foi possível carregar os orçamentos.");
        }
      } finally {
        setCarregando(false);
      }
    }

    void carregarOrcamentos();
  }, []);

  const formatarMoeda = (valor: number) => {
    return valor.toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });
  };

  const valorTotalOrcamentos = orcamentos.reduce(
    (soma, item) => soma + item.valor,
    0,
  );

  const valorTotalPonderado = orcamentos.reduce(
    (soma, item) => soma + item.valorPonderado,
    0,
  );

  if (carregando) {
    return <p>Carregando orçamentos...</p>;
  }

  if (erro) {
    return <p>{erro}</p>;
  }

  return (
    <div className="dashboard">
      {/* ORÇAMENTOS / PIPELINE */}

      <section id="orcamentos" className="budget-pipeline-section">
        <div className="pipeline-summary">
          <div className="pipeline-summary-card">
            <span>Pipeline total</span>

            <strong>{formatarMoeda(valorTotalOrcamentos)}</strong>
          </div>

          <div className="pipeline-summary-card">
            <span>Receita ponderada</span>

            <strong>{formatarMoeda(valorTotalPonderado)}</strong>
          </div>
        </div>

        <BudgetPipelineChart dados={orcamentos} />
      </section>

      <section className="budget-pipeline-table-card">
        <div className="budget-pipeline-table-header">
          <div>
            <span className="budget-pipeline-table-eyebrow">
              OPORTUNIDADES
            </span>

            <h2>Oportunidades comerciais</h2>

            <p>
              Acompanhamento das propostas por valor, estágio e probabilidade de
              fechamento.
            </p>
          </div>

          <div className="budget-pipeline-table-total">
            <span>Pipeline total</span>

            <strong>
              {formatarMoeda(
                orcamentos.reduce(
                  (total, item) => total + Number(item.valor),
                  0,
                ),
              )}
            </strong>

            <small>{orcamentos.length} oportunidades</small>
          </div>
        </div>

        {orcamentos.length > 0 ? (
          <div className="table-wrapper">
            <table className="budget-pipeline-table">
              <thead>
                <tr>
                  <th className="budget-rank-column">#</th>
                  <th>Cliente</th>
                  <th>Valor</th>
                  <th>Status</th>
                  <th>Probabilidade</th>
                  <th>Receita ponderada</th>
                </tr>
              </thead>

              <tbody>
                {orcamentos.map((item, index) => {
                  const probabilidade = Number(item.probabilidade);

                  const statusClass =
                    item.status === "Negociação"
                      ? "budget-status-negotiation"
                      : item.status === "Proposta enviada"
                        ? "budget-status-proposal"
                        : item.status === "Em análise"
                          ? "budget-status-analysis"
                          : "budget-status-initial";

                  return (
                    <tr key={item.id}>
                      <td className="budget-rank-column">
                        <span
                          className={
                            index === 0
                              ? "budget-rank budget-rank-first"
                              : "budget-rank"
                          }
                        >
                          {index + 1}
                        </span>
                      </td>

                      <td>
                        <div className="budget-client">
                          <span className="budget-client-icon">
                            {item.cliente.charAt(0).toUpperCase()}
                          </span>

                          <div>
                            <strong>{item.cliente}</strong>
                            <small>Oportunidade comercial</small>
                          </div>
                        </div>
                      </td>

                      <td>
                        <strong className="budget-value">
                          {formatarMoeda(Number(item.valor))}
                        </strong>
                      </td>

                      <td>
                        <span className={`budget-status ${statusClass}`}>
                          <span className="budget-status-dot" />
                          {item.status}
                        </span>
                      </td>

                      <td>
                        <div className="budget-probability">
                          <div className="budget-probability-top">
                            <strong>{probabilidade}%</strong>

                            {probabilidade >= 80 && (
                              <span className="budget-high-chance">
                                Alta chance
                              </span>
                            )}
                          </div>

                          <div
                            className="budget-progress"
                            role="progressbar"
                            aria-label={`Probabilidade de fechamento de ${item.cliente}`}
                            aria-valuenow={probabilidade}
                            aria-valuemin={0}
                            aria-valuemax={100}
                          >
                            <div
                              className="budget-progress-fill"
                              style={{
                                width: `${Math.min(
                                  Math.max(probabilidade, 0),
                                  100,
                                )}%`,
                              }}
                            />
                          </div>
                        </div>
                      </td>

                      <td>
                        <div className="budget-weighted-value">
                          <strong>
                            {formatarMoeda(Number(item.valorPonderado))}
                          </strong>

                          <small>Valor ponderado</small>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="budget-pipeline-table-empty">
            Nenhuma oportunidade comercial disponível.
          </div>
        )}
      </section>
    </div>
  );
}

export default Budgets;