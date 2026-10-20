import { useEffect, useRef, useState } from "react";
import type { Cliente } from "../services/clientService";
import {
  consultarAutomacao,
  alterarAutomacao,
  acaoAutomacao,
  type AutoStatus,
} from "../services/contaAzulAutoService";
import "../styles/conta-azul-auto.css";
import ContaAzulParcelDetailPanel from "./ContaAzulParcelDetailPanel";

const horario = (v: string | null) =>
  v ? new Date(v).toLocaleString("pt-BR") : "Não agendada";
const data = (v: string) => v.split("-").reverse().join("/");
function datasMoveis() {
  const hoje = new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Sao_Paulo" }).format(new Date());
  const deslocar = (dias: number) => {
    const d = new Date(`${hoje}T12:00:00Z`); d.setUTCDate(d.getUTCDate() + dias);
    return d.toISOString().slice(0, 10);
  };
  return { inicio: deslocar(-90), fim: deslocar(90) };
}
const rotulos: Record<string, string> = {
  EM_ANDAMENTO: "Em andamento",
  AGUARDANDO_REPETICAO: "Aguardando nova tentativa",
  CONCLUIDA: "Varredura concluída",
  CONCLUIDA_COM_PENDENCIAS: "Concluída com pendências",
  PARCIAL: "Cobertura incompleta",
  CANCELADA: "Cancelada",
};

export default function ContaAzulAutoPanel({
  cliente,
}: {
  cliente: Cliente | null;
}) {
  const [status, setStatus] = useState<AutoStatus | null>(null);
  const [inicio, setInicio] = useState("");
  const [fim, setFim] = useState("");
  const [intervalo, setIntervalo] = useState(30);
  const [movel, setMovel] = useState(false);
  const [novas, setNovas] = useState(false);
  const [confirmado, setConfirmado] = useState(false);
  const [historico, setHistorico] = useState(false);
  const [busy, setBusy] = useState(false);
  const [erro, setErro] = useState("");
  const [aviso, setAviso] = useState("");
  const [pagina, setPagina] = useState(1);
  const [parcelaSelecionada, setParcelaSelecionada] = useState<string | null>(
    null,
  );
  const [instanteConsulta, setInstanteConsulta] = useState(0);
  const [consultadoEm, setConsultadoEm] = useState<string | null>(null);
  const [refresh, setRefresh] = useState(0);
  const generation = useRef(0);
  const inicializado = useRef(false);
  const operacao = useRef(false);
  const pending = useRef<AbortController | null>(null);

  useEffect(() => {
    if (!cliente) return;
    const controller = new AbortController();
    let lendo = false;
    async function carregar() {
      if (operacao.current || lendo) return;
      lendo = true;
      const atual = generation.current;
      try {
        const value = await consultarAutomacao(
          cliente!.id,
          pagina,
          controller.signal,
        );
        if (controller.signal.aborted || atual !== generation.current) return;
        setStatus(value);
        setConsultadoEm(new Date().toLocaleTimeString("pt-BR"));
        setInstanteConsulta(Date.now());
        setAviso((v) => v === "Atualizando status…" ? "Status atualizado." : v);
        if (!inicializado.current) {
          setInicio(value.configuracao.inicio);
          setFim(value.configuracao.fim);
          setIntervalo(value.configuracao.intervaloMinutos);
          setNovas(value.configuracao.incluirNovas);
          setMovel(Boolean(value.configuracao.periodoMovel));
          inicializado.current = true;
        }
        setErro("");
      } catch (e) {
        if (!controller.signal.aborted && atual === generation.current)
          setErro(
            e instanceof Error ? e.message : "Falha ao consultar a automação.",
          );
      } finally {
        lendo = false;
      }
    }
    void carregar();
    const timer = window.setInterval(() => {
      if (!document.hidden) void carregar();
    }, 15000);
    return () => {
      controller.abort();
      window.clearInterval(timer);
    };
  }, [cliente, pagina, refresh]);
  useEffect(() => () => pending.current?.abort(), []);

  async function agir(acao: "salvar" | "pausar" | "executar") {
    if (!cliente || operacao.current) return;
    if (acao === "salvar" && (!confirmado || (novas && !historico))) return;
    generation.current++;
    const controller = new AbortController();
    pending.current = controller;
    operacao.current = true;
    setBusy(true);
    setErro("");
    setAviso("");
    try {
      const value =
        acao === "salvar"
          ? await alterarAutomacao(
              {
                clienteId: cliente.id,
                habilitada: true,
                inicio,
                fim,
                intervaloMinutos: intervalo,
                incluirNovas: novas,
                confirmado,
                confirmoHistorico: historico,
                periodoMovel: movel,
              },
              controller.signal,
            )
          : await acaoAutomacao(cliente.id, acao, controller.signal);
      if (controller.signal.aborted) return;
      setStatus(value);
      setConsultadoEm(new Date().toLocaleTimeString("pt-BR"));
        setInstanteConsulta(Date.now());
      setPagina(1);
      setConfirmado(false);
      setHistorico(false);
      setAviso(
        acao === "pausar"
          ? "Automação pausada. Etapas ainda não gravadas não serão aplicadas."
          : acao === "executar"
            ? "Execução solicitada. A fila será processada pelo backend."
            : "Autorização salva. O backend iniciará a varredura periódica.",
      );
    } catch (e) {
      if (!controller.signal.aborted)
        setErro(
          `${e instanceof Error ? e.message : "Não foi possível confirmar a resposta."} Atualize o status antes de repetir a alteração.`,
        );
    } finally {
      operacao.current = false;
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  const atual = status?.execucoes[0];
  return (
    <section
      className="ca-auto"
      aria-label="Sincronização automática Conta Azul"
      aria-busy={busy}
    >
      <header>
        <h2>Sincronização automática</h2>
        <strong>
          {!status
            ? "Consultando status…"
            : status.configuracao.habilitada
              ? status.processadorHabilitado
                ? "Ativa para esta empresa"
                : "Aguardando processador"
              : "Pausada"}
        </strong>
      </header>
      <p>Contas a pagar e receber sincronizadas automaticamente. Revise os casos que precisam de atenção.</p>
      {status && (
        <div className="ca-auto-overview">
          <div><span>{status.configuracao.periodoMovel ? "Período móvel · por vencimento" : "Período autorizado · por vencimento"}</span><strong>{data(status.configuracao.inicio)} a {data(status.configuracao.fim)}</strong></div>
          <div><span>Próxima verificação</span><strong>{status.configuracao.habilitada ? atual?.status === "EM_ANDAMENTO" ? "Execução em andamento" : status.configuracao.proximaEm && new Date(status.configuracao.proximaEm).getTime() <= instanteConsulta ? "Aguardando processamento" : horario(status.configuracao.proximaEm) : "Automação pausada"}</strong><small>Intervalo: {status.configuracao.intervaloMinutos === 1440 ? "diariamente" : `${status.configuracao.intervaloMinutos} min`} após concluir a varredura.</small></div>
          <div><span>Novas parcelas</span><strong>{status.configuracao.incluirNovas ? "Inclusão autorizada" : "Somente registros já vinculados"}</strong></div>
        </div>
      )}
      {status && !status.processadorHabilitado && (
        <p role="status">
          O processador está desativado no backend. Configure
          CONTA_AZUL_AUTO_ENABLED antes de ativar esta empresa.
        </p>
      )}
      <details className="ca-auto-settings" key={status?.configuracao.alteradoEm || "nova"} open={Boolean(status && !status.configuracao.alteradoEm)}>
        <summary>{status?.configuracao.alteradoEm ? "Configurar automação" : "Configurar e autorizar automação"}</summary>
        <p>Esta autorização fica salva. As confirmações abaixo são necessárias apenas para ativar ou alterar a configuração.</p>
        <label className="ca-auto-check">
          <input type="checkbox" checked={movel} disabled={busy} onChange={(e) => {
            setMovel(e.target.checked); setConfirmado(false); setHistorico(false);
            if (e.target.checked) { const datas = datasMoveis(); setInicio(datas.inicio); setFim(datas.fim); }
          }} />
          Acompanhar o calendário automaticamente: últimos 90 e próximos 90 dias.
        </label>
        <p>{movel ? "Cada nova varredura recalcula as datas pelo horário de Brasília. Uma varredura em andamento mantém o período com que começou." : "Período fixo de até 366 dias. Vencimentos fora dele não serão consultados."}</p>
      <div className="ca-auto-fields">
        <label>
          Vencimento inicial da automação
          <input
            type="date"
            value={inicio}
            disabled={busy || movel}
            onChange={(e) => {
              setInicio(e.target.value);
              setConfirmado(false);
              setHistorico(false);
            }}
          />
        </label>
        <label>
          Vencimento final da automação
          <input
            type="date"
            value={fim}
            min={inicio}
            disabled={busy || movel}
            onChange={(e) => {
              setFim(e.target.value);
              setConfirmado(false);
              setHistorico(false);
            }}
          />
        </label>
        <label>
          Intervalo entre varreduras
          <select
            value={intervalo}
            disabled={busy}
            onChange={(e) => {
              setIntervalo(Number(e.target.value));
              setConfirmado(false);
            }}
          >
            <option value={30}>30 minutos</option>
            <option value={60}>1 hora</option>
            <option value={360}>6 horas</option>
            <option value={1440}>Diariamente</option>
          </select>
        </label>
      </div>
      <label className="ca-auto-check">
        <input
          type="checkbox"
          checked={novas}
          disabled={busy}
          onChange={(e) => {
            setNovas(e.target.checked);
            setConfirmado(false);
            setHistorico(false);
          }}
        />
        Incluir novas parcelas automaticamente quando não houver impedimentos ou
        possíveis correspondências.
      </label>
      {novas && (
        <label className="ca-auto-check">
          <input
            type="checkbox"
            checked={historico}
            disabled={busy}
            onChange={(e) => setHistorico(e.target.checked)}
          />
          Revisei o histórico desta empresa no período escolhido e autorizo
          novas inclusões. A ausência de correspondência não comprova que uma
          parcela seja nova.
        </label>
      )}
      <label className="ca-auto-check">
        <input
          type="checkbox"
          checked={confirmado}
          disabled={busy}
          onChange={(e) => setConfirmado(e.target.checked)}
        />
        {movel ? "Autorizo as execuções periódicas para esta empresa no período móvel, acompanhando o calendário, conforme as opções acima." : "Autorizo as execuções periódicas para esta empresa e este período, conforme as opções acima."}
      </label>
      <div className="ca-auto-actions">
        <button
          type="button"
          disabled={
            busy ||
            !cliente ||
            !status?.processadorHabilitado ||
            !inicio ||
            !fim ||
            !confirmado ||
            (novas && !historico)
          }
          onClick={() => void agir("salvar")}
        >
          {status?.configuracao.habilitada ? "Salvar configuração autorizada" : "Salvar e ativar automação"}
        </button>
      </div>
      </details>
      <div className="ca-auto-actions ca-auto-daily-actions">
        <button
          type="button"
          disabled={busy || !status?.configuracao.habilitada}
          onClick={() => void agir("pausar")}
        >
          Pausar automação
        </button>
        <button
          type="button"
          disabled={
            busy ||
            !status?.configuracao.habilitada ||
            !status.processadorHabilitado
          }
          onClick={() => void agir("executar")}
        >
          Solicitar execução agora
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={() => { setAviso("Atualizando status…"); setRefresh((p) => p + 1); }}
        >
          Atualizar status da automação
        </button>
      </div>
      {aviso && <p role="status">{aviso}</p>}
      {erro && <p role="alert">{erro}</p>}
      {consultadoEm && <p className="ca-auto-updated">Status consultado às {consultadoEm}. A execução continua no backend com esta página fechada, enquanto ele estiver ativo.</p>}
      {!atual && status && <div className="ca-auto-empty"><h3>{status.configuracao.habilitada ? "Aguardando a primeira execução" : "Comece pela configuração"}</h3><p>{status.configuracao.habilitada ? "O resultado aparecerá aqui quando a fila começar a ser processada." : "Escolha o período e autorize a sincronização uma vez. Depois acompanhe os resultados por aqui."}</p></div>}
      {atual && (
        <section
          className="ca-auto-run"
          aria-label="Última execução automática"
        >
          <div className={`ca-auto-result-heading ${atual.status === "CONCLUIDA" ? "is-complete" : "needs-attention"}`}>
            <span>Última execução</span>
            <h3>{atual.status === "CONCLUIDA" ? "Tudo atualizado neste período" : atual.status === "CONCLUIDA_COM_PENDENCIAS" ? `${atual.pendentes} pendência(s) para revisar` : rotulos[atual.status] || atual.status}</h3>
          </div>
          <p>
            Período consultado: {data(atual.inicio)} a {data(atual.fim)} ·
            início {horario(atual.criadoEm)}
            {atual.concluidoEm
              ? ` · término ${horario(atual.concluidoEm)}`
              : ""}
          </p>
          <dl>
            {(
              [
                ["Incluídas", atual.incluidas],
                ["Atualizadas", atual.atualizadas],
                ["Sem alteração", atual.mantidas],
                ["Para conferir", atual.pendentes],
                ["Na fila", atual.emFila],
              ] as const
            ).map(([nome, valor]) => (
              <div key={nome}>
                <dt>{nome}</dt>
                <dd>{valor}</dd>
              </div>
            ))}
          </dl>
          {atual.mensagem && !["CONCLUIDA", "CONCLUIDA_COM_PENDENCIAS"].includes(atual.status) && <p role="status">{atual.mensagem}</p>}
          {status!.pendencias.length > 0 && (
            <>
              <h4>O que precisa da sua atenção</h4><p>Revise cada caso abaixo. A consulta de uma pendência não grava lançamentos.</p>
              <ul>
                {status!.pendencias.map((p) => (
                  <li key={p.parcelaId}>
                    <strong>{p.descricao || "Parcela sem descrição"}</strong> ·{" "}
                    {p.tipo === "RECEITA" ? "Receita" : "Despesa"}
                    <p>{p.mensagem}</p>
                    <button
                      type="button"
                      disabled={busy}
                      aria-label={`Conferir parcela: ${p.descricao || p.parcelaId}`}
                      onClick={() => setParcelaSelecionada(p.parcelaId)}
                    >
                      Conferir parcela
                    </button>
                    <details>
                      <summary>Identificação</summary>
                      {p.parcelaId}
                      {p.lancamentoId ? ` · lançamento #${p.lancamentoId}` : ""}
                    </details>
                  </li>
                ))}
              </ul>
              <p>
                Exibindo {status!.pendencias.length} de{" "}
                {status!.totalPendencias} pendências da última execução.
              </p>
              <div className="ca-auto-actions">
                <button
                  disabled={busy || pagina <= 1}
                  onClick={() => setPagina((p) => p - 1)}
                >
                  Pendências anteriores
                </button>
                <button
                  disabled={busy || pagina * 20 >= status!.totalPendencias}
                  onClick={() => setPagina((p) => p + 1)}
                >
                  Próximas pendências
                </button>
              </div>
            </>
          )}
          <details>
            <summary>Ver histórico das execuções</summary>
            <ul>
              {status!.execucoes.map((r) => (
                <li key={r.id}>
                  {horario(r.criadoEm)} · {rotulos[r.status] || r.status} ·{" "}
                  {r.incluidas} incluídas, {r.atualizadas} atualizadas,{" "}
                  {r.pendentes} pendentes
                </li>
              ))}
            </ul>
          </details>
        </section>
      )}
      {parcelaSelecionada && cliente && (
        <ContaAzulParcelDetailPanel
          key={`${cliente.id}:${parcelaSelecionada}`}
          clienteId={cliente.id}
          parcelaId={parcelaSelecionada}
          panelId={`automatica-parcela-${parcelaSelecionada}`}
          fechar={() => setParcelaSelecionada(null)}
        />
      )}
    </section>
  );
}
