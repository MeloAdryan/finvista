import { criterionNames, viewRange } from "../services/financialViewService";
import { useContext } from "react";
import {
  FinancialViewContext,
  FinancialViewUpdateContext,
} from "../contexts/financialViewContext";
import { useEffect, useMemo, useState } from "react";
import { useLocation } from "react-router-dom";

import {
  createBudget,
  getBudgetPipeline,
  type BudgetPipelineData,
  type CreateBudgetData,
} from "../services/budgetPipelineService";

import { obterUsuarioAtual, type AuthUser } from "../services/authService";

import BudgetPipelineChart from "../charts/BudgetPipelineChart";

import "../styles/dashboard.css";
import "../styles/budget-analysis.css";
import {
  chavePeriodo,
  dataOrcamento,
  detectarSobreposicoes,
  filtrarOrcamentos,
  moedaOrcamento,
  percentualOrcamento,
  type FiltroOrcamento,
} from "../services/budgetAnalysis";

function Budgets() {
  const viewFilter = useContext(FinancialViewContext);
  const updateView = useContext(FinancialViewUpdateContext);
  const { search } = useLocation();
  const [orcamentoAlvo] = useState(
    () => Number(new URLSearchParams(search).get("orcamento")) || null,
  );
  const [orcamentos, setOrcamentos] = useState<BudgetPipelineData[]>([]);
  const [filtro, setFiltro] = useState<FiltroOrcamento>({
    modo: "TODOS",
    inicio: "",
    fim: "",
  });
  const [inicio, setInicio] = useState("");
  const [fim, setFim] = useState("");
  const [erroFiltro, setErroFiltro] = useState("");
  const [usuario, setUsuario] = useState<AuthUser | null>(null);

  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  const [mostrarFormulario, setMostrarFormulario] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [erroCadastro, setErroCadastro] = useState<string | null>(null);

  const [novoOrcamento, setNovoOrcamento] = useState<CreateBudgetData>({
    nome: "",
    centroCusto: null,
    categoria: null,
    valorPlanejado: 0,
    dataInicio: "",
    dataFim: "",
  });

  useEffect(() => {
    let ativo = true;
    async function carregarPagina() {
      try {
        setCarregando(true);
        setErro(null);

        const [dadosOrcamentos, usuarioAtual] = await Promise.all([
          getBudgetPipeline(viewFilter ?? undefined),
          obterUsuarioAtual(),
        ]);

        if (!ativo) return;
        setOrcamentos(dadosOrcamentos);
        const recente =
          dadosOrcamentos.find((item) => item.id === orcamentoAlvo) ??
          [...dadosOrcamentos].sort(
            (a, b) =>
              b.dataInicio.localeCompare(a.dataInicio) ||
              b.dataFim.localeCompare(a.dataFim),
          )[0];
        if (recente && !viewFilter) {
          setFiltro({
            modo: "EXATO",
            inicio: recente.dataInicio,
            fim: recente.dataFim,
          });
          setInicio(recente.dataInicio);
          setFim(recente.dataFim);
        }
        setUsuario(usuarioAtual);
      } catch (error) {
        if (!ativo) return;
        console.error("Erro ao carregar orçamentos:", error);

        if (error instanceof Error) {
          setErro(error.message);
        } else {
          setErro("Não foi possível carregar os orçamentos.");
        }
      } finally {
        if (ativo) setCarregando(false);
      }
    }

    void carregarPagina();
    return () => {
      ativo = false;
    };
  }, [orcamentoAlvo, viewFilter]);

  const visiveis = useMemo(
    () => filtrarOrcamentos(orcamentos, filtro),
    [orcamentos, filtro],
  );
  const periodos = useMemo(
    () =>
      Array.from(
        new Map(orcamentos.map((item) => [chavePeriodo(item), item])).values(),
      ).sort(
        (a, b) =>
          b.dataInicio.localeCompare(a.dataInicio) ||
          b.dataFim.localeCompare(a.dataFim),
      ),
    [orcamentos],
  );
  const sobreposicoes = useMemo(
    () => detectarSobreposicoes(visiveis),
    [visiveis],
  );
  const totalPlanejado = visiveis.reduce(
    (s, item) => s + Number(item.valorPlanejado),
    0,
  );
  const totalUtilizado = visiveis.reduce(
    (s, item) => s + Number(item.valorUtilizado),
    0,
  );
  const totalDisponivel = visiveis.reduce(
    (s, item) => s + Number(item.valorDisponivel),
    0,
  );
  const excedentes = visiveis
    .map((item) => ({
      item,
      valor:
        Math.round(Number(item.valorUtilizado) * 100) -
        Math.round(Number(item.valorPlanejado) * 100),
    }))
    .filter(({ valor }) => valor > 0)
    .sort((a, b) => b.valor - a.valor);
  const totalExcedente =
    excedentes.reduce((total, { valor }) => total + valor, 0) / 100;
  const periodoAnalise = viewFilter ? viewRange(viewFilter) : null;

  function selecionarPeriodo(valor: string) {
    setErroFiltro("");
    if (valor === "TODOS") {
      setFiltro({ modo: "TODOS", inicio: "", fim: "" });
      setInicio("");
      setFim("");
      return;
    }
    const [inicioPeriodo, fimPeriodo] = valor.split("|");
    if (!inicioPeriodo || !fimPeriodo) return;
    setFiltro({ modo: "EXATO", inicio: inicioPeriodo, fim: fimPeriodo });
    setInicio(inicioPeriodo);
    setFim(fimPeriodo);
  }
  function aplicarIntervalo() {
    if (!inicio && !fim) {
      selecionarPeriodo("TODOS");
      return;
    }
    if (!inicio || !fim) {
      setErroFiltro("Informe as duas datas para selecionar um intervalo.");
      return;
    }
    if (fim < inicio) {
      setErroFiltro("A data final não pode ser anterior à inicial.");
      return;
    }
    setErroFiltro("");
    setFiltro({ modo: "INTERSECAO", inicio, fim });
  }
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
      setErroCadastro("A data final não pode ser anterior à data inicial.");
      return;
    }

    try {
      setSalvando(true);

      const cadastrado = await createBudget({
        ...novoOrcamento,
        nome: novoOrcamento.nome.trim(),
        centroCusto: novoOrcamento.centroCusto?.trim() || null,
        categoria: novoOrcamento.categoria?.trim() || null,
      });

      if (viewFilter && updateView)
        updateView({
          ...viewFilter,
          periodo: "PERSONALIZADO",
          inicio: cadastrado.dataInicio,
          fim: cadastrado.dataFim,
        });

      setOrcamentos((atuais) => [...atuais, cadastrado]);

      setFiltro({
        modo: "EXATO",
        inicio: cadastrado.dataInicio,
        fim: cadastrado.dataFim,
      });
      setInicio(cadastrado.dataInicio);
      setFim(cadastrado.dataFim);
      setErroFiltro("");
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
        setErroCadastro("Não foi possível cadastrar o orçamento.");
      }
    } finally {
      setSalvando(false);
    }
  }

  if (carregando)
    return (
      <div className="dashboard">
        <p role="status">Carregando orçamentos…</p>
      </div>
    );
  if (erro)
    return (
      <div className="dashboard">
        <p role="alert">{erro}</p>
      </div>
    );
  return (
    <div className="dashboard budget-analysis-page">
      <header className="ba-page-heading">
        <div>
          <span className="ba-eyebrow">Controle orçamentário</span>
          <h1>Planejado × registrado</h1>
          <p>
            {viewFilter
              ? `${criterionNames[viewFilter.criterio]} · ${
                  periodoAnalise
                    ? `${dataOrcamento(periodoAnalise.inicio)} a ${dataOrcamento(periodoAnalise.fim)}`
                    : "Todo o período"
                }`
              : "Acompanhe cada limite no seu período e identifique os excedentes."}
          </p>
        </div>
        {usuarioAdmin && (
          <button
            type="button"
            className="budget-new-opportunity-button"
            disabled={salvando}
            onClick={() => {
              setErroCadastro(null);
              setMostrarFormulario(!mostrarFormulario);
            }}
          >
            {mostrarFormulario ? "Cancelar" : "+ Novo orçamento"}
          </button>
        )}
      </header>
      {usuarioAdmin && mostrarFormulario && (
        <div className="budget-opportunity-form">
          <div className="budget-opportunity-form-header">
            <div>
              <span className="budget-pipeline-table-eyebrow">
                NOVO ORÇAMENTO
              </span>

              <h3>Cadastrar orçamento financeiro</h3>

              <p>
                Defina o valor planejado e os critérios usados para acompanhar
                as despesas realizadas.
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
                    valorPlanejado: Number(event.target.value),
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
                    centroCusto: event.target.value || null,
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
                    categoria: event.target.value || null,
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
            <p className="budget-opportunity-form-error">{erroCadastro}</p>
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
              onClick={() => void handleCadastrarOrcamento()}
              disabled={salvando}
            >
              {salvando ? "Salvando..." : "Cadastrar orçamento"}
            </button>
          </div>
        </div>
      )}

      <section
        className="ba-filter"
        aria-label="Filtrar orçamentos pelo período cadastrado"
      >
        <details>
          <summary>Filtrar por período cadastrado</summary>
          <div className="ba-filter-grid">
            <label>
              Período cadastrado
              <select
                value={
                  filtro.modo === "TODOS"
                    ? "TODOS"
                    : filtro.modo === "EXATO"
                      ? `${filtro.inicio}|${filtro.fim}`
                      : "PERSONALIZADO"
                }
                onChange={(e) => selecionarPeriodo(e.target.value)}
              >
                <option value="TODOS">Todos os períodos</option>
                <option value="PERSONALIZADO" disabled>
                  Intervalo personalizado
                </option>
                {periodos.map((item) => (
                  <option key={chavePeriodo(item)} value={chavePeriodo(item)}>
                    {dataOrcamento(item.dataInicio)} —{" "}
                    {dataOrcamento(item.dataFim)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Data inicial
              <input
                type="date"
                value={inicio}
                onChange={(e) => setInicio(e.target.value)}
              />
            </label>
            <label>
              Data final
              <input
                type="date"
                value={fim}
                min={inicio || undefined}
                onChange={(e) => setFim(e.target.value)}
              />
            </label>
            <button type="button" onClick={aplicarIntervalo}>
              Aplicar intervalo
            </button>
          </div>
        </details>
        <p>
          Exibido:{" "}
          {filtro.modo === "TODOS"
            ? "todos os períodos cadastrados"
            : `${dataOrcamento(filtro.inicio)} — ${dataOrcamento(filtro.fim)}`}
          {filtro.modo === "INTERSECAO"
            ? " · orçamentos cujo período cruza o intervalo"
            : filtro.modo === "EXATO"
              ? " · período exato cadastrado"
              : ""}
          . {visiveis.length} orçamento(s).
        </p>
        <p className="ba-note">
          O período cadastrado seleciona os orçamentos exibidos. O consumo segue
          o critério e o período de análise selecionados. O limite cadastrado
          não é rateado por dias.
        </p>
        {erroFiltro && (
          <p role="alert" className="ba-error">
            {erroFiltro}
          </p>
        )}
      </section>
      {sobreposicoes.length > 0 && (
        <aside className="ba-overlap">
          <strong>Os critérios de alguns orçamentos se sobrepõem.</strong>
          <p>
            Uma mesma despesa pode entrar em mais de um orçamento. A soma dos
            indicadores não representa despesas únicas da empresa.
          </p>
          <details>
            <summary>
              Ver possíveis sobreposições ({sobreposicoes.length})
            </summary>
            <ul>
              {sobreposicoes.map(({ primeiro, segundo }) => (
                <li key={`${primeiro.id}-${segundo.id}`}>
                  {primeiro.nome} ↔ {segundo.nome}
                </li>
              ))}
            </ul>
          </details>
        </aside>
      )}
      <section
        className="ba-summary"
        aria-label="Resumo dos orçamentos exibidos"
      >
        <div>
          <span>Planejado</span>
          <strong>{moedaOrcamento(totalPlanejado)}</strong>
        </div>
        <div>
          <span>Registrado nos orçamentos</span>
          <strong>{moedaOrcamento(totalUtilizado)}</strong>
        </div>
        <div>
          <span>Saldo do orçamento</span>
          <strong>{moedaOrcamento(totalDisponivel)}</strong>
        </div>
        <div>
          <span>Orçamentos excedidos</span>
          <strong>
            {excedentes.length} de {visiveis.length}
          </strong>
          <small>
            {moedaOrcamento(totalExcedente)} em excedentes individuais
          </small>
        </div>
      </section>
      {filtro.modo === "TODOS" && periodos.length > 1 && (
        <p className="ba-note">
          Você está somando limites de períodos diferentes. Confira a utilização
          individual nas barras.
        </p>
      )}
      <section
        className="ba-filter ba-comparison"
        aria-labelledby="ba-comparison-title"
      >
        <h2 id="ba-comparison-title">Comparativo por orçamento</h2>
        {visiveis.length === 0 && (
          <p>Nenhum orçamento para os filtros aplicados.</p>
        )}
        <ul className="ba-comparison-list">
          {visiveis.map((item) => {
            const percentual = Number(item.percentualUtilizado);
            const excedido =
              Math.round(Number(item.valorUtilizado) * 100) >
              Math.round(Number(item.valorPlanejado) * 100);
            return (
              <li key={item.id}>
                <div>
                  <strong>{item.nome}</strong>
                  <p>
                    {dataOrcamento(item.dataInicio)} a{" "}
                    {dataOrcamento(item.dataFim)}
                  </p>
                  <p>
                    {[item.categoria, item.centroCusto]
                      .filter(Boolean)
                      .join(" · ") || "Todas as despesas"}
                  </p>
                </div>
                <div>
                  <span>Planejado</span>
                  <strong>{moedaOrcamento(item.valorPlanejado)}</strong>
                </div>
                <div>
                  <span>Registrado</span>
                  <strong>{moedaOrcamento(item.valorUtilizado)}</strong>
                </div>
                <div className="ba-comparison-usage">
                  <strong>
                    {percentualOrcamento(percentual)}% ·{" "}
                    {excedido ? "Excedido" : "Dentro do limite"}
                  </strong>
                  <div className="ba-comparison-track" aria-hidden="true">
                    <div
                      className={excedido ? "ba-comparison-over" : ""}
                      style={{
                        width: `${Math.max(0, Math.min(100, percentual))}%`,
                      }}
                    />
                  </div>
                </div>
              </li>
            );
          })}
        </ul>
      </section>
      {visiveis.length > 0 && (
        <details className="ba-filter">
          <summary>Ver gráfico e detalhamento</summary>
          <BudgetPipelineChart dados={visiveis} />
        </details>
      )}
      <section className="ba-filter" aria-labelledby="ba-overages-title">
        <h2 id="ba-overages-title">Excedentes</h2>
        {excedentes.length === 0 ? (
          <p>Nenhum excedente nos orçamentos exibidos.</p>
        ) : (
          <div
            className="ba-table-scroll"
            tabIndex={0}
            role="region"
            aria-label="Tabela de excedentes"
          >
            <table className="ba-table">
              <thead>
                <tr>
                  <th scope="col">Orçamento</th>
                  <th scope="col">Planejado</th>
                  <th scope="col">Registrado</th>
                  <th scope="col">Excedente</th>
                </tr>
              </thead>
              <tbody>
                {excedentes.map(({ item, valor }) => (
                  <tr key={item.id}>
                    <th scope="row">
                      {item.nome}
                      <br />
                      {dataOrcamento(item.dataInicio)} a{" "}
                      {dataOrcamento(item.dataFim)}
                    </th>
                    <td>{moedaOrcamento(item.valorPlanejado)}</td>
                    <td>{moedaOrcamento(item.valorUtilizado)}</td>
                    <td>{moedaOrcamento(valor / 100)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
export default Budgets;
