import { chartTones } from "../services/chartAppearance";
export function FinancialChartPaint({ id }: { id: string }) {
  return (
    <svg
      aria-hidden="true"
      focusable="false"
      style={{
        position: "absolute",
        width: 0,
        height: 0,
        pointerEvents: "none",
      }}
    >
      <defs>
        {chartTones.map((t) => (
          <linearGradient key={t} id={`${id}-${t}`} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor={`var(--fv-chart-${t}-top)`} />
            <stop offset="48%" stopColor={`var(--fv-chart-${t})`} />
            <stop offset="100%" stopColor={`var(--fv-chart-${t}-bottom)`} />
          </linearGradient>
        ))}
        {chartTones.map((t) => (
          <linearGradient
            key={t}
            id={`${id}-${t}-area`}
            x1="0"
            y1="0"
            x2="0"
            y2="1"
          >
            <stop
              offset="0%"
              stopColor={`var(--fv-chart-${t})`}
              stopOpacity=".34"
            />
            <stop
              offset="100%"
              stopColor={`var(--fv-chart-${t})`}
              stopOpacity=".025"
            />
          </linearGradient>
        ))}
      </defs>
    </svg>
  );
}
