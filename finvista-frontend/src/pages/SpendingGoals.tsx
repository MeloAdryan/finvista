import { useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent } from "react";
import { useLocation } from "react-router-dom";

import {
  excluirMeta,
  listarMetas,
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

import {
  criarRecorrencia,
  excluirRecorrencia,
  previaRecorrencia,
  recorrencias,
  type RecurrenceRequest,
  type RecurrencePreview,
} from "../services/goalRecurrenceService";
import "../styles/goal-recurrence.css";
import "../styles/spending-goals.css";
import "../styles/goal-actions.css";
import "../styles/phase3-goal-cards.css";
import "../styles/goal-expanded.css";
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
  const { hash } = useLocation();
  const [periodosSelecionados, setPeriodosSelecionados] = useState<
    Record<string, { id: number; hash: string }>
  >({});
  const [previa, setPrevia] = useState<{
    assinatura: string;
    dados: RecurrencePreview;
  } | null>(null);
  const [gerandoPrevia, setGerandoPrevia] = useState(false);
  const gravando = useRef(false);
  const [metas, setMetas] = useState<SpendingGoal[]>([]);
  const [excluindo, setExcluindo] = useState<number | null>(null);
  const [sucessoExclusao, setSucessoExclusao] = useState("");

  const [usuario, setUsuario] = useState<AuthUser | null>(null);

  const [centrosCusto, setCentrosCusto] = useState<CostCenterData[]>([]);

  const [, setDistribuicaoDespesas] = useState<ExpenseDistributionData[]>([]);

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

  const [formulario, setFormulario] = useState<RecurrenceRequest>({
    nome: "",
    recorrencia: "MENSAL",
    dataInicio: "",
    dataFim: "",
    valorLimite: 0,
    percentualAlerta: 80,
    categoria: null,
    centroCusto: null,
  });

  useEffect(() => {
    if (carregando || !/^#meta-\d+$/.test(hash)) return;
    const alvo = document.getElementById(hash.slice(1));
    if (alvo) {
      alvo.scrollIntoView({ block: "center" });
      alvo.focus({ preventScroll: true });
    }
  }, [hash, carregando]);
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

  /*
   * =========================================================
   * FILTRAGEM DAS METAS
   * =========================================================
   */

  const metasFiltradas = useMemo(
    () =>
      [...new Map(metas.map((m) => [m.serie?.id ?? `meta-${m.id}`, m])).keys()]
        .map((chave) => {
          const periodos = metas.filter(
            (m) => (m.serie?.id ?? `meta-${m.id}`) === chave,
          );
          const idHash = /^#meta-\d+$/.test(hash)
            ? Number(hash.slice(6))
            : null;
          const selecao = periodosSelecionados[chave];
          return (
            periodos.find(
              (m) => selecao?.hash === hash && m.id === selecao.id,
            ) ??
            periodos.find((m) => m.id === idHash) ??
            periodos.find((m) => m.situacaoTemporal === "EM_ANDAMENTO") ??
            [...periodos]
              .filter((m) => m.situacaoTemporal === "FUTURA")
              .sort((a, b) => a.dataInicio.localeCompare(b.dataInicio))[0] ??
            [...periodos].sort((a, b) => b.dataFim.localeCompare(a.dataFim))[0]
          );
        })
        .sort((a, b) => {
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
    [metas, periodosSelecionados, hash],
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
    campo: keyof RecurrenceRequest,
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

  async function visualizarPeriodos() {
    const pedido = { ...formulario };
    setGerandoPrevia(true);
    setPrevia(null);
    setErro("");
    try {
      const dados = await previaRecorrencia(pedido);
      setPrevia({ assinatura: JSON.stringify(pedido), dados });
    } catch (error) {
      setErro(
        error instanceof Error
          ? error.message
          : "Não foi possível gerar a prévia.",
      );
    } finally {
      setGerandoPrevia(false);
    }
  }

  const salvarMeta = async (event: FormEvent) => {
    event.preventDefault();
    if (gravando.current) return;
    if (!previa || previa.assinatura !== JSON.stringify(formulario)) {
      setErro(
        "Visualize os períodos antes de salvar. Se alterar os dados, gere uma nova prévia.",
      );
      return;
    }

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
      gravando.current = true;
      setSalvando(true);
      setErro("");

      await criarRecorrencia(formulario);
      setPrevia(null);

      setFormulario({
        nome: "",
        recorrencia: "MENSAL",
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
      gravando.current = false;
      setSalvando(false);
    }
  };

  async function confirmarExclusao(meta: SpendingGoal) {
    if (!usuarioAdmin || excluindo !== null) return;
    const serie = meta.serie;
    const quantidade = serie
      ? metas.filter((m) => m.serie?.id === serie.id).length
      : 1;
    const descricao = serie
      ? `Excluir a meta "${serie.nome}" e todos os seus ${quantidade} períodos, inclusive encerrados?`
      : `Excluir a meta #${meta.id}, de ${formatarData(meta.dataInicio)} a ${formatarData(meta.dataFim)}?`;
    if (
      !window.confirm(
        `${descricao}\n\nA exclusão é definitiva. Os lançamentos financeiros serão preservados.`,
      )
    )
      return;
    setExcluindo(meta.id);
    setErro("");
    setSucessoExclusao("");
    try {
      if (serie) {
        await excluirRecorrencia(serie.id);
        setMetas((atuais) => atuais.filter((m) => m.serie?.id !== serie.id));
      } else {
        await excluirMeta(meta.id);
        setMetas((atuais) => atuais.filter((m) => m.id !== meta.id));
      }
      setSucessoExclusao(
        serie ? `Meta "${serie.nome}" excluída.` : `Meta #${meta.id} excluída.`,
      );
    } catch (error) {
      setErro(
        error instanceof Error
          ? error.message
          : "Não foi possível excluir a meta.",
      );
    } finally {
      setExcluindo(null);
    }
  }

  return (
    <section className="metas-page">
      <div role="status" aria-live="polite" className="meta-exclusao-status">
        {sucessoExclusao}
      </div>
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
            disabled={salvando}
            onClick={() => {
              setMostrarFormulario((atual) => !atual);
              setPrevia(null);
            }}
          >
            <span>+</span>
            Nova meta
          </button>
        )}
      </div>

      {erro && <div className="metas-alerta-erro">{erro}</div>}

      {usuarioAdmin && mostrarFormulario && (
        <form className="metas-formulario" onSubmit={salvarMeta}>
          <fieldset className="meta-cadastro-campos" disabled={salvando}>
            <legend className="meta-cadastro-legenda">
              Cadastrar meta e períodos
            </legend>
            <div className="metas-form-grid">
              <label>
                Nome da meta
                <input
                  maxLength={120}
                  required
                  value={formulario.nome}
                  onChange={(event) =>
                    atualizarFormulario("nome", event.target.value)
                  }
                  placeholder="Ex.: Despesas operacionais"
                />
              </label>
              <label>
                Recorrência
                <select
                  aria-label="Recorrência"
                  value={formulario.recorrencia}
                  onChange={(event) =>
                    atualizarFormulario("recorrencia", event.target.value)
                  }
                >
                  {Object.entries(recorrencias).map(([valor, nome]) => (
                    <option key={valor} value={valor}>
                      {nome}
                    </option>
                  ))}
                </select>
              </label>

              <label>
                {formulario.recorrencia === "SEM_RECORRENCIA"
                  ? "Limite para toda a vigência"
                  : "Limite por período completo"}
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={formulario.valorLimite || ""}
                  onChange={(event) =>
                    atualizarFormulario(
                      "valorLimite",
                      Number(event.target.value),
                    )
                  }
                  placeholder="Ex.: 100000,00"
                />
              </label>

              <label>
                Início da vigência
                <input
                  type="date"
                  value={formulario.dataInicio}
                  onChange={(event) =>
                    atualizarFormulario("dataInicio", event.target.value)
                  }
                />
              </label>

              <label>
                Fim da vigência
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

            <div className="meta-recorrencia-previa">
              <p>
                Os ciclos seguem o calendário: mês, trimestre, semestre ou ano.
                Períodos incompletos recebem limite proporcional aos dias, sem
                transferência de saldo. Vigência de até dez anos e no máximo 120
                períodos.
              </p>
              <button
                type="button"
                className="metas-secondary-button"
                disabled={gerandoPrevia || salvando}
                onClick={() => void visualizarPeriodos()}
              >
                {gerandoPrevia ? "Gerando prévia…" : "Visualizar períodos"}
              </button>
              {previa && previa.assinatura === JSON.stringify(formulario) && (
                <div aria-live="polite">
                  <h3>
                    {previa.dados.periodos.length}{" "}
                    {previa.dados.periodos.length === 1
                      ? "período"
                      : "períodos"}{" "}
                    · Limite somado: {formatarMoeda(previa.dados.limiteTotal)}
                  </h3>
                  <div
                    className="meta-previa-tabela"
                    tabIndex={0}
                    aria-label="Períodos previstos"
                  >
                    <table>
                      <thead>
                        <tr>
                          <th>Início</th>
                          <th>Fim</th>
                          <th>Limite</th>
                          <th>Cálculo</th>
                        </tr>
                      </thead>
                      <tbody>
                        {previa.dados.periodos.map((periodo) => (
                          <tr key={periodo.inicio}>
                            <td>{formatarData(periodo.inicio)}</td>
                            <td>{formatarData(periodo.fim)}</td>
                            <td>{formatarMoeda(periodo.limite)}</td>
                            <td>
                              {periodo.proporcional
                                ? `${periodo.dias}/${periodo.diasPeriodoCompleto} dias`
                                : "Integral"}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
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
                  disabled={
                    salvando ||
                    !previa ||
                    previa.assinatura !== JSON.stringify(formulario)
                  }
                >
                  {salvando ? "Salvando..." : "Criar meta"}
                </button>
              </div>
            </div>
          </fieldset>
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

          <div className="metas-filtro-resultado">
            <span>Resultados</span>

            <strong>{metasFiltradas.length}</strong>

            <small>
              {metas.length}{" "}
              {metas.length === 1
                ? "período cadastrado"
                : "períodos cadastrados"}
            </small>
          </div>
        </div>
      </div>

      <div className="metas-resumo">
        <article>
          <span>Limite planejado</span>

          <strong>{formatarMoeda(resumo.limiteTotal)}</strong>

          <small>
            Limites dos períodos exibidos, sem alteração pelos filtros
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
              : "Valor consumido nos períodos exibidos"}
          </small>
        </article>

        <article>
          <span>Saldo disponível</span>

          <strong>{formatarMoeda(resumo.saldoTotal)}</strong>

          <small>Saldo nos períodos exibidos, sem filtros</small>
        </article>

        <article>
          <span>Requer atenção</span>

          <strong>{resumo.metasAtencao}</strong>

          <small>Alertas nos períodos exibidos, sem filtros</small>
        </article>
      </div>

      <p className="meta-resumo-periodos">
        Resumo dos períodos exibidos em cada cartão. Metas com escopos
        sobrepostos podem incluir a mesma despesa.
      </p>

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
                <article
                  className={`meta-card meta-risk-${meta.status.toLowerCase()}`}
                  key={meta.serie?.id ?? meta.id}
                  id={`meta-${meta.id}`}
                  tabIndex={-1}
                >
                  <div className="meta-card-top">
                    <div>
                      <h3>
                        {meta.serie?.nome ?? `Meta de gastos #${meta.id}`}
                      </h3>

                      <small>
                        <span aria-hidden="true">▦ </span>
                        {recorrencias[meta.tipo as keyof typeof recorrencias] ??
                          meta.tipo}{" "}
                        · {formatarData(meta.dataInicio)}
                        {" — "}
                        {formatarData(meta.dataFim)}
                      </small>
                    </div>

                    <span
                      className={`meta-status meta-temporal-${meta.situacaoTemporal.toLowerCase()}`}
                    >
                      {meta.situacaoTemporal === "ENCERRADA" && (
                        <span aria-hidden="true">▣ </span>
                      )}
                      {periodoMeta(meta)}
                    </span>
                  </div>

                  {meta.serie && (
                    <div className="meta-serie-periodo">
                      <p>
                        Vigência: {formatarData(meta.serie.inicio)} —{" "}
                        {formatarData(meta.serie.fim)} · Limite integral:{" "}
                        {formatarMoeda(meta.serie.limitePeriodo)}
                      </p>
                      <label>
                        Período em acompanhamento
                        <select
                          aria-label="Período em acompanhamento"
                          value={meta.id}
                          onChange={(event) =>
                            setPeriodosSelecionados((atuais) => ({
                              ...atuais,
                              [meta.serie!.id]: {
                                id: Number(event.target.value),
                                hash,
                              },
                            }))
                          }
                        >
                          {metas
                            .filter((m) => m.serie?.id === meta.serie!.id)
                            .sort((a, b) =>
                              a.dataInicio.localeCompare(b.dataInicio),
                            )
                            .map((m) => (
                              <option key={m.id} value={m.id}>
                                {formatarData(m.dataInicio)} —{" "}
                                {formatarData(m.dataFim)} · {periodoMeta(m)}
                              </option>
                            ))}
                        </select>
                      </label>
                      {Number(meta.valorLimite) !==
                        Number(meta.serie.limitePeriodo) && (
                        <small>
                          Limite proporcional aos dias deste período.
                        </small>
                      )}
                    </div>
                  )}

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

                  {usuarioAdmin && (
                    <div className="meta-acoes">
                      <button
                        type="button"
                        className="meta-excluir"
                        aria-label={`Excluir meta ${meta.serie?.nome ?? `de gastos #${meta.id}`}`}
                        disabled={excluindo !== null}
                        onClick={() => void confirmarExclusao(meta)}
                      >
                        {excluindo === meta.id ? "Excluindo…" : "Excluir meta"}
                      </button>
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
