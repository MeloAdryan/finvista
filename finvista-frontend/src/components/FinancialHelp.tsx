import { useId } from "react";
import "../styles/financial-help.css";

const explicacoes = {
  receita: "Soma dos valores contabilizados dos lançamentos de receita do período. Pode incluir valores em aberto; não representa apenas recebimentos. Cada lançamento entra uma vez, mesmo quando possui rateios.",
  despesa: "Soma dos valores contabilizados dos lançamentos de despesa do período. Pode incluir valores em aberto; não representa apenas pagamentos. Os rateios distribuem o valor entre categorias e centros sem duplicar o total.",
  resultado: "Receita menos despesa no mesmo período. Um resultado negativo indica que as despesas superaram as receitas contabilizadas. Esse indicador não é o saldo da conta bancária.",
  margem: "Resultado dividido pela receita, multiplicado por 100. Quando a receita é zero, não é possível calcular essa proporção; o painel apresenta 0%, e a comparação de margem não é aplicável.",
} as const;

type Props = { termo: keyof typeof explicacoes };

export default function FinancialHelp({ termo }: Props) {
  const id = useId();
  return (
    <details className="financial-help">
      <summary aria-label={`Entender ${termo}`} aria-controls={id}>
        <span aria-hidden="true">?</span>
      </summary>
      <div id={id} className="financial-help-text">{explicacoes[termo]}</div>
    </details>
  );
}
