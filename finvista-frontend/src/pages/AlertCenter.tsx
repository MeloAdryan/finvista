import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { useAnalysisPeriod } from "../contexts/analysisPeriodContext";
import { brl, dataBR } from "../services/dashboardAnalysisService";
import {
  listarAlertas,
  reavaliarAlertas,
  marcarAlertaLido,
  type AlertCenterData,
  type AlertCenterFilters,
  type AlertCenterUpdate,
} from "../services/alertCenterService";
import "../styles/alert-center.css";

const gravidades = {
  INFORMATIVO: "Informativo",
  ATENCAO: "Atenção",
  CRITICO: "Crítico",
};
const tipos = {
  META_LIMITE: "Metas",
  ORCAMENTO_EXCEDENTE: "Orçamentos",
  AUMENTO_DESPESAS: "Aumento de despesas",
};
const instante = (data: string) =>
  new Date(data).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" });
const comparacoes = {
  DISPONIVEL: "Aumento de despesas avaliado sobre os registros disponíveis.",
  SEM_REGISTROS:
    "Aumento de despesas não reavaliado: faltam registros em um dos períodos.",
  BASE_NAO_POSITIVA:
    "Aumento de despesas não reavaliado: o total anterior não é positivo.",
  PERIODO_EM_ANDAMENTO:
    "Aumento de despesas não reavaliado: o período ainda não terminou.",
};

export default function AlertCenter() {
  const { periodo, atualizarPeriodo } = useAnalysisPeriod();
  const [consulta, setConsulta] = useState(() => ({
    filtros: {
      inicio: periodo.inicio,
      fim: periodo.fim,
      estado: "ATIVO",
      pagina: 0,
    } as AlertCenterFilters,
    reavaliar: true,
    ciclo: 0,
  }));
  const [draft, setDraft] = useState({
    inicio: periodo.inicio ?? "",
    fim: periodo.fim ?? "",
  });
  const [dados, setDados] = useState<AlertCenterData | null>(null);
  const [atualizacao, setAtualizacao] = useState<AlertCenterUpdate | null>(
    null,
  );
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");
  const [erroAcao, setErroAcao] = useState("");
  const [lendo, setLendo] = useState<number | null>(null);
  const acao = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    async function carregar() {
      setCarregando(true);
      setErro("");
      try {
        let filtros = consulta.filtros;
        let resumo: AlertCenterUpdate | null = null;
        if (consulta.reavaliar) {
          resumo = await reavaliarAlertas(
            filtros.inicio,
            filtros.fim,
            controller.signal,
          );
          filtros = { ...filtros, inicio: resumo.inicio, fim: resumo.fim };
        }
        const resultado = await listarAlertas(filtros, controller.signal);
        if (controller.signal.aborted) return;
        if (resumo && resultado.clienteId !== resumo.clienteId)
          throw new Error("A empresa selecionada mudou. Atualize a central.");
        if (
          resultado.totalPaginas > 0 &&
          resultado.pagina >= resultado.totalPaginas
        ) {
          setConsulta((atual) => ({
            filtros: { ...filtros, pagina: resultado.totalPaginas - 1 },
            reavaliar: false,
            ciclo: atual.ciclo + 1,
          }));
          return;
        }
        setDados(resultado);
        setDraft({ inicio: resultado.inicio, fim: resultado.fim });
        if (resumo) setAtualizacao(resumo);
      } catch (error) {
        if (!controller.signal.aborted)
          setErro(
            error instanceof Error
              ? error.message
              : "Falha ao consultar alertas.",
          );
      } finally {
        if (!controller.signal.aborted) setCarregando(false);
      }
    }
    void carregar();
    return () => controller.abort();
  }, [consulta]);
  useEffect(() => () => acao.current?.abort(), []);

  function trocarFiltro<K extends keyof AlertCenterFilters>(
    nome: K,
    valor: AlertCenterFilters[K],
  ) {
    setConsulta((atual) => ({
      filtros: {
        ...atual.filtros,
        inicio: dados?.inicio,
        fim: dados?.fim,
        [nome]: valor,
        pagina: 0,
      },
      reavaliar: false,
      ciclo: atual.ciclo + 1,
    }));
  }
  function atualizar(event: FormEvent) {
    event.preventDefault();
    if (!draft.inicio || !draft.fim || draft.inicio > draft.fim) {
      setErro("Informe as duas datas em ordem válida.");
      return;
    }
    setConsulta((atual) => ({
      filtros: {
        ...atual.filtros,
        inicio: draft.inicio,
        fim: draft.fim,
        pagina: 0,
      },
      reavaliar: true,
      ciclo: atual.ciclo + 1,
    }));
  }
  async function ler(id: number) {
    acao.current?.abort();
    const controller = new AbortController();
    acao.current = controller;
    setLendo(id);
    setErroAcao("");
    try {
      await marcarAlertaLido(id, controller.signal);
      if (!controller.signal.aborted)
        setConsulta((atual) => ({
          ...atual,
          reavaliar: false,
          ciclo: atual.ciclo + 1,
        }));
    } catch (error) {
      if (!controller.signal.aborted)
        setErroAcao(
          error instanceof Error
            ? error.message
            : "Não foi possível marcar a leitura.",
        );
    } finally {
      if (!controller.signal.aborted) setLendo(null);
    }
  }
  const ocupado = carregando || lendo !== null;
  return (
    <div className="ac-page">
      <header className="ac-heading">
        <span>Acompanhamento financeiro</span>
        <h1>Central de Alertas</h1>
        <p>Consulte avisos, evidências e o histórico da empresa selecionada.</p>
      </header>
      <section className="ac-panel" aria-label="Filtros dos alertas">
        <form className="ac-period" onSubmit={atualizar}>
          <label>
            Data inicial
            <input
              aria-label="Data inicial"
              type="date"
              value={draft.inicio}
              disabled={ocupado}
              onChange={(event) =>
                setDraft({ ...draft, inicio: event.target.value })
              }
            />
          </label>
          <label>
            Data final
            <input
              aria-label="Data final"
              type="date"
              value={draft.fim}
              disabled={ocupado}
              onChange={(event) =>
                setDraft({ ...draft, fim: event.target.value })
              }
            />
          </label>
          <button type="submit" disabled={ocupado}>
            {carregando && consulta.reavaliar
              ? "Reavaliando..."
              : "Atualizar alertas"}
          </button>
        </form>
        <div className="ac-filters">
          <label>
            Gravidade
            <select
              aria-label="Gravidade"
              value={consulta.filtros.gravidade ?? ""}
              disabled={ocupado}
              onChange={(event) =>
                trocarFiltro(
                  "gravidade",
                  (event.target.value ||
                    undefined) as AlertCenterFilters["gravidade"],
                )
              }
            >
              <option value="">Todas</option>
              {Object.entries(gravidades).map(([valor, nome]) => (
                <option key={valor} value={valor}>
                  {nome}
                </option>
              ))}
            </select>
          </label>
          <label>
            Tipo
            <select
              aria-label="Tipo"
              value={consulta.filtros.tipo ?? ""}
              disabled={ocupado}
              onChange={(event) =>
                trocarFiltro(
                  "tipo",
                  (event.target.value ||
                    undefined) as AlertCenterFilters["tipo"],
                )
              }
            >
              <option value="">Todos</option>
              {Object.entries(tipos).map(([valor, nome]) => (
                <option key={valor} value={valor}>
                  {nome}
                </option>
              ))}
            </select>
          </label>
          <label>
            Estado
            <select
              aria-label="Estado"
              value={consulta.filtros.estado ?? ""}
              disabled={ocupado}
              onChange={(event) =>
                trocarFiltro(
                  "estado",
                  (event.target.value ||
                    undefined) as AlertCenterFilters["estado"],
                )
              }
            >
              <option value="">Todos</option>
              <option value="ATIVO">Ativos</option>
              <option value="RESOLVIDO">Resolvidos</option>
            </select>
          </label>
          <label>
            Leitura
            <select
              aria-label="Leitura"
              value={
                consulta.filtros.lido === undefined
                  ? ""
                  : String(consulta.filtros.lido)
              }
              disabled={ocupado}
              onChange={(event) =>
                trocarFiltro(
                  "lido",
                  event.target.value === ""
                    ? undefined
                    : event.target.value === "true",
                )
              }
            >
              <option value="">Todos</option>
              <option value="false">Não lidos</option>
              <option value="true">Lidos</option>
            </select>
          </label>
        </div>
        <p className="ac-note">
          A reavaliação usa toda a empresa; a categoria selecionada no painel
          não restringe esta central. Metas e orçamentos mantêm seus períodos
          próprios. A presença de registros não confirma importação completa.
        </p>
        {atualizacao && (
          <p className="ac-note">
            Reavaliado em {instante(atualizacao.atualizadoEm)}.{" "}
            {comparacoes[atualizacao.comparacao]}
          </p>
        )}
      </section>
      {erro && (
        <div className="ac-message" role="alert">
          <p>{erro}</p>
          <button
            type="button"
            disabled={ocupado}
            onClick={() =>
              setConsulta((atual) => ({ ...atual, ciclo: atual.ciclo + 1 }))
            }
          >
            Tentar novamente
          </button>
        </div>
      )}
      {erroAcao && (
        <p className="ac-message" role="alert">
          {erroAcao}
        </p>
      )}
      {carregando && <p role="status">Consultando a Central de Alertas...</p>}
      {!carregando && !erro && dados && (
        <>
          <p className="ac-results" role="status">
            {dados.total} avisos nos filtros · {dataBR(dados.inicio)} —{" "}
            {dataBR(dados.fim)}
          </p>
          {dados.registros.length === 0 ? (
            <p className="ac-panel">
              Nenhum aviso registrado para estes filtros.
            </p>
          ) : (
            <div className="ac-list">
              {dados.registros.map((registro) => {
                const alerta = registro.alerta;
                const aumento = alerta.tipo === "AUMENTO_DESPESAS";
                return (
                  <article
                    key={registro.id}
                    className={`ac-card ac-${alerta.gravidade.toLowerCase()}`}
                  >
                    <div className="ac-badges">
                      <span>{gravidades[alerta.gravidade]}</span>
                      <span>
                        {registro.estado === "ATIVO" ? "Ativo" : "Resolvido"}
                      </span>
                      <span>{registro.lido ? "Lido" : "Não lido"}</span>
                      {alerta.encerrado && <span>Período encerrado</span>}
                    </div>
                    <h2>{alerta.titulo}</h2>
                    <p>{alerta.motivo}</p>
                    <p>
                      {registro.estado === "RESOLVIDO"
                        ? "Última ocorrência: "
                        : ""}
                      {dataBR(alerta.inicio)} — {dataBR(alerta.fim)} ·{" "}
                      {alerta.percentual.toLocaleString("pt-BR")}%{" "}
                      {aumento ? "de aumento" : "utilizado"}
                    </p>
                    {registro.estado === "RESOLVIDO" && (
                      <p className="ac-note">
                        A regra deixou de gerar este aviso na reavaliação. Isso
                        não comprova quitação de despesas.
                      </p>
                    )}
                    <details>
                      <summary>Ver evidências e histórico</summary>
                      {registro.estado === "RESOLVIDO" && (
                        <p>
                          Valores preservados da última ocorrência que gerou o
                          aviso.
                        </p>
                      )}
                      <dl>
                        <div>
                          <dt>Registrado</dt>
                          <dd>{brl(alerta.valorAtual)}</dd>
                        </div>
                        <div>
                          <dt>{aumento ? "Base anterior" : "Limite"}</dt>
                          <dd>{brl(alerta.valorBase)}</dd>
                        </div>
                        <div>
                          <dt>
                            {aumento
                              ? "Aumento"
                              : alerta.diferenca > 0
                                ? "Excedente"
                                : "Saldo disponível"}
                          </dt>
                          <dd>{brl(Math.abs(alerta.diferenca))}</dd>
                        </div>
                      </dl>
                      <p>
                        Categoria: {alerta.categoria ?? "Todas"}. Centro:{" "}
                        {alerta.centroCusto ?? "Todos"}.
                      </p>
                      {alerta.inicioAnterior && alerta.fimAnterior && (
                        <p>
                          Base: {dataBR(alerta.inicioAnterior)} —{" "}
                          {dataBR(alerta.fimAnterior)}.
                        </p>
                      )}
                      <p>Primeiro registro: {instante(registro.criadoEm)}.</p>
                      <p>
                        Última verificação: {instante(registro.verificadoEm)}.
                      </p>
                      <p>Ocorrências: {registro.ocorrencias}.</p>
                      {registro.resolvidoEm && (
                        <p>Resolvido em: {instante(registro.resolvidoEm)}.</p>
                      )}
                    </details>
                    <div className="ac-actions">
                      <Link
                        to={
                          aumento
                            ? "/despesas"
                            : alerta.tipo === "META_LIMITE"
                              ? `/metas#meta-${alerta.entidadeId}`
                              : `/orcamentos?orcamento=${alerta.entidadeId}`
                        }
                        onClick={() => {
                          if (aumento)
                            atualizarPeriodo({
                              inicio: alerta.inicio,
                              fim: alerta.fim,
                              categoria: alerta.categoria ?? undefined,
                            });
                        }}
                      >
                        {aumento
                          ? "Ver despesas"
                          : alerta.tipo === "META_LIMITE"
                            ? "Ver meta"
                            : "Ver orçamento"}
                      </Link>
                      {!registro.lido && (
                        <button
                          type="button"
                          disabled={ocupado}
                          onClick={() => void ler(registro.id)}
                        >
                          {lendo === registro.id
                            ? "Registrando leitura..."
                            : "Marcar como lido"}
                        </button>
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          )}
          {dados.totalPaginas > 1 && (
            <nav className="ac-pagination" aria-label="Páginas dos alertas">
              <button
                type="button"
                disabled={ocupado || dados.pagina === 0}
                onClick={() =>
                  setConsulta((atual) => ({
                    ...atual,
                    filtros: {
                      ...atual.filtros,
                      inicio: dados.inicio,
                      fim: dados.fim,
                      pagina: dados.pagina - 1,
                    },
                    reavaliar: false,
                  }))
                }
              >
                Anterior
              </button>
              <span>
                Página {dados.pagina + 1} de {dados.totalPaginas}
              </span>
              <button
                type="button"
                disabled={ocupado || dados.pagina + 1 >= dados.totalPaginas}
                onClick={() =>
                  setConsulta((atual) => ({
                    ...atual,
                    filtros: {
                      ...atual.filtros,
                      inicio: dados.inicio,
                      fim: dados.fim,
                      pagina: dados.pagina + 1,
                    },
                    reavaliar: false,
                  }))
                }
              >
                Próxima
              </button>
            </nav>
          )}
        </>
      )}
    </div>
  );
}
