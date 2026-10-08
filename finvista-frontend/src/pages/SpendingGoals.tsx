import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";

import {
  criarMeta,
  listarMetas,
  type CreateSpendingGoalRequest,
  type SpendingGoal,
} from "../services/spendingGoalService";

import {
  getCostCenters,
  type CostCenterData,
} from "../services/costCenterService";

import {
  getCategoriesByCostCenter,
  getExpenseDistribution,
  type ExpenseDistributionData,
} from "../services/expenseDistributionService";

import { obterUsuarioAtual, type AuthUser } from "../services/authService";

import "../styles/spending-goals.css";
import GoalProgress from "../components/GoalProgress";
import { periodoMeta } from "../services/goalPresentation";

const formatarMoeda = (valor: number) =>
  new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
  }).format(valor || 0);

const formatarData = (data: string) => {
  if (!data) {
    return "-";
  }

  return new Intl.DateTimeFormat("pt-BR").format(new Date(`${data}T00:00:00`));
};

function SpendingGoals() {
  const [metas, setMetas] = useState<SpendingGoal[]>([]);

  const [usuario, setUsuario] = useState<AuthUser | null>(null);

  const [centrosCusto, setCentrosCusto] = useState<CostCenterData[]>([]);

  const [distribuicaoDespesas, setDistribuicaoDespesas] = useState<
    ExpenseDistributionData[]
  >([]);

  const [categoriasFormulario, setCategoriasFormulario] = useState<string[]>(
    [],
  );

  const [carregandoCategoriasFormulario, setCarregandoCategoriasFormulario] =
    useState(false);

  const [filtroCentroCusto, setFiltroCentroCusto] = useState("");

  const [filtroCategoria, setFiltroCategoria] = useState("");

  const [carregando, setCarregando] = useState(true);

  const [salvando, setSalvando] = useState(false);

  const [erro, setErro] = useState("");

  const [mostrarFormulario, setMostrarFormulario] = useState(false);

  const [formulario, setFormulario] = useState<CreateSpendingGoalRequest>({
    tipo: "MENSAL",
    dataInicio: "",
    dataFim: "",
    valorLimite: 0,
    percentualAlerta: 80,
    categoria: null,
    centroCusto: null,
  });

  const carregarMetas = async (
    centroCusto = filtroCentroCusto,
    categoria = filtroCategoria,
  ) => {
    try {
      setCarregando(true);
      setErro("");

      const dados = await listarMetas({
        centroCusto: centroCusto || null,
        categoria: categoria || null,
      });

      setMetas(dados);
    } catch (error) {
      setErro(
        error instanceof Error
          ? error.message
          : "Não foi possível carregar as metas.",
      );
    } finally {
      setCarregando(false);
    }
  };
  useEffect(() => {
    let ativo = true;

    async function carregarPagina() {
      try {
        setCarregando(true);
        setErro("");

        const resultados = await Promise.allSettled([
          listarMetas(),
          obterUsuarioAtual(),
          getCostCenters(true),
          getExpenseDistribution(true),
        ]);

        if (!ativo) {
          return;
        }

        const [
          resultadoMetas,
          resultadoUsuario,
          resultadoCentros,
          resultadoDespesas,
        ] = resultados;

        if (resultadoMetas.status === "fulfilled") {
          setMetas(resultadoMetas.value);
        } else {
          throw resultadoMetas.reason;
        }

        if (resultadoUsuario.status === "fulfilled") {
          setUsuario(resultadoUsuario.value);
        } else {
          throw resultadoUsuario.reason;
        }

        if (resultadoCentros.status === "fulfilled") {
          setCentrosCusto(resultadoCentros.value);
        } else {
          console.error(
            "Não foi possível carregar centros de custo:",
            resultadoCentros.reason,
          );

          setCentrosCusto([]);
        }

        if (resultadoDespesas.status === "fulfilled") {
          setDistribuicaoDespesas(resultadoDespesas.value);
        } else {
          console.error(
            "Não foi possível carregar categorias:",
            resultadoDespesas.reason,
          );

          setDistribuicaoDespesas([]);
        }
      } catch (error) {
        if (ativo) {
          setErro(
            error instanceof Error
              ? error.message
              : "Não foi possível carregar as metas.",
          );
        }
      } finally {
        if (ativo) {
          setCarregando(false);
        }
      }
    }

    void carregarPagina();

    return () => {
      ativo = false;
    };
  }, []);

  const usuarioAdmin = usuario?.perfil === "ADMIN";

  /*
   * =========================================================
   * OPÇÕES DOS FILTROS
   * =========================================================
   */

  const opcoesCentroCusto = useMemo(() => {
    const nomes = new Set<string>();

    centrosCusto.forEach((item) => {
      const nome = item.nome?.trim();

      if (nome) {
        nomes.add(nome);
      }
    });

    metas.forEach((meta) => {
      const nome = meta.centroCusto?.trim();

      if (nome) {
        nomes.add(nome);
      }
    });

    return Array.from(nomes).sort((a, b) => a.localeCompare(b, "pt-BR"));
  }, [centrosCusto, metas]);

  const opcoesCategoria = useMemo(() => {
    const categorias = new Set<string>();

    distribuicaoDespesas.forEach((item) => {
      const categoria = item.categoria?.trim();

      if (categoria) {
        categorias.add(categoria);
      }
    });

    metas.forEach((meta) => {
      const categoria = meta.categoria?.trim();

      if (categoria) {
        categorias.add(categoria);
      }
    });

    return Array.from(categorias).sort((a, b) => a.localeCompare(b, "pt-BR"));
  }, [distribuicaoDespesas, metas]);

  /*
   * =========================================================
   * FILTRAGEM DAS METAS
   * =========================================================
   */

  const metasFiltradas = useMemo(
    () =>
      [...metas].sort((a, b) => {
        const etapa = (m: SpendingGoal) =>
          m.situacaoTemporal === "EM_ANDAMENTO"
            ? 0
            : m.situacaoTemporal === "FUTURA"
              ? 1
              : 2;
        return (
          etapa(a) - etapa(b) ||
          (etapa(a) === 0
            ? Number(b.percentualUtilizado) - Number(a.percentualUtilizado)
            : etapa(a) === 1
              ? a.dataInicio.localeCompare(b.dataInicio)
              : b.dataFim.localeCompare(a.dataFim))
        );
      }),
    [metas],
  );

  const filtrosAtivos = Boolean(filtroCentroCusto) || Boolean(filtroCategoria);

  const limparFiltros = () => {
    setFiltroCentroCusto("");
    setFiltroCategoria("");

    void carregarMetas("", "");
  };

  /*
   * =========================================================
   * RESUMO
   * =========================================================
   */

  const resumo = useMemo(() => {
    const limiteTotal = metasFiltradas.reduce(
      (total, meta) => total + Number(meta.valorLimite),
      0,
    );

    const gastoTotal = metasFiltradas.reduce(
      (total, meta) => total + Number(meta.gastoFiltrado),
      0,
    );

    const saldoTotal = metasFiltradas.reduce(
      (total, meta) => total + Number(meta.saldoMeta),
      0,
    );

    const metasAtencao = metasFiltradas.filter(
      (meta) =>
        meta.situacaoTemporal === "EM_ANDAMENTO" &&
        (meta.status === "ALERTA" || meta.status === "EXCEDIDA"),
    ).length;

    return {
      limiteTotal,
      gastoTotal,
      saldoTotal,
      metasAtencao,
    };
  }, [metasFiltradas]);

  const atualizarFormulario = (
    campo: keyof CreateSpendingGoalRequest,
    valor: string | number | null,
  ) => {
    setFormulario((atual) => ({
      ...atual,
      [campo]: valor,
    }));
  };

  const alterarCentroCustoFormulario = async (centroCusto: string) => {
    atualizarFormulario("centroCusto", centroCusto || null);
    atualizarFormulario("categoria", null);

    if (!centroCusto) {
      setCategoriasFormulario([]);
      return;
    }

    try {
      setCarregandoCategoriasFormulario(true);

      const categorias = await getCategoriesByCostCenter(centroCusto);

      setCategoriasFormulario(categorias);
    } catch (error) {
      console.error(
        "Não foi possível carregar categorias do centro de custo:",
        error,
      );

      setCategoriasFormulario([]);
    } finally {
      setCarregandoCategoriasFormulario(false);
    }
  };

  const salvarMeta = async (event: FormEvent) => {
    event.preventDefault();

    if (
      !formulario.dataInicio ||
      !formulario.dataFim ||
      formulario.valorLimite <= 0
    ) {
      setErro("Preencha o período e informe um valor limite maior que zero.");

      return;
    }

    if (formulario.percentualAlerta <= 0 || formulario.percentualAlerta > 100) {
      setErro("O percentual de alerta deve estar entre 1% e 100%.");

      return;
    }

    if (formulario.dataFim < formulario.dataInicio) {
      setErro("A data final não pode ser anterior à data inicial.");

      return;
    }

    try {
      setSalvando(true);
      setErro("");

      await criarMeta(formulario);

      setFormulario({
        tipo: "MENSAL",
        dataInicio: "",
        dataFim: "",
        valorLimite: 0,
        percentualAlerta: 80,
        categoria: null,
        centroCusto: null,
      });

      setMostrarFormulario(false);

      await carregarMetas();
    } catch (error) {
      setErro(
        error instanceof Error
          ? error.message
          : "Não foi possível criar a meta.",
      );
    } finally {
      setSalvando(false);
    }
  };

  return (
    <section className="metas-page">
      <div className="metas-header">
        <div>
          <span className="metas-eyebrow">PLANEJAMENTO FINANCEIRO</span>

          <h2>Metas financeiras</h2>

          <p>
            Defina limites de gastos, acompanhe o consumo do orçamento e
            identifique riscos antes que os limites sejam ultrapassados.
          </p>
        </div>

        {usuarioAdmin && (
          <button
            type="button"
            className="metas-primary-button"
            onClick={() => setMostrarFormulario((atual) => !atual)}
          >
            <span>+</span>
            Nova meta
          </button>
        )}
      </div>

      {erro && <div className="metas-alerta-erro">{erro}</div>}

      {usuarioAdmin && mostrarFormulario && (
        <form className="metas-formulario" onSubmit={salvarMeta}>
          <div className="metas-form-grid">
            <label>
              Tipo da meta
              <select
                value={formulario.tipo}
                onChange={(event) =>
                  atualizarFormulario("tipo", event.target.value)
                }
              >
                <option value="MENSAL">Mensal</option>
                <option value="SEMESTRAL">Semestral</option>
              </select>
            </label>

            <label>
              Valor limite
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={formulario.valorLimite || ""}
                onChange={(event) =>
                  atualizarFormulario("valorLimite", Number(event.target.value))
                }
                placeholder="Ex.: 100000,00"
              />
            </label>

            <label>
              Data inicial
              <input
                type="date"
                value={formulario.dataInicio}
                onChange={(event) =>
                  atualizarFormulario("dataInicio", event.target.value)
                }
              />
            </label>

            <label>
              Data final
              <input
                type="date"
                value={formulario.dataFim}
                onChange={(event) =>
                  atualizarFormulario("dataFim", event.target.value)
                }
              />
            </label>

            <label>
              Alerta a partir de
              <div className="metas-input-percentual">
                <input
                  type="number"
                  min="1"
                  max="100"
                  value={formulario.percentualAlerta}
                  onChange={(event) =>
                    atualizarFormulario(
                      "percentualAlerta",
                      Number(event.target.value),
                    )
                  }
                />
                <span>%</span>
              </div>
            </label>
          </div>

          <div className="metas-form-grid metas-form-grid-contexto">
            <label>
              Centro de custo
              <select
                value={formulario.centroCusto ?? ""}
                onChange={(event) => {
                  void alterarCentroCustoFormulario(event.target.value);
                }}
              >
                <option value="">Todos os centros de custo</option>

                {opcoesCentroCusto.map((centro) => (
                  <option key={centro} value={centro}>
                    {centro}
                  </option>
                ))}
              </select>
            </label>

            <label>
              Categoria
              <select
                value={formulario.categoria ?? ""}
                disabled={
                  !formulario.centroCusto || carregandoCategoriasFormulario
                }
                onChange={(event) =>
                  atualizarFormulario("categoria", event.target.value || null)
                }
              >
                <option value="">
                  {carregandoCategoriasFormulario
                    ? "Carregando categorias..."
                    : formulario.centroCusto
                      ? "Todas as categorias"
                      : "Selecione um centro de custo"}
                </option>

                {categoriasFormulario.map((categoria) => (
                  <option key={categoria} value={categoria}>
                    {categoria}
                  </option>
                ))}
              </select>
            </label>
          </div>

          <div className="metas-formulario-footer">
            <span>
              O alerta não bloqueia lançamentos. Ele funciona como indicador
              gerencial.
            </span>

            <div>
              <button
                type="button"
                className="metas-secondary-button"
                onClick={() => setMostrarFormulario(false)}
              >
                Cancelar
              </button>

              <button
                type="submit"
                className="metas-primary-button"
                disabled={salvando}
              >
                {salvando ? "Salvando..." : "Criar meta"}
              </button>
            </div>
          </div>
        </form>
      )}

      <div className="metas-filtros-card">
        <div className="metas-filtros-header">
          <div>
            <span className="metas-filtros-eyebrow">VISUALIZAÇÃO</span>

            <h3>Analisar despesas das metas</h3>

            <p>
              Consulte as despesas por centro de custo ou categoria sem criar
              uma meta para cada grupo. Saldo e alertas consideram o consumo
              total de cada meta.
            </p>
          </div>

          {filtrosAtivos && (
            <button
              type="button"
              className="metas-limpar-filtros"
              disabled={carregando}
              onClick={limparFiltros}
            >
              Limpar filtros
            </button>
          )}
        </div>

        <div className="metas-filtros-grid">
          <label>
            <span>Centro de custo</span>

            <select
              value={filtroCentroCusto}
              disabled={carregando}
              onChange={(event) => {
                const novoCentroCusto = event.target.value;

                setFiltroCentroCusto(novoCentroCusto);
                setFiltroCategoria("");

                void carregarMetas(novoCentroCusto, "");
              }}
            >
              <option value="">Todos os centros de custo</option>

              {opcoesCentroCusto.map((centro) => (
                <option key={centro} value={centro}>
                  {centro}
                </option>
              ))}
            </select>
          </label>

          <label>
            <span>Categoria</span>

            <select
              value={filtroCategoria}
              disabled={carregando}
              onChange={(event) => {
                const novaCategoria = event.target.value;

                setFiltroCategoria(novaCategoria);

                void carregarMetas(filtroCentroCusto, novaCategoria);
              }}
            >
              <option value="">Todas as categorias</option>

              {opcoesCategoria.map((categoria) => (
                <option key={categoria} value={categoria}>
                  {categoria}
                </option>
              ))}
            </select>
          </label>

          <div className="metas-filtro-resultado">
            <span>Resultados</span>

            <strong>{metasFiltradas.length}</strong>

            <small>
              de {metas.length} {metas.length === 1 ? "meta" : "metas"}
            </small>
          </div>
        </div>
      </div>

      <div className="metas-resumo">
        <article>
          <span>Limite planejado</span>

          <strong>{formatarMoeda(resumo.limiteTotal)}</strong>

          <small>
            Limites das metas cadastradas, sem alteração pelos filtros
          </small>
        </article>

        <article>
          <span>
            {filtrosAtivos ? "Gastos no filtro" : "Gastos acumulados"}
          </span>

          <strong>{formatarMoeda(resumo.gastoTotal)}</strong>

          <small>
            {filtrosAtivos
              ? "Despesas selecionadas nos períodos das metas"
              : "Valor consumido nas metas"}
          </small>
        </article>

        <article>
          <span>Saldo disponível</span>

          <strong>{formatarMoeda(resumo.saldoTotal)}</strong>

          <small>Considera o consumo total, sem filtros</small>
        </article>

        <article>
          <span>Requer atenção</span>

          <strong>{resumo.metasAtencao}</strong>

          <small>Alertas pelo consumo total, sem filtros</small>
        </article>
      </div>

      <div className="metas-content">
        <div className="metas-section-heading">
          <div>
            <span>ACOMPANHAMENTO</span>

            <h3>Metas cadastradas</h3>
          </div>

          <strong>{metasFiltradas.length}</strong>
        </div>

        {carregando ? (
          <div className="metas-estado">Carregando metas...</div>
        ) : metas.length === 0 ? (
          <div className="metas-vazio">
            <div className="metas-vazio-icon">◎</div>

            <h3>Nenhuma meta cadastrada</h3>

            <p>
              Crie a primeira meta financeira para começar a acompanhar limites
              e gastos.
            </p>

            {usuarioAdmin && (
              <button
                type="button"
                className="metas-empty-button"
                onClick={() => setMostrarFormulario(true)}
              >
                + Criar primeira meta
              </button>
            )}
          </div>
        ) : metasFiltradas.length === 0 ? (
          <div className="metas-vazio">
            <div className="metas-vazio-icon">◌</div>

            <h3>Nenhuma meta encontrada</h3>

            <p>Não existem metas que correspondam aos filtros selecionados.</p>

            <button
              type="button"
              className="metas-empty-button"
              onClick={limparFiltros}
            >
              Limpar filtros
            </button>
          </div>
        ) : (
          <div className="metas-lista">
            {metasFiltradas.map((meta) => {
              return (
                <article className="meta-card" key={meta.id}>
                  <div className="meta-card-top">
                    <div>
                      <span className="meta-tipo">{meta.tipo}</span>

                      <h3>Meta de gastos #{meta.id}</h3>

                      <small>
                        {formatarData(meta.dataInicio)}
                        {" — "}
                        {formatarData(meta.dataFim)}
                      </small>
                    </div>

                    <span className="meta-status">{periodoMeta(meta)}</span>
                  </div>

                  {(meta.categoria || meta.centroCusto) && (
                    <div className="meta-contexto">
                      {meta.centroCusto && (
                        <span>
                          <small>Centro de custo</small>

                          <strong>{meta.centroCusto}</strong>
                        </span>
                      )}

                      {meta.categoria && (
                        <span>
                          <small>Categoria</small>

                          <strong>{meta.categoria}</strong>
                        </span>
                      )}
                    </div>
                  )}

                  <div className="meta-valores">
                    <div>
                      <span>Limite</span>

                      <strong>{formatarMoeda(Number(meta.valorLimite))}</strong>
                    </div>

                    <div>
                      <span>Utilizado total</span>

                      <strong>{formatarMoeda(Number(meta.gastoAtual))}</strong>
                    </div>

                    <div>
                      <span>Saldo total</span>

                      <strong>{formatarMoeda(Number(meta.saldoMeta))}</strong>
                    </div>
                  </div>

                  {filtrosAtivos && (
                    <div className="meta-contexto">
                      <span>
                        <small>Filtro de despesas</small>
                        <strong>
                          {meta.filtroCentroCusto || "Todos os centros"}
                          {" · "}
                          {meta.filtroCategoria || "Todas as categorias"}
                        </strong>
                      </span>
                      <span>
                        <small>Gastos no filtro</small>
                        <strong>
                          {formatarMoeda(Number(meta.gastoFiltrado))}
                        </strong>
                      </span>
                      <span>
                        <small>Participação no limite da meta</small>
                        <strong>
                          {Number(meta.percentualFiltrado).toFixed(1)}%
                        </strong>
                      </span>
                    </div>
                  )}

                  <GoalProgress
                    key={`${meta.id}-${meta.gastoAtual}-${meta.dataReferencia}`}
                    meta={meta}
                  />
                </article>
              );
            })}
          </div>
        )}
      </div>
    </section>
  );
}

export default SpendingGoals;
