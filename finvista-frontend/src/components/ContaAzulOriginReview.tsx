import { useState } from "react";
import { API_URL } from "../config/api";
import { apiFetch } from "../config/http";

interface Plan {
  token: string;
  validade: string;
  clienteId: number;
  parcelaId: string;
  descricao: string;
  principal: number;
  aberto: number;
  situacao: string;
  motivo: string;
  substitutaId: string;
}
export default function ContaAzulOriginReview({
  clienteId,
  parcelaId,
}: {
  clienteId: number;
  parcelaId: string;
}) {
  const [reason, setReason] = useState("");
  const [replacement, setReplacement] = useState("");
  const [plan, setPlan] = useState<Plan | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const reset = () => {
    setPlan(null);
    setConfirmed(false);
    setNotice("");
  };
  async function submit(apply: boolean) {
    if (busy || (apply && (!plan || !confirmed))) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      const response = await apiFetch(
        `${API_URL}/api/integracoes/conta-azul/revisao-origem/${apply ? "confirmar" : "planejar"}`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(
            apply
              ? { token: plan?.token, clienteId, parcelaId, confirmado: true }
              : {
                  clienteId,
                  parcelaId,
                  motivo: reason,
                  substitutaId: replacement || null,
                },
          ),
        },
      );
      const data = await response.json().catch(() => null);
      if (!response.ok)
        throw new Error(
          data?.mensagem ||
            data?.detail ||
            data?.message ||
            "Não foi possível concluir a revisão. Prepare outra prévia.",
        );
      if (apply) {
        reset();
        setNotice(data.mensagem);
      } else {
        if (
          data.clienteId !== clienteId ||
          data.parcelaId !== parcelaId ||
          !data.token
        )
          throw new Error("A empresa ou parcela mudou. Consulte novamente.");
        setPlan(data);
        setConfirmed(false);
      }
    } catch (e) {
      reset();
      setError(e instanceof Error ? e.message : "Falha na revisão.");
    } finally {
      setBusy(false);
    }
  }
  const money = (value: number) =>
    value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
  return (
    <section
      className="ca-statement-import"
      aria-label="Revisar parcela indisponível"
    >
      <h4>Parcela indisponível: conferir na origem</h4>
      <p>
        A indisponibilidade não comprova exclusão. Confira no Conta Azul se
        houve remoção ou substituição antes de continuar. Esta ação só aceita
        parcelas sem pagamentos comprovados e preserva o histórico.
      </p>
      <label>
        O que foi conferido no Conta Azul?
        <textarea
          value={reason}
          minLength={15}
          maxLength={1000}
          disabled={busy}
          onChange={(e) => {
            setReason(e.target.value);
            reset();
          }}
        />
      </label>
      <label>
        Identificador da parcela substituta (opcional)
        <input
          value={replacement}
          disabled={busy}
          onChange={(e) => {
            setReplacement(e.target.value);
            reset();
          }}
        />
      </label>
      <p>
        Um valor ou descrição semelhante não comprova substituição. O vínculo
        informado será conferido nesta empresa, sem mesclar os registros.
      </p>
      <button
        type="button"
        disabled={busy || reason.trim().length < 15}
        onClick={() => void submit(false)}
      >
        Preparar revisão da origem
      </button>
      {plan && (
        <div>
          <p>
            <strong>{plan.descricao}</strong> · Principal{" "}
            {money(plan.principal)} · Em aberto {money(plan.aberto)} · Situação
            atual: {plan.situacao}.
          </p>
          <p>
            Ao confirmar, esta parcela ficará cancelada no FinVista, fora da
            posição em aberto e dos totais por competência e vencimento. Os
            valores originais e a justificativa serão preservados. O extrato e o
            Conta Azul não serão alterados.
          </p>
          <label>
            <input
              type="checkbox"
              checked={confirmed}
              disabled={busy}
              onChange={(e) => setConfirmed(e.target.checked)}
            />
            Conferi o documento original e confirmo que esta parcela foi
            removida ou substituída no Conta Azul.
          </label>
          <button
            type="button"
            disabled={busy || !confirmed}
            onClick={() => void submit(true)}
          >
            Confirmar revisão e retirar dos totais
          </button>
        </div>
      )}
      {error && <p role="alert">{error}</p>}
      {notice && <p role="status">{notice}</p>}
    </section>
  );
}
