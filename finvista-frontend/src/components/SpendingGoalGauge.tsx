import type { SpendingGoal } from "../services/spendingGoalService";

import GoalProgress from "./GoalProgress";

import { periodoMeta } from "../services/goalPresentation";

interface SpendingGoalGaugeProps {
  meta: SpendingGoal | null;
  metas: SpendingGoal[];
  metaSelecionadaId: number | null;
  onSelecionarMeta: (id: number) => void;
}

function SpendingGoalGauge({
  meta,
  metas,
  metaSelecionadaId,
  onSelecionarMeta,
}: SpendingGoalGaugeProps) {
  const selecionarMeta = (valor: string) => {
    if (!valor) {
      return;
    }

    const id = Number(valor);

    if (Number.isFinite(id)) {
      onSelecionarMeta(id);
    }
  };

  return (
    <article className="executive-panel goal-panel">
      <div className="executive-panel-header goal-panel-header">
        <div>
          <span className="executive-eyebrow">
            Controle de gastos
          </span>

          <h2>Limite da meta</h2>

          <p>
            Acompanhamento do período próprio da meta selecionada
          </p>
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
        />
      ) : (
        <div className="goal-empty">
          <strong>Nenhuma meta para o período exibido</strong>

          <span>
            Consulte as metas cadastradas ou escolha um período
            que inclua uma meta.
          </span>
        </div>
      )}
    </article>
  );
}

export default SpendingGoalGauge;