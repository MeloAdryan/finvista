import { useState } from "react";
import { Link } from "react-router-dom";
import type { SpendingGoal } from "../services/spendingGoalService";

import GoalProgress from "./GoalProgress";

import { periodoMeta } from "../services/goalPresentation";

interface SpendingGoalGaugeProps {
  compacto?: boolean;
  meta: SpendingGoal | null;
  metas: SpendingGoal[];
  metaSelecionadaId: number | null;
  onSelecionarMeta: (id: number) => void;
}

function SpendingGoalGauge({
  meta,
  compacto = false,
  metas,
  metaSelecionadaId,
  onSelecionarMeta,
}: SpendingGoalGaugeProps) {
  const [detalhes, setDetalhes] = useState(false);
  const selecionarMeta = (valor: string) => {
    if (!valor) {
      return;
    }

    const id = Number(valor);

    if (Number.isFinite(id)) {
      onSelecionarMeta(id);
    }
  };

  if (compacto) {
    const uso = meta?.percentualUtilizado ?? 0,
      arco = Math.PI * 82;
    const moeda = (v: number) =>
      v.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
    const cor =
      meta?.status === "EXCEDIDA"
        ? "var(--fv-chart-danger)"
        : "var(--fv-chart-orange)";
    return (
      <article className="executive-panel ev-goal-compact">
        <header>
          <h2>Limite de gastos</h2>
          <Link to="/metas">Ver metas ↗</Link>
        </header>
        {metas.length > 0 && (
          <label className="ev-goal-select">
            <span>Meta</span>
            <select
              aria-label="Meta exibida no painel"
              value={metaSelecionadaId ?? ""}
              onChange={(e) => selecionarMeta(e.target.value)}
            >
              {metas.map((m) => (
                <option key={m.id} value={m.id}>
                  #{m.id} · {m.dataInicio.split("-").reverse().join("/")} a{" "}
                  {m.dataFim.split("-").reverse().join("/")} · {periodoMeta(m)}
                </option>
              ))}
            </select>
          </label>
        )}
        {meta ? (
          <>
            <div className="ev-goal-summary">
              <svg
                viewBox="0 0 200 128"
                role="img"
                aria-label={`Consumo da meta: ${uso.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}%`}
              >
                <path
                  d="M 18 106 A 82 82 0 0 1 182 106"
                  fill="none"
                  stroke="var(--fv-border)"
                  strokeWidth="16"
                />
                <path
                  d="M 18 106 A 82 82 0 0 1 182 106"
                  fill="none"
                  stroke={cor}
                  strokeWidth="16"
                  strokeDasharray={arco}
                  strokeDashoffset={
                    arco * (1 - Math.max(0, Math.min(100, uso)) / 100)
                  }
                />
                <text
                  x="100"
                  y="88"
                  textAnchor="middle"
                  fill="var(--fv-text)"
                  fontSize="25"
                  fontWeight="700"
                >
                  {uso.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}%
                </text>
                <text
                  x="100"
                  y="110"
                  textAnchor="middle"
                  fill="var(--fv-muted)"
                  fontSize="11"
                >
                  utilizado
                </text>
              </svg>
              <div>
                <strong>{moeda(meta.gastoAtual)}</strong>
                <span>de {moeda(meta.valorLimite)}</span>
                <small
                  style={{
                    color: meta.status === "EXCEDIDA" ? cor : undefined,
                  }}
                >
                  {meta.status === "EXCEDIDA"
                    ? `Excedente: ${moeda(Math.max(0, meta.gastoAtual - meta.valorLimite))}`
                    : periodoMeta(meta)}
                </small>
              </div>
            </div>
            <p className="ev-caption">
              Período próprio: {meta.dataInicio.split("-").reverse().join("/")}{" "}
              a {meta.dataFim.split("-").reverse().join("/")} ·{" "}
              {periodoMeta(meta)}.
            </p>
            <details
              onToggle={(e) => setDetalhes(e.currentTarget.open)}
              className="ev-all"
            >
              <summary>Ver acompanhamento completo</summary>
              {detalhes && (
                <GoalProgress
                  key={`${meta.id}-${meta.gastoAtual}-${meta.dataReferencia}`}
                  meta={meta}
                  automatico
                  resumo
                  evolucaoEmDialogo
                />
              )}
            </details>
          </>
        ) : (
          <div className="ev-empty">
            <strong>Nenhuma meta neste período</strong>
            <p>
              Escolha um período que inclua uma meta ou consulte as metas
              cadastradas.
            </p>
          </div>
        )}
      </article>
    );
  }
  return (
    <article className="executive-panel goal-panel">
      <div className="executive-panel-header goal-panel-header">
        <div>
          <span className="executive-eyebrow">Controle de gastos</span>

          <h2>Limite da meta</h2>

          <p>Acompanhamento do período próprio da meta selecionada</p>
        </div>

        {metas.length > 0 && (
          <label className="goal-selector">
            <span>Meta</span>

            <select
              value={metaSelecionadaId ?? ""}
              onChange={(event) => selecionarMeta(event.target.value)}
            >
              <option value="" disabled>
                Selecione uma meta
              </option>

              {metas.map((item) => (
                <option key={item.id} value={item.id}>
                  #{item.id} · {item.dataInicio} — {item.dataFim}
                  {" · "}
                  {periodoMeta(item)}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      {meta ? (
        <GoalProgress
          key={`${meta.id}-${meta.gastoAtual}-${meta.dataReferencia}`}
          meta={meta}
          automatico
          resumo
          evolucaoEmDialogo
        />
      ) : (
        <div className="goal-empty">
          <strong>Nenhuma meta para o período exibido</strong>

          <span>
            Consulte as metas cadastradas ou escolha um período que inclua uma
            meta.
          </span>
        </div>
      )}
    </article>
  );
}

export default SpendingGoalGauge;
