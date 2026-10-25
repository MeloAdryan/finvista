import { FinancialViewContext } from "../contexts/financialViewContext";
import { useContext, useId } from "react";
import "../styles/financial-help.css";

const explicacoes = {
  receita:
    "Soma dos valores contabilizados dos lançamentos de receita do período. Pode incluir valores em aberto; não representa apenas recebimentos. Cada lançamento entra uma vez, mesmo quando possui rateios.",
  despesa:
    "Soma dos valores contabilizados dos lançamentos de despesa do período. Pode incluir valores em aberto; não representa apenas pagamentos. Os rateios distribuem o valor entre categorias e centros sem duplicar o total.",
  resultado:
    "Receita menos despesa no mesmo período. Um resultado negativo indica que as despesas superaram as receitas contabilizadas. Esse indicador não é o saldo da conta bancária.",
  margem:
    "Resultado dividido pela receita, multiplicado por 100. Quando a receita é zero, não é possível calcular essa proporção; o indicador fica não calculável, e a comparação de margem não é aplicável.",
} as const;

type Props = { termo: keyof typeof explicacoes };

export default function FinancialHelp({ termo }: Props) {
  const id = useId();
  const filter = useContext(FinancialViewContext);
  const baixa =
    filter?.criterio === "BAIXA" || filter?.criterio === "BAIXA_EXTRATO";
  const description =
    baixa && (termo === "receita" || termo === "despesa")
      ? `Soma líquida das ${termo === "receita" ? "entradas recebidas" : "saídas pagas"} pela data efetiva, na fonte e no período selecionados. Transferências e saldos iniciais não entram nesse indicador. Rateios não multiplicam o valor do movimento.`
      : explicacoes[termo];
  return (
    <details className="financial-help">
      <summary aria-label={`Entender ${termo}`} aria-controls={id}>
        <span aria-hidden="true">?</span>
      </summary>
      <div id={id} className="financial-help-text">
        {description}
      </div>
    </details>
  );
}
