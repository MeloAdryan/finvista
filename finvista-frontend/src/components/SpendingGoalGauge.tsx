import type {
  SpendingGoal,
  SpendingGoalStatus,
} from "../services/spendingGoalService";

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
  const formatarMoeda = (valor: number) =>
    Number(valor || 0).toLocaleString("pt-BR", {
      style: "currency",
      currency: "BRL",
    });

  const formatarPercentual = (valor: number) =>
    Number(valor || 0).toLocaleString("pt-BR", {
      minimumFractionDigits: 1,
      maximumFractionDigits: 1,
    });

  const obterTextoStatus = (
    status: SpendingGoalStatus,
  ) => {
    switch (status) {
      case "EXCEDIDA":
        return "Limite ultrapassado";

      case "ALERTA":
        return "Próximo do limite";

      default:
        return "Dentro do limite";
    }
  };

  if (!meta) {
    return (
      <article className="executive-panel goal-panel">
        <div className="executive-panel-header">
          <div>
            <span className="executive-eyebrow">
              Controle de gastos
            </span>

            <h2>Limite da meta</h2>

            <p>
              Acompanhamento do orçamento
              selecionado
            </p>
          </div>
        </div>

        <div className="goal-empty">
          <strong>
            Nenhuma meta cadastrada
          </strong>

          <span>
            Cadastre uma meta para acompanhar
            sua utilização neste painel.
          </span>
        </div>
      </article>
    );
  }

  /*
   * Escala visual do odômetro:
   *
   * 0%   → início
   * 100% → limite da meta
   * 120% → limite visual máximo
   *
   * Se o gasto passar de 120%, mostramos o
   * percentual real no texto, mas mantemos
   * o arco no máximo visual.
   */
  const percentualReal = Number(
    meta.percentualUtilizado || 0,
  );

  const percentualVisual = Math.min(
    Math.max(percentualReal, 0),
    120,
  );

  /*
   * O SVG utiliza pathLength="100".
   *
   * Isso significa que não precisamos
   * calcular manualmente o comprimento
   * físico do arco.
   */
  const progressoVisual =
    (percentualVisual / 120) * 100;

  const dashOffset =
    100 - progressoVisual;

  return (
    <article
      className={[
        "executive-panel",
        "goal-panel",
        `goal-status-${meta.status.toLowerCase()}`,
      ].join(" ")}
    >
      <div className="executive-panel-header goal-panel-header">
        <div>
          <span className="executive-eyebrow">
            Controle de gastos
          </span>

          <h2>Limite da meta</h2>

          <p>
            Acompanhamento do orçamento
            selecionado
          </p>
        </div>

        {metas.length > 0 && (
          <label className="goal-selector">
            <span>Meta</span>

            <select
              value={
                metaSelecionadaId ?? ""
              }
              onChange={(event) =>
                onSelecionarMeta(
                  Number(
                    event.target.value,
                  ),
                )
              }
            >
              {metas.map((item) => (
                <option
                  key={item.id}
                  value={item.id}
                >
                  {item.tipo} ·{" "}
                  {formatarMoeda(
                    Number(
                      item.valorLimite,
                    ),
                  )}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      <div className="goal-gauge-content">
        <div className="goal-gauge">
          <svg
            className="goal-gauge-svg"
            viewBox="0 0 240 135"
            role="img"
            aria-label={`Meta utilizada em ${formatarPercentual(
              percentualReal,
            )}%`}
          >
            {/* arco de fundo */}
            <path
              className="goal-gauge-track"
              d="M 25 115 A 95 95 0 0 1 215 115"
              pathLength="100"
              fill="none"
            />

            {/* arco preenchido */}
            <path
              className="goal-gauge-progress"
              d="M 25 115 A 95 95 0 0 1 215 115"
              pathLength="100"
              fill="none"
              strokeDasharray="100"
              strokeDashoffset={
                dashOffset
              }
            />
          </svg>

          <div className="goal-gauge-value">
            <strong>
              {formatarPercentual(
                percentualReal,
              )}
              %
            </strong>

            <span>
              {obterTextoStatus(
                meta.status,
              )}
            </span>
          </div>
        </div>

        <div className="goal-summary">
          <div className="goal-summary-item">
            <span>Limite</span>

            <strong>
              {formatarMoeda(
                Number(
                  meta.valorLimite,
                ),
              )}
            </strong>
          </div>

          <div className="goal-summary-item">
            <span>Utilizado</span>

            <strong>
              {formatarMoeda(
                Number(
                  meta.gastoAtual,
                ),
              )}
            </strong>
          </div>

          <div className="goal-summary-item">
            <span>
              {Number(meta.saldoMeta) >=
              0
                ? "Disponível"
                : "Excedente"}
            </span>

            <strong>
              {formatarMoeda(
                Math.abs(
                  Number(
                    meta.saldoMeta,
                  ),
                ),
              )}
            </strong>
          </div>
        </div>

        <div className="goal-status-line">
          <span className="goal-status-dot" />

          <strong>
            {obterTextoStatus(
              meta.status,
            )}
          </strong>

          <span>
            Alerta configurado em{" "}
            {meta.percentualAlerta}%
          </span>
        </div>
      </div>
    </article>
  );
}

export default SpendingGoalGauge;