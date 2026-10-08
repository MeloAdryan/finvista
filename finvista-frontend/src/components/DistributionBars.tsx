import type { ExpenseRow } from "../services/expenseExplorerService";
import { moedaFinanceira } from "../services/financialVisualization";
export default function DistributionBars({
  dados,
  total,
  selecionado,
  onSelecionar,
  destaques = 1,
}: {
  dados: ExpenseRow[];
  total: number;
  selecionado?: string;
  onSelecionar: (nome: string) => void;
  destaques?: number;
}) {
  const minimo = Math.min(0, ...dados.flatMap((m) => [m.atual, m.anterior]));
  const maximo = Math.max(0, ...dados.flatMap((m) => [m.atual, m.anterior]));
  const escala = maximo - minimo || 1;
  return (
    <div className="ax-bars">
      {dados.map((m, i) => (
        <button
          type="button"
          key={m.nome}
          className="ax-bar-row"
          aria-pressed={selecionado === m.nome}
          onClick={() => onSelecionar(m.nome)}
          title={`${m.nome}: ${moedaFinanceira(m.atual)}; anterior ${moedaFinanceira(m.anterior)}; diferença ${moedaFinanceira(m.diferenca)}`}
        >
          <span className="ax-bar-name">{m.nome}</span>
          <span className="ax-track">
            <span
              className={`ax-fill ${i < destaques ? "ax-emphasis" : ""}`}
              style={{
                left: `${((Math.min(0, m.atual) - minimo) / escala) * 100}%`,
                width: `${(Math.abs(m.atual) / escala) * 100}%`,
              }}
            />
            <i
              className="ax-zero"
              style={{ left: `${(-minimo / escala) * 100}%` }}
            />
            <i
              className="ax-previous"
              style={{ left: `${((m.anterior - minimo) / escala) * 100}%` }}
            />
          </span>
          <span className="ax-bar-value">
            <strong>{moedaFinanceira(m.atual)}</strong>
            <small>
              {total > 0
                ? `${((m.atual / total) * 100).toLocaleString("pt-BR", { maximumFractionDigits: 1 })}% do total`
                : "Participação não comparável"}
            </small>
            <small>Anterior: {moedaFinanceira(m.anterior)}</small>
          </span>
        </button>
      ))}
    </div>
  );
}
