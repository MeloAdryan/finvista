import { useEffect, useMemo, useState } from "react";

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
  type FiltroOrcamento,
} from "../services/budgetAnalysis";

function Budgets() {
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
    async function carregarPagina() {
      try {
        setCarregando(true);
        setErro(null);

        const [dadosOrcamentos, usuarioAtual] = await Promise.all([
          getBudgetPipeline(),
          obterUsuarioAtual(),
        ]);

        setOrcamentos(dadosOrcamentos);
        const recente = [...dadosOrcamentos].sort(
          (a, b) =>
            b.dataInicio.localeCompare(a.dataInicio) ||
            b.dataFim.localeCompare(a.dataFim),
        )[0];
        if (recente) {
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
  const estourados = visiveis.filter(
    (item) => Number(item.valorUtilizado) > Number(item.valorPlanejado),
  ).length;
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
          <h1>Orçamentos empresariais</h1>
          <p>Acompanhe cada limite no seu período e identifique os excedentes.</p>
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
        aria-label="Selecionar período dos orçamentos"
      >
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
          O filtro seleciona orçamentos. Valores planejados e utilizados
          permanecem calculados no período completo de cada um, sem ratear o
          limite por dias.
        </p>
        {erroFiltro && (
          <p role="alert" className="ba-error">
            {erroFiltro}
          </p>
        )}
      </section>
      <section
        className="ba-summary"
        aria-label="Resumo dos orçamentos exibidos"
      >
        <div>
          <span>Planejado somado</span>
          <strong>{moedaOrcamento(totalPlanejado)}</strong>
        </div>
        <div>
          <span>Consumo somado dos orçamentos</span>
          <strong>{moedaOrcamento(totalUtilizado)}</strong>
        </div>
        <div>
          <span>Disponível somado</span>
          <strong>{moedaOrcamento(totalDisponivel)}</strong>
        </div>
        <div>
          <span>Orçamentos estourados</span>
          <strong>
            {estourados} de {visiveis.length}
          </strong>
        </div>
      </section>
      {sobreposicoes.length > 0 && (
        <aside className="ba-overlap">
          <strong>Os critérios de alguns orçamentos se sobrepõem.</strong>
          <p>
            Uma mesma despesa pode entrar em mais de um orçamento. A soma acima
            não representa despesas únicas da empresa.
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
      {filtro.modo === "TODOS" && periodos.length > 1 && (
        <p className="ba-note">
          Você está somando limites de períodos diferentes. Confira a utilização
          individual nas barras.
        </p>
      )}
      <BudgetPipelineChart dados={visiveis} />
    </div>
  );
}
export default Budgets;
