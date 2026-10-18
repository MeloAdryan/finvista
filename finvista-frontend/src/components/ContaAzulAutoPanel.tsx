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
        if (!inicializado.current) {
          setInicio(value.configuracao.inicio);
          setFim(value.configuracao.fim);
          setIntervalo(value.configuracao.intervaloMinutos);
          setNovas(value.configuracao.incluirNovas);
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
    if (
      acao === "salvar" &&
      !window.confirm(
        `Autorizar a sincronização periódica de ${cliente.nome}, de ${data(inicio)} a ${data(fim)} por vencimento?\n\n${novas ? "Novas parcelas simples poderão ser incluídas após as verificações de correspondência." : "Somente registros já identificados pela API serão atualizados; novas parcelas ficarão pendentes."}`,
      )
    )
      return;
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
              },
              controller.signal,
            )
          : await acaoAutomacao(cliente.id, acao, controller.signal);
      if (controller.signal.aborted) return;
      setStatus(value);
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
      <p>
        {cliente?.nome || "Selecione uma empresa"}. Consulta periódica de contas
        a pagar e receber, com renovação do acesso e registro das operações.
      </p>
      <p>
        O período é fixo e considera vencimentos. Parcelas simples em aberto
        podem ser processadas; baixas, ajustes, múltiplas parcelas e possíveis
        correspondências Excel ficam pendentes. A execução depende de o backend
        estar ativo.
      </p>
      {status && !status.processadorHabilitado && (
        <p role="status">
          O processador está desativado no backend. Configure
          CONTA_AZUL_AUTO_ENABLED antes de ativar esta empresa.
        </p>
      )}
      <div className="ca-auto-fields">
        <label>
          Vencimento inicial da automação
          <input
            type="date"
            value={inicio}
            disabled={busy}
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
            disabled={busy}
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
        Autorizo as execuções periódicas para esta empresa e este período,
        conforme as opções acima.
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
          Salvar e ativar automação
        </button>
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
          onClick={() => setRefresh((p) => p + 1)}
        >
          Atualizar status da automação
        </button>
      </div>
      {aviso && <p role="status">{aviso}</p>}
      {erro && <p role="alert">{erro}</p>}
      {status && (
        <p>
          Configuração salva: {data(status.configuracao.inicio)} a{" "}
          {data(status.configuracao.fim)} · próximas etapas a partir de{" "}
          {horario(status.configuracao.proximaEm)}. A execução completa pode
          exigir várias etapas.
        </p>
      )}
      {atual && (
        <section
          className="ca-auto-run"
          aria-label="Última execução automática"
        >
          <h3>{rotulos[atual.status] || atual.status}</h3>
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
          {atual.mensagem && <p>{atual.mensagem}</p>}
          {status!.pendencias.length > 0 && (
            <>
              <h4>Parcelas que exigem conferência</h4>
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
            <summary>Histórico das últimas execuções</summary>
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
