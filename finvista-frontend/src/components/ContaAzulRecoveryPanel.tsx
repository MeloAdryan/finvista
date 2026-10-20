import { useEffect, useMemo, useRef, useState } from "react";
import {
  previewRecovery,
  applyRecovery,
  SyncHttpError,
  type RecoveryPlan,
  type RecoveryResult,
} from "../services/contaAzulSyncService";
import "../styles/conta-azul-recovery.css";
const rotulos: Record<string, string> = {
  valorOriginal: "Original",
  valorRealizado: "Principal realizado",
  valorAberto: "Principal em aberto",
  valorTotalRealizado: "Total realizado",
  valorTotalAberto: "Total em aberto",
  jurosRealizado: "Juros realizados",
  multaRealizada: "Multa realizada",
  descontoRealizado: "Desconto realizado",
  jurosPrevisto: "Juros previstos",
  multaPrevista: "Multa prevista",
  descontoPrevisto: "Desconto previsto",
};
const moeda = (v: number | string | null) =>
  v === null
    ? "Não informado"
    : Number(v).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
export default function ContaAzulRecoveryPanel({
  file,
  cut,
  clienteId,
  clienteNome,
  disabled,
  aoOcupar,
  aoConcluir,
}: {
  file: File;
  cut: string;
  clienteId: number;
  clienteNome: string;
  disabled: boolean;
  aoOcupar: (busy: boolean) => void;
  aoConcluir: () => void;
}) {
  const [plan, setPlan] = useState<RecoveryPlan | null>(null);
  const [result, setResult] = useState<RecoveryResult | null>(null);
  const [selected, setSelected] = useState<number[]>([]);
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [filter, setFilter] = useState("TODOS");
  const [page, setPage] = useState(0);
  const abort = useRef<AbortController | null>(null);
  const running = useRef(false);
  useEffect(() => () => abort.current?.abort(), []);
  async function preview() {
    if (running.current || disabled) return;
    running.current = true;
    const controller = new AbortController();
    abort.current = controller;
    setBusy(true);
    aoOcupar(true);
    setError("");
    setResult(null);
    setPlan(null);
    setSelected([]);
    setConfirmed(false);
    setPage(0);
    try {
      const p = await previewRecovery(file, cut, controller.signal);
      if (p.clienteId !== clienteId)
        throw new Error("O cliente mudou. Gere outra prévia.");
      if (!controller.signal.aborted) setPlan(p);
    } catch (e) {
      if (!controller.signal.aborted)
        setError(e instanceof Error ? e.message : "Falha na prévia.");
    } finally {
      if (abort.current === controller) abort.current = null;
      running.current = false;
      setBusy(false);
      aoOcupar(false);
    }
  }
  async function apply() {
    if (!plan || !confirmed || !selected.length || running.current || disabled)
      return;
    running.current = true;
    setBusy(true);
    aoOcupar(true);
    setError("");
    try {
      const r = await applyRecovery(file, plan, selected);
      setResult(r);
      setPlan(null);
      setSelected([]);
      setConfirmed(false);
      aoConcluir();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha na recuperação.");
      if (e instanceof SyncHttpError && e.status === 409) {
        setPlan(null);
        setSelected([]);
        setConfirmed(false);
      }
    } finally {
      running.current = false;
      setBusy(false);
      aoOcupar(false);
    }
  }
  const selectedSet = useMemo(() => new Set(selected), [selected]);
  const filtered = useMemo(
    () => (plan?.itens ?? []).filter(
      (i) => filter === "TODOS" || (filter === "ELEGIVEIS" ? i.elegivel : !i.elegivel),
    ),
    [plan, filter],
  );
  const pages = Math.max(1, Math.ceil(filtered.length / 20));
  const shown = filtered.slice(page * 20, page * 20 + 20);
  const fields = useMemo(
    () => (plan?.itens ?? []).reduce(
      (total, item) => total + (selectedSet.has(item.linha) ? item.campos.length : 0),
      0,
    ),
    [plan, selectedSet],
  );
  return (
    <section
      className="ca-recovery"
      aria-label="Recuperação de campos financeiros"
    >
      <header>
        <h3>Recuperar campos financeiros ausentes</h3>
        <p>
          Preencha apenas valores não informados dos registros existentes.
          Categorias, centros, rateios, datas e valores já registrados são
          preservados.
        </p>
      </header>
      <button
        type="button"
        disabled={busy || disabled}
        onClick={() => void preview()}
      >
        {busy ? "Processando recuperação…" : "Preparar prévia de recuperação"}
      </button>
      {error && (
        <p role="alert" className="ca-error">
          {error}
        </p>
      )}
      {result && (
        <div role="status" className="ca-recovery-result">
          <strong>Recuperação registrada — lote {result.loteId}</strong>
          <p>
            {result.atualizados} registros atualizados ·{" "}
            {result.camposPreenchidos} campos preenchidos · {result.pendentes}{" "}
            linhas não processadas.
          </p>
          <p>
            Atualize a posição financeira no Painel Executivo. Gere outra prévia
            para conferir o restante.
          </p>
        </div>
      )}
      {plan && (
        <>
          <p>
            <strong>{plan.elegiveis} elegíveis</strong> · {plan.pendentes} sem
            preenchimento automático. O lote não cria lançamentos.
          </p>
          <div className="ca-recovery-tools">
            <button
              type="button"
              disabled={busy || disabled || !plan.elegiveis}
              onClick={() => {
                setSelected(
                  plan.itens.filter((i) => i.elegivel).map((i) => i.linha),
                );
                setConfirmed(false);
              }}
            >
              Selecionar elegíveis ({plan.elegiveis})
            </button>
            <button
              type="button"
              disabled={busy || disabled || !selected.length}
              onClick={() => {
                setSelected([]);
                setConfirmed(false);
              }}
            >
              Limpar seleção
            </button>
            <label>
              Mostrar
              <select
                value={filter}
                disabled={busy || disabled}
                onChange={(e) => {
                  setFilter(e.target.value);
                  setPage(0);
                }}
              >
                <option value="TODOS">Todas as linhas</option>
                <option value="ELEGIVEIS">Elegíveis</option>
                <option value="PENDENTES">
                  Para conferir / sem alterações
                </option>
              </select>
            </label>
          </div>
          <ul className="ca-recovery-list">
            {shown.map((i) => (
              <li key={i.linha}>
                <div>
                  <strong>
                    Linha {i.linha} · {i.descricao}
                  </strong>
                  {i.lancamentoId != null && <p>Registro #{i.lancamentoId}</p>}
                  <p>{i.motivo}</p>
                </div>
                {i.elegivel && (
                  <div>
                    <label className="ca-recovery-choice">
                      <input
                        type="checkbox"
                        checked={selectedSet.has(i.linha)}
                        disabled={busy || disabled}
                        onChange={(e) => {
                          setSelected((prev) =>
                            e.target.checked
                              ? [...prev, i.linha]
                              : prev.filter((n) => n !== i.linha),
                          );
                          setConfirmed(false);
                        }}
                      />
                      Recuperar linha {i.linha}
                    </label>
                    <details>
                      <summary>
                        Conferir {i.campos.length} campos ausentes
                      </summary>
                      <ul>
                        {i.campos.map((c) => (
                          <li key={c.campo}>
                            {rotulos[c.campo] ?? c.campo}: não informado →{" "}
                            {moeda(c.valor)}
                          </li>
                        ))}
                      </ul>
                    </details>
                  </div>
                )}
              </li>
            ))}
          </ul>
          <div className="ca-recovery-pages">
            <button
              type="button"
              disabled={busy || page === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Anterior
            </button>
            <span>
              Página {page + 1} de {pages}
            </span>
            <button
              type="button"
              disabled={busy || page + 1 >= pages}
              onClick={() => setPage((p) => p + 1)}
            >
              Próxima
            </button>
          </div>
          <p>
            {selected.length} registros selecionados · {fields} campos a
            preencher. A seleção inclui outras páginas.
          </p>
          <label className="ca-recovery-confirm">
            <input
              type="checkbox"
              checked={confirmed}
              disabled={busy || disabled || !selected.length}
              onChange={(e) => setConfirmed(e.target.checked)}
            />
            Revisei a prévia e autorizo preencher somente os campos financeiros
            ausentes dos registros selecionados de {clienteNome}.
          </label>
          <button
            type="button"
            className="ca-primary"
            disabled={busy || disabled || !confirmed || !selected.length}
            onClick={() => void apply()}
          >
            Confirmar recuperação de {selected.length} registros
          </button>
        </>
      )}
    </section>
  );
}
