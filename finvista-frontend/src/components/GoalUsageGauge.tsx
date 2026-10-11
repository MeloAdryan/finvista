import type { SpendingGoal } from "../services/spendingGoalService";
import { percentual } from "../services/goalPresentation";

interface Props {
  meta: SpendingGoal;
  ritmo?: number | null;
  projecao?: number | null;
  aoAmpliar?: () => void;
}

const limitar = (v: number) => Math.max(0, Math.min(100, v));
function ponto(v: number, raio = 120) {
  const angulo = Math.PI + (limitar(v) / 100) * Math.PI;
  return { x: 160 + raio * Math.cos(angulo), y: 165 + raio * Math.sin(angulo) };
}
function arco(inicio: number, fim: number) {
  const a = ponto(inicio),
    b = ponto(fim);
  return `M ${a.x} ${a.y} A 120 120 0 0 1 ${b.x} ${b.y}`;
}

export default function GoalUsageGauge({
  meta,
  ritmo,
  projecao,
  aoAmpliar,
}: Props) {
  const uso = Number.isFinite(Number(meta.percentualUtilizado))
    ? Number(meta.percentualUtilizado)
    : 0;
  const alerta = limitar(Number(meta.percentualAlerta) || 0);
  const needle = ponto(uso, 95);
  const cor =
    meta.status === "EXCEDIDA"
      ? "#ab3030"
      : meta.status === "ALERTA"
        ? "#a36a16"
        : "#2f7d5d";
  const valor =
    uso > 100 ? `${percentual(uso / 100)}× o limite` : `${percentual(uso)}%`;
  const descricao = `Meta ${meta.id}: consumo ${percentual(uso)} por cento do limite. Alerta ${percentual(alerta)} por cento.`;
  const conteudo = (
    <>
      <svg viewBox="0 0 320 205" aria-hidden="true" focusable="false">
        <path
          d={arco(0, 100)}
          fill="none"
          stroke="#e8edf2"
          strokeWidth="20"
          strokeLinecap="round"
        />
        {alerta > 0 && (
          <path
            d={arco(0, alerta)}
            fill="none"
            stroke="#c9e3d8"
            strokeWidth="20"
          />
        )}
        {alerta < 100 && (
          <path
            d={arco(alerta, 100)}
            fill="none"
            stroke="#f0dfbb"
            strokeWidth="20"
          />
        )}
        {uso > 0 && (
          <path
            d={arco(0, limitar(uso))}
            fill="none"
            stroke={cor}
            strokeWidth="12"
            strokeLinecap="round"
          />
        )}
        {[0, 25, 50, 75, 100].map((v) => {
          const a = ponto(v, 135),
            b = ponto(v, 142);
          return (
            <line
              key={v}
              x1={a.x}
              y1={a.y}
              x2={b.x}
              y2={b.y}
              stroke="#64748b"
              strokeWidth="2"
            />
          );
        })}
        {ritmo != null && Number.isFinite(ritmo) && (
          <circle
            {...{ cx: ponto(ritmo, 120).x, cy: ponto(ritmo, 120).y }}
            r="5"
            fill="#64748b"
            stroke="white"
            strokeWidth="2"
          >
            <title>Ritmo ideal: {percentual(ritmo)}%</title>
          </circle>
        )}
        {projecao != null && Number.isFinite(projecao) && (
          <circle
            {...{ cx: ponto(projecao, 106).x, cy: ponto(projecao, 106).y }}
            r="5"
            fill="#2563eb"
            stroke="white"
            strokeWidth="2"
          >
            <title>Estimativa final: {percentual(projecao)}%</title>
          </circle>
        )}
        <line
          x1="160"
          y1="165"
          x2={needle.x}
          y2={needle.y}
          stroke="#173955"
          strokeWidth="4"
          strokeLinecap="round"
        />
        <circle
          cx="160"
          cy="165"
          r="9"
          fill={cor}
          stroke="white"
          strokeWidth="3"
        />
        <text x="30" y="192" textAnchor="middle">
          0%
        </text>
        <text x="160" y="26" textAnchor="middle">
          50%
        </text>
        <text x="284" y="192" textAnchor="middle">
          100%
        </text>
      </svg>
      <span className="goal-dial-caption">
        <span>Uso da meta</span>
        <strong style={{ color: cor }}>{valor}</strong>
        <small>
          {aoAmpliar
            ? "Clique para ampliar o acompanhamento"
            : uso > 100
              ? "Ponteiro no limite; consumo real indicado acima"
              : "Consumo total em relação ao limite"}
        </small>
      </span>
    </>
  );
  return aoAmpliar ? (
    <button
      type="button"
      className="goal-dial goal-dial-trigger"
      aria-haspopup="dialog"
      aria-label={`Ampliar acompanhamento. ${descricao}`}
      onClick={aoAmpliar}
    >
      {conteudo}
    </button>
  ) : (
    <div className="goal-dial" role="img" aria-label={descricao}>
      {conteudo}
    </div>
  );
}
