type Props = { size?: number; className?: string };

/** Monograma FV (favicon escolhido). Os traços usam pathLength=1 para poder ser "desenhados" via CSS. */
export default function LogoFV({ size = 112, className }: Props) {
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 120 120"
      role="img"
      aria-label="FinVista"
    >
      <rect width="120" height="120" rx="30" fill="#162540" />
      <g
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="9"
      >
        <path
          className="fv-draw fv-f1"
          pathLength={1}
          d="M28 28V92"
          stroke="#fff"
        />
        <path
          className="fv-draw fv-f2"
          pathLength={1}
          d="M28 28H50"
          stroke="#fff"
        />
        <path
          className="fv-draw fv-f3"
          pathLength={1}
          d="M28 57H46"
          stroke="#fff"
        />
        <path
          className="fv-draw fv-v1"
          pathLength={1}
          d="M64 28L77 92"
          stroke="#fff"
        />
        <path
          className="fv-draw fv-v2"
          pathLength={1}
          d="M77 92L104 28"
          stroke="#e8892b"
        />
      </g>
    </svg>
  );
}
