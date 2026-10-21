import { useId } from "react";
import type { BudgetPipelineData } from "../services/budgetPipelineService";
import {
  detectarSobreposicoes,
  moedaOrcamento,
  percentualOrcamento,
  dataOrcamento,
} from "../services/budgetAnalysis";
import "../styles/budget-percentage.css";

export default function BudgetPercentageChart({
  dados,
}: {
  dados: BudgetPipelineData[];
}) {
  const id = useId();
  const linhas = dados.map((item) => {
    const valido =
      Number.isFinite(item.valorPlanejado) &&
      item.valorPlanejado > 0 &&
      Number.isFinite(item.valorUtilizado);
    return {
      item,
      percentual: valido
        ? (item.valorUtilizado / item.valorPlanejado) * 100
        : null,
    };
  });
  const maior = Math.max(
    100,
    ...linhas.map((l) =>
      l.percentual === null ? 0 : Math.max(0, l.percentual),
    ),
  );
  const maximo = Math.ceil(maior / 100) * 100;
  const limite = (100 / maximo) * 100;
  const sobrepostos = detectarSobreposicoes(dados);
  return (
    <div className="bp-panel" aria-labelledby={`bp-title-${id}`}>
      <h3 id={`bp-title-${id}`}>Utilização de cada orçamento</h3>
      <p className="bp-description">
        Azul: consumo até o limite · vermelho: excedente · cinza: faixa
        planejada. A referência de 100% usa a mesma escala em todas as linhas.
      </p>
      {sobrepostos.length > 0 && (
        <p className="bp-warning" role="status">
          Há {sobrepostos.length} par(es) de orçamentos com períodos e critérios
          sobrepostos. Um lançamento pode participar de mais de um orçamento; os
          totais somados não representam despesa única consolidada. Confira os
          critérios de cada linha.
        </p>
      )}
      <div className="bp-axis" aria-hidden="true">
        <span>0%</span>
        <strong
          style={{
            left: `${limite}%`,
            transform:
              limite === 100 ? "translateX(-100%)" : "translateX(-50%)",
          }}
        >
          100% · planejado
        </strong>
        {maximo > 100 && <span>{percentualOrcamento(maximo)}%</span>}
      </div>
      <div className="bp-rows">
        {linhas.map(({ item, percentual }) => {
          const positivo = Math.max(0, percentual ?? 0);
          const excedente =
            percentual !== null
              ? Math.max(0, item.valorUtilizado - item.valorPlanejado)
              : null;
          return (
            <article className="bp-row" key={item.id}>
              <div className="bp-row-name">
                <strong>
                  {item.nome} <small>#{item.id}</small>
                </strong>
                <span>
                  {dataOrcamento(item.dataInicio)} —{" "}
                  {dataOrcamento(item.dataFim)}
                </span>
                <span>
                  {item.centroCusto || "Todos os centros"} ·{" "}
                  {item.categoria || "Todas as categorias"}
                </span>
              </div>
              <div className="bp-row-chart">
                {percentual === null ? (
                  <p className="bp-invalid">
                    Percentual não calculável: confira o valor planejado e o
                    utilizado.
                  </p>
                ) : (
                  <>
                    <div
                      className="bp-track"
                      role="img"
                      aria-label={`${item.nome}: ${percentualOrcamento(percentual)}% utilizado. ${moedaOrcamento(item.valorUtilizado)} de ${moedaOrcamento(item.valorPlanejado)}. Excedente: ${moedaOrcamento(excedente ?? 0)}.`}
                    >
                      <div
                        className="bp-planned"
                        style={{ width: `${limite}%` }}
                      />
                      <div
                        className="bp-used"
                        style={{
                          width: `${(Math.min(positivo, 100) / maximo) * 100}%`,
                        }}
                      />
                      {positivo > 100 && (
                        <div
                          className="bp-excess"
                          style={{
                            left: `${limite}%`,
                            width: `${((positivo - 100) / maximo) * 100}%`,
                          }}
                        />
                      )}
                      <span
                        className="bp-limit"
                        style={{ left: `${limite}%` }}
                      />
                    </div>
                    <div className="bp-values">
                      <span>
                        {moedaOrcamento(item.valorUtilizado)} de{" "}
                        {moedaOrcamento(item.valorPlanejado)}
                      </span>
                      <strong className={positivo > 100 ? "bp-exceeded" : ""}>
                        {percentualOrcamento(percentual)}%
                      </strong>
                    </div>
                    <p
                      className={`bp-status ${positivo > 100 ? "bp-exceeded" : ""}`}
                    >
                      {positivo > 100
                        ? `▲ ${moedaOrcamento(excedente ?? 0)} acima do planejado`
                        : percentual < 0
                          ? "Utilização negativa: confira estornos e ajustes; a barra não representa consumo positivo."
                          : percentual === 0
                            ? "Sem consumo registrado para estes critérios."
                            : `Disponível: ${moedaOrcamento(item.valorDisponivel)}`}
                    </p>
                  </>
                )}
              </div>
            </article>
          );
        })}
      </div>
      <p className="bp-footnote">
        O limite de cada orçamento é 100%, indicado pelo traço escuro. Não
        redistribuímos despesas entre orçamentos nem presumimos erro porque um
        deles ficou zerado. Valores registrados podem incluir lançamentos em
        aberto.
      </p>
    </div>
  );
}
