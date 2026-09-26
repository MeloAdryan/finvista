import { useEffect, useState } from "react";

import {
  createBudget,
  getBudgetPipeline,
  type BudgetPipelineData,
  type CreateBudgetData,
} from "../services/budgetPipelineService";

import {
  obterUsuarioAtual,
  type AuthUser,
} from "../services/authService";

import BudgetPipelineChart from "../charts/BudgetPipelineChart";

import "../styles/dashboard.css";

function Budgets() {
  const [orcamentos, setOrcamentos] = useState<BudgetPipelineData[]>([]);
  const [usuario, setUsuario] = useState<AuthUser | null>(null);

  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  const [mostrarFormulario, setMostrarFormulario] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [erroCadastro, setErroCadastro] = useState<string | null>(null);

  const [novoOrcamento, setNovoOrcamento] =
    useState<CreateBudgetData>({
      nome: "",
      centroCusto: null,
      categoria: null,
      valorPlanejado: 0,
      dataInicio: "",
      dataFim: "",
    });

  useEffect(() => {
    async function carregarPagina() {
      try {
        setCarregando(true);
        setErro(null);

        const [dadosOrcamentos, usuarioAtual] = await Promise.all([
          getBudgetPipeline(),
          obterUsuarioAtual(),
        ]);

        setOrcamentos(dadosOrcamentos);
        setUsuario(usuarioAtual);
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

    void carregarPagina();
  }, []);

  const formatarMoeda = (valor: number) => {
    return Number(valor).toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });
  };

  const formatarData = (data: string) => {
    if (!data) {
      return "—";
    }

    const [ano, mes, dia] = data.split("-");

    return `${dia}/${mes}/${ano}`;
  };

  const totalPlanejado = orcamentos.reduce(
    (total, item) => total + Number(item.valorPlanejado),
    0,
  );

  const totalUtilizado = orcamentos.reduce(
    (total, item) => total + Number(item.valorUtilizado),
    0,
  );

  const totalDisponivel = orcamentos.reduce(
    (total, item) => total + Number(item.valorDisponivel),
    0,
  );

  const percentualGeral =
    totalPlanejado > 0
      ? (totalUtilizado / totalPlanejado) * 100
      : 0;

  const usuarioAdmin = usuario?.perfil === "ADMIN";

  async function handleCadastrarOrcamento() {
    setErroCadastro(null);

    if (!usuarioAdmin) {
      setErroCadastro(
        "Apenas administradores podem cadastrar orçamentos manualmente.",
      );
      return;
    }

    if (!novoOrcamento.nome.trim()) {
      setErroCadastro("Informe o nome do orçamento.");
      return;
    }

    if (novoOrcamento.valorPlanejado <= 0) {
      setErroCadastro("Informe um valor planejado maior que zero.");
      return;
    }

    if (!novoOrcamento.dataInicio) {
      setErroCadastro("Informe a data inicial.");
      return;
    }

    if (!novoOrcamento.dataFim) {
      setErroCadastro("Informe a data final.");
      return;
    }

    if (novoOrcamento.dataFim < novoOrcamento.dataInicio) {
      setErroCadastro(
        "A data final não pode ser anterior à data inicial.",
      );
      return;
    }

    if (
      !novoOrcamento.centroCusto?.trim() &&
      !novoOrcamento.categoria?.trim()
    ) {
      setErroCadastro(
        "Informe pelo menos um centro de custo ou uma categoria.",
      );
      return;
    }

    try {
      setSalvando(true);

      const cadastrado = await createBudget({
        ...novoOrcamento,
        nome: novoOrcamento.nome.trim(),
        centroCusto:
          novoOrcamento.centroCusto?.trim() || null,
        categoria:
          novoOrcamento.categoria?.trim() || null,
      });

      setOrcamentos((atuais) => [
        ...atuais,
        cadastrado,
      ]);

      setNovoOrcamento({
        nome: "",
        centroCusto: null,
        categoria: null,
        valorPlanejado: 0,
        dataInicio: "",
        dataFim: "",
      });

      setMostrarFormulario(false);
    } catch (error) {
      console.error("Erro ao cadastrar orçamento:", error);

      if (error instanceof Error) {
        setErroCadastro(error.message);
      } else {
        setErroCadastro(
          "Não foi possível cadastrar o orçamento.",
        );
      }
    } finally {
      setSalvando(false);
    }
  }

  if (carregando) {
    return (
      <div className="dashboard">
        <p>Carregando orçamentos...</p>
      </div>
    );
  }

  if (erro) {
    return (
      <div className="dashboard">
        <p>{erro}</p>
      </div>
    );
  }

  return (
    <div className="dashboard">
      <section
        id="orcamentos"
        className="budget-pipeline-section"
      >
        <div className="pipeline-summary">
          <div className="pipeline-summary-card">
            <span>Orçamento planejado</span>

            <strong>{formatarMoeda(totalPlanejado)}</strong>
          </div>

          <div className="pipeline-summary-card">
            <span>Valor utilizado</span>

            <strong>{formatarMoeda(totalUtilizado)}</strong>
          </div>

          <div className="pipeline-summary-card">
            <span>Saldo disponível</span>

            <strong>{formatarMoeda(totalDisponivel)}</strong>
          </div>

          <div className="pipeline-summary-card">
            <span>Utilização geral</span>

            <strong>
              {percentualGeral.toLocaleString("pt-BR", {
                minimumFractionDigits: 1,
                maximumFractionDigits: 1,
              })}
              %
            </strong>
          </div>
        </div>

        <BudgetPipelineChart dados={orcamentos} />
      </section>

      <section className="budget-pipeline-table-card">
        <div className="budget-pipeline-table-header">
          <div>
            <span className="budget-pipeline-table-eyebrow">
              CONTROLE ORÇAMENTÁRIO
            </span>

            <h2>Orçamentos empresariais</h2>

            <p>
              Acompanhe os valores planejados, despesas realizadas
              e recursos disponíveis por período e centro de custo.
            </p>
          </div>

          <div className="budget-pipeline-table-total">
            <span>Total planejado</span>

            <strong>{formatarMoeda(totalPlanejado)}</strong>

            <small>
              {orcamentos.length}{" "}
              {orcamentos.length === 1
                ? "orçamento"
                : "orçamentos"}
            </small>

            {usuarioAdmin && (
              <button
                type="button"
                className="budget-new-opportunity-button"
                onClick={() => {
                  setErroCadastro(null);
                  setMostrarFormulario((atual) => !atual);
                }}
              >
                {mostrarFormulario
                  ? "Cancelar"
                  : "+ Novo orçamento"}
              </button>
            )}
          </div>
        </div>

        {usuarioAdmin && mostrarFormulario && (
          <div className="budget-opportunity-form">
            <div className="budget-opportunity-form-header">
              <div>
                <span className="budget-pipeline-table-eyebrow">
                  NOVO ORÇAMENTO
                </span>

                <h3>Cadastrar orçamento financeiro</h3>

                <p>
                  Defina o valor planejado e os critérios usados
                  para acompanhar as despesas realizadas.
                </p>
              </div>
            </div>

            <div className="budget-opportunity-form-grid">
              <label>
                <span>Nome do orçamento</span>

                <input
                  type="text"
                  value={novoOrcamento.nome}
                  placeholder="Ex.: Administrativo 2026"
                  onChange={(event) =>
                    setNovoOrcamento((atual) => ({
                      ...atual,
                      nome: event.target.value,
                    }))
                  }
                />
              </label>

              <label>
                <span>Valor planejado</span>

                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={
                    novoOrcamento.valorPlanejado === 0
                      ? ""
                      : novoOrcamento.valorPlanejado
                  }
                  placeholder="0,00"
                  onChange={(event) =>
                    setNovoOrcamento((atual) => ({
                      ...atual,
                      valorPlanejado:
                        Number(event.target.value),
                    }))
                  }
                />
              </label>

              <label>
                <span>Centro de custo</span>

                <input
                  type="text"
                  value={novoOrcamento.centroCusto ?? ""}
                  placeholder="Ex.: Despesas Administrativas"
                  onChange={(event) =>
                    setNovoOrcamento((atual) => ({
                      ...atual,
                      centroCusto:
                        event.target.value || null,
                    }))
                  }
                />
              </label>

              <label>
                <span>Categoria</span>

                <input
                  type="text"
                  value={novoOrcamento.categoria ?? ""}
                  placeholder="Ex.: Serviços"
                  onChange={(event) =>
                    setNovoOrcamento((atual) => ({
                      ...atual,
                      categoria:
                        event.target.value || null,
                    }))
                  }
                />
              </label>

              <label>
                <span>Data inicial</span>

                <input
                  type="date"
                  value={novoOrcamento.dataInicio}
                  onChange={(event) =>
                    setNovoOrcamento((atual) => ({
                      ...atual,
                      dataInicio: event.target.value,
                    }))
                  }
                />
              </label>

              <label>
                <span>Data final</span>

                <input
                  type="date"
                  value={novoOrcamento.dataFim}
                  min={novoOrcamento.dataInicio || undefined}
                  onChange={(event) =>
                    setNovoOrcamento((atual) => ({
                      ...atual,
                      dataFim: event.target.value,
                    }))
                  }
                />
              </label>
            </div>

            {erroCadastro && (
              <p className="budget-opportunity-form-error">
                {erroCadastro}
              </p>
            )}

            <div className="budget-opportunity-form-actions">
              <button
                type="button"
                onClick={() => {
                  setMostrarFormulario(false);
                  setErroCadastro(null);
                }}
                disabled={salvando}
              >
                Cancelar
              </button>

              <button
                type="button"
                onClick={() =>
                  void handleCadastrarOrcamento()
                }
                disabled={salvando}
              >
                {salvando
                  ? "Salvando..."
                  : "Cadastrar orçamento"}
              </button>
            </div>
          </div>
        )}

        {orcamentos.length > 0 ? (
          <div className="table-wrapper">
            <table className="budget-pipeline-table">
              <thead>
                <tr>
                  <th className="budget-rank-column">#</th>
                  <th>Orçamento</th>
                  <th>Planejado</th>
                  <th>Utilizado</th>
                  <th>Disponível</th>
                  <th>Utilização</th>
                  <th>Período</th>
                </tr>
              </thead>

              <tbody>
                {orcamentos.map((item, index) => {
                  const percentual = Number(
                    item.percentualUtilizado,
                  );

                  const percentualLimitado = Math.min(
                    Math.max(percentual, 0),
                    100,
                  );

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
                            {item.nome
                              .charAt(0)
                              .toUpperCase()}
                          </span>

                          <div>
                            <strong>{item.nome}</strong>

                            <small>
                              {item.centroCusto ||
                                item.categoria ||
                                "Orçamento geral"}
                            </small>
                          </div>
                        </div>
                      </td>

                      <td>
                        <strong className="budget-value">
                          {formatarMoeda(
                            Number(item.valorPlanejado),
                          )}
                        </strong>
                      </td>

                      <td>
                        <strong className="budget-value">
                          {formatarMoeda(
                            Number(item.valorUtilizado),
                          )}
                        </strong>
                      </td>

                      <td>
                        <div className="budget-weighted-value">
                          <strong>
                            {formatarMoeda(
                              Number(item.valorDisponivel),
                            )}
                          </strong>

                          <small>Saldo do orçamento</small>
                        </div>
                      </td>

                      <td>
                        <div className="budget-probability">
                          <div className="budget-probability-top">
                            <strong>
                              {percentual.toLocaleString(
                                "pt-BR",
                                {
                                  minimumFractionDigits: 1,
                                  maximumFractionDigits: 1,
                                },
                              )}
                              %
                            </strong>

                            {percentual >= 100 && (
                              <span className="budget-high-chance">
                                Limite atingido
                              </span>
                            )}
                          </div>

                          <div
                            className="budget-progress"
                            role="progressbar"
                            aria-label={`Utilização do orçamento ${item.nome}`}
                            aria-valuenow={percentual}
                            aria-valuemin={0}
                            aria-valuemax={100}
                          >
                            <div
                              className="budget-progress-fill"
                              style={{
                                width: `${percentualLimitado}%`,
                              }}
                            />
                          </div>
                        </div>
                      </td>

                      <td>
                        <div className="budget-weighted-value">
                          <strong>
                            {formatarData(item.dataInicio)}
                          </strong>

                          <small>
                            até {formatarData(item.dataFim)}
                          </small>
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
            Nenhum orçamento financeiro cadastrado.
          </div>
        )}
      </section>
    </div>
  );
}

export default Budgets;