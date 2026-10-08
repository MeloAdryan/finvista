interface KpiComparisonProps {
  rotulo?: string;
  atual: number;
  anterior?: number | null;
  variacao?: number | null;
  despesa?: boolean;
  margem?: boolean;
  margemComparavel?: boolean;
}

const moeda = (valor: number) =>
  valor.toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
  });

const numero = (valor: number) =>
  valor.toLocaleString("pt-BR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

function KpiComparison({
  rotulo = "mês anterior",
  atual,
  anterior,
  variacao,
  despesa = false,
  margem = false,
  margemComparavel = true,
}: KpiComparisonProps) {
  if (
    anterior == null ||
    !Number.isFinite(anterior) ||
    !Number.isFinite(atual)
  ) {
    return (
      <div className="kpi-comparison kpi-comparison-neutral">
        <span>Comparação indisponível</span>
      </div>
    );
  }

  if (margem && !margemComparavel) {
    return (
      <div className="kpi-comparison kpi-comparison-neutral">
        <span>Margem não comparável</span>
        <small>Receita zerada em um dos períodos</small>
      </div>
    );
  }

  const diferenca = Math.round((atual - anterior) * 100) / 100;
  const semVariacao = diferenca === 0;

  const favoravel = despesa
    ? diferenca < 0
    : diferenca > 0;

  const classe = semVariacao
    ? "neutral"
    : favoravel
      ? "positive"
      : "negative";

  const direcao = diferenca > 0 ? "↑" : "↓";

  let texto: string;

  if (semVariacao) {
    texto = "Sem variação";
  } else if (margem) {
    texto = `${direcao} ${numero(Math.abs(diferenca))} p.p.`;
  } else if (anterior <= 0) {
    texto = `${direcao} ${moeda(Math.abs(diferenca))}`;
  } else if (variacao != null && Number.isFinite(variacao)) {
    texto = `${direcao} ${numero(Math.abs(variacao))}%`;
  } else {
    texto = `${direcao} ${moeda(Math.abs(diferenca))}`;
  }

  return (
    <div className={`kpi-comparison kpi-comparison-${classe}`}>
      <span>{texto} em relação ao {rotulo}</span>

      <small>
        {rotulo}: {" "}
        {margem ? `${numero(anterior)}%` : moeda(anterior)}
      </small>
    </div>
  );
}

export default KpiComparison;