import { useEffect, useRef, useState } from "react";
import {
  ApiSyncError,
  prepararSincronizacao,
  prepararVinculo,
  confirmarSincronizacao,
  type ApiSyncPreview,
  type ApiSyncResult,
  type SyncResumo,
} from "../services/contaAzulApiSyncService";
import "../styles/conta-azul-api-sync.css";

const dinheiro = (valor: number | null) =>
  valor == null
    ? "Não informado"
    : valor.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const data = (valor: string | null) =>
  valor ? valor.split("-").reverse().join("/") : "Não informada";

function Resumo({ titulo, resumo }: { titulo: string; resumo: SyncResumo }) {
  return (
    <section className="ca-sync-summary" aria-label={titulo}>
      <h5>{titulo}</h5>
      <strong>{resumo.descricao}</strong>
      <dl>
        <div>
          <dt>Tipo</dt>
          <dd>{resumo.tipo === "RECEITA" ? "Receita" : "Despesa"}</dd>
        </div>
        <div>
          <dt>Valor</dt>
          <dd>{dinheiro(resumo.valor)}</dd>
        </div>
        <div>
          <dt>Competência</dt>
          <dd>{data(resumo.competencia)}</dd>
        </div>
        <div>
          <dt>Vencimento</dt>
          <dd>{data(resumo.vencimento)}</dd>
        </div>
        <div>
          <dt>Pago / recebido</dt>
          <dd>{dinheiro(resumo.pago)}</dd>
        </div>
        <div>
          <dt>Pendente</dt>
          <dd>{dinheiro(resumo.pendente)}</dd>
        </div>
      </dl>
      <ul>
        {resumo.rateios.map((r, i) => (
          <li key={i}>
            {r.categoria} · {r.centro || "Sem centro de custo"}:{" "}
            {dinheiro(r.valor)}
          </li>
        ))}
      </ul>
    </section>
  );
}

export default function ContaAzulApiSyncPanel({
  clienteId,
  parcelaId,
  aoConcluir,
  vincularId,
}: {
  clienteId: number;
  parcelaId: string;
  aoConcluir: () => void;
  vincularId?: number;
}) {
  const [previa, setPrevia] = useState<ApiSyncPreview | null>(null);
  const [resultado, setResultado] = useState<ApiSyncResult | null>(null);
  const [confirmado, setConfirmado] = useState(false);
  const [erro, setErro] = useState("");
  const [busy, setBusy] = useState(false);
  const pending = useRef<AbortController | null>(null);
  const executando = useRef(false);
  useEffect(() => () => pending.current?.abort(), []);

  async function preparar() {
    if (executando.current) return;
    executando.current = true;
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true);
    setErro("");
    setPrevia(null);
    setConfirmado(false);
    setResultado(null);
    try {
      const value =
        vincularId == null
          ? await prepararSincronizacao(clienteId, parcelaId, controller.signal)
          : await prepararVinculo(
              clienteId,
              parcelaId,
              vincularId,
              controller.signal,
            );
      if (!controller.signal.aborted) setPrevia(value);
    } catch (e) {
      if (!controller.signal.aborted)
        setErro(
          e instanceof Error
            ? e.message
            : "Não foi possível preparar a sincronização.",
        );
    } finally {
      executando.current = false;
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  async function confirmar() {
    if (!previa || !confirmado || executando.current) return;
    if (
      !window.confirm(
        `${previa.acao === "VINCULAR_EXCEL" ? `Vincular o registro Excel #${previa.lancamentoId} a` : previa.acao === "INCLUIR" ? "Incluir" : "Atualizar"} esta ${previa.depois.tipo === "RECEITA" ? "receita" : "despesa"} de ${dinheiro(previa.depois.valor)} em ${previa.clienteNome}?\n\nA gravação será feita no FinVista. O Conta Azul não será alterado.`,
      )
    )
      return;
    executando.current = true;
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true);
    setErro("");
    try {
      const value = await confirmarSincronizacao(previa, controller.signal);
      if (!controller.signal.aborted) {
        setResultado(value);
        setPrevia(null);
        setConfirmado(false);
        aoConcluir();
      }
    } catch (e) {
      if (!controller.signal.aborted) {
        if (e instanceof ApiSyncError) {
          setErro(e.message);
          if ([400, 401, 403, 404, 409].includes(e.status)) {
            setPrevia(null);
            setConfirmado(false);
          }
        } else {
          setErro(
            "Não foi possível confirmar a resposta. Tente confirmar novamente esta mesma prévia: uma operação já concluída não será repetida.",
          );
        }
      }
    } finally {
      executando.current = false;
      if (!controller.signal.aborted) setBusy(false);
    }
  }

  return (
    <section
      className="ca-api-sync"
      aria-label="Sincronização controlada"
      aria-busy={busy}
    >
      <h4>
        {vincularId == null
          ? "Sincronizar esta parcela com o FinVista"
          : `Conferir vínculo com o registro Excel #${vincularId}`}
      </h4>
      <p>
        A prévia reconsulta a fonte. A gravação exige sua confirmação e não
        altera o Conta Azul.
      </p>
      <p>
        Esta entrega aceita somente uma parcela por evento, em aberto e sem
        ajustes. O Excel Conta Azul pode ser revisado pelo plano de importação;
        linhas com possível correspondência API ficam protegidas. A importação
        genérica permanece bloqueada nesta empresa após o primeiro vínculo API.
      </p>
      <button type="button" disabled={busy} onClick={() => void preparar()}>
        {busy
          ? "Processando…"
          : vincularId == null
            ? "Preparar prévia de sincronização"
            : "Preparar prévia do vínculo"}
      </button>
      {erro && (
        <p role="alert" className="ca-parcel-detail-error">
          {erro}
        </p>
      )}
      {previa && (
        <>
          <p>
            <strong>Empresa: {previa.clienteNome}</strong> ·{" "}
            {previa.acao === "VINCULAR_EXCEL"
              ? "Vínculo Excel/API, sem alterar valores ou rateios"
              : previa.acao === "INCLUIR"
                ? "Inclusão de lançamento"
                : "Atualização de lançamento identificado"}
          </p>
          <div className="ca-sync-comparison">
            {previa.antes && (
              <Resumo titulo="Como está no FinVista" resumo={previa.antes} />
            )}
            <Resumo titulo="Como ficará no FinVista" resumo={previa.depois} />
          </div>
          <ul>
            {previa.avisos.map((a, i) => (
              <li key={i}>{a}</li>
            ))}
          </ul>
          <p>
            Prévia válida até{" "}
            {new Date(previa.expiraEm).toLocaleString("pt-BR")}. Se a fonte ou
            os dados locais mudarem, será necessário gerar outra.
          </p>
          <label className="ca-sync-consent">
            <input
              type="checkbox"
              checked={confirmado}
              disabled={busy}
              onChange={(e) => setConfirmado(e.target.checked)}
            />
            <span>
              {previa.acao === "VINCULAR_EXCEL"
                ? "Revisei os dados e confirmo que o lançamento Excel escolhido e esta parcela da API representam a mesma operação."
                : previa.acao === "INCLUIR"
                  ? "Conferi os lançamentos desta empresa e confirmo que esta parcela ainda não está cadastrada no FinVista."
                  : "Revisei os valores e confirmo a atualização deste lançamento identificado pela API."}
            </span>
          </label>
          <button
            type="button"
            disabled={busy || !confirmado}
            onClick={() => void confirmar()}
          >
            {previa.acao === "VINCULAR_EXCEL"
              ? "Confirmar vínculo Excel/API"
              : previa.acao === "INCLUIR"
                ? "Confirmar inclusão"
                : "Confirmar atualização"}
          </button>
        </>
      )}
      {resultado && (
        <div role="status" className="ca-sync-result">
          <strong>
            {resultado.acao === "VINCULAR_EXCEL"
              ? "Vínculo Excel/API registrado. O lançamento manteve seu ID, valores e rateios."
              : resultado.acao === "SEM_ALTERACAO"
                ? "O lançamento já estava atualizado."
                : resultado.acao === "INCLUIR"
                  ? "Lançamento incluído no FinVista."
                  : "Lançamento atualizado no FinVista."}
          </strong>
          <p>
            Competência e vencimento permanecem separados. Atualize as análises
            para consultar o resultado.
          </p>
          <details>
            <summary>Registro da operação</summary>
            <p>Operação: {resultado.operacaoId}</p>
            <p>
              Lançamento #{resultado.lancamentoId} ·{" "}
              {new Date(resultado.concluidoEm).toLocaleString("pt-BR")}
            </p>
          </details>
        </div>
      )}
    </section>
  );
}
