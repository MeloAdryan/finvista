import { useContext, useEffect, useState } from "react";
import { FinancialViewContext } from "../contexts/financialViewContext";
import {
  getBudgetPipeline,
  type BudgetPipelineData,
} from "../services/budgetPipelineService";
import {
  getExpenseAnalysis,
  type ExpenseAnalysis,
} from "../services/expenseExplorerService";
import {
  detectarSobreposicoes,
  moedaOrcamento,
} from "../services/budgetAnalysis";
import "../styles/budget-analysis.css";
import ExpenseExplorer from "../components/ExpenseExplorer";
import CostClassificationEditor from "../components/CostClassificationEditor";
import "../styles/dashboard.css";
export default function CostCenters() {
  const [revision, setRevision] = useState(0);
  const filtro = useContext(FinancialViewContext);
  const [visao, setVisao] = useState<{
    filtro: typeof filtro;
    gastos: ExpenseAnalysis;
    limites: BudgetPipelineData[];
  } | null>(null);
  const [falha, setFalha] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    void Promise.all([
      getExpenseAnalysis({}, controller.signal, filtro ?? undefined),
      getBudgetPipeline(filtro ?? undefined),
    ])
      .then(([gastos, limites]) => {
        if (!controller.signal.aborted) {
          setFalha(false);
          setVisao({ filtro, gastos, limites });
        }
      })
      .catch(() => {
        if (!controller.signal.aborted) setFalha(true);
      });
    return () => controller.abort();
  }, [filtro]);

  const atual = visao?.filtro === filtro ? visao : null;
  const limites = atual?.limites.filter((item) => item.centroCusto) ?? [];
  const centros = Array.from(
    new Set([
      ...(atual?.gastos.centros.map((item) => item.nome) ?? []),
      ...limites.flatMap((item) =>
        item.centroCusto ? [item.centroCusto] : [],
      ),
    ]),
  ).sort((a, b) => a.localeCompare(b, "pt-BR"));
  const orcado = limites.reduce(
    (total, item) => total + Number(item.valorPlanejado),
    0,
  );
  return (
    <div className="dashboard">
      <section className="ba-filter" aria-labelledby="center-budget-title">
        <h2 id="center-budget-title">Execução por centro de custo</h2>
        <p>
          Usa o recorte global. O registrado nos limites segue também as
          categorias de cada orçamento.
        </p>
        {falha ? (
          <p role="alert">Não foi possível consultar a execução por centro.</p>
        ) : !atual ? (
          <p role="status">Carregando execução…</p>
        ) : (
          <>
            {detectarSobreposicoes(limites).length > 0 && (
              <p className="ba-overlap">
                Há limites sobrepostos. Uma despesa pode aparecer em mais de um
                orçamento.
              </p>
            )}
            <div className="ba-summary">
              <div>
                <span>Gasto do recorte</span>
                <strong>{moedaOrcamento(atual.gastos.total)}</strong>
              </div>
              <div>
                <span>Orçado para os centros</span>
                <strong>{moedaOrcamento(orcado)}</strong>
              </div>
            </div>
            <div
              className="ba-table-scroll"
              tabIndex={0}
              role="region"
              aria-label="Execução por centro"
            >
              <table className="ba-table">
                <thead>
                  <tr>
                    <th scope="col">Centro</th>
                    <th scope="col">Gasto total</th>
                    <th scope="col">Orçado</th>
                    <th scope="col">Registrado nos limites</th>
                    <th scope="col">Utilização</th>
                  </tr>
                </thead>
                <tbody>
                  {centros.map((nome) => {
                    const doCentro = limites.filter(
                      (item) => item.centroCusto === nome,
                    );
                    const gasto =
                      atual.gastos.centros.find((item) => item.nome === nome)
                        ?.atual ?? 0;
                    const limite = doCentro.reduce(
                      (total, item) => total + Number(item.valorPlanejado),
                      0,
                    );
                    const utilizado = doCentro.reduce(
                      (total, item) => total + Number(item.valorUtilizado),
                      0,
                    );
                    const percentual =
                      limite > 0 ? (utilizado / limite) * 100 : null;
                    return (
                      <tr key={nome}>
                        <th scope="row">{nome}</th>
                        <td>{moedaOrcamento(gasto)}</td>
                        <td>
                          {doCentro.length
                            ? moedaOrcamento(limite)
                            : "Sem orçamento"}
                        </td>
                        <td>
                          {doCentro.length ? moedaOrcamento(utilizado) : "—"}
                        </td>
                        <td>
                          {percentual == null ? (
                            "—"
                          ) : (
                            <>
                              <meter
                                aria-label={`Utilização do orçamento de ${nome}`}
                                min={0}
                                max={100}
                                value={Math.min(100, Math.max(0, percentual))}
                              />
                              <br />
                              {percentual.toLocaleString("pt-BR", {
                                maximumFractionDigits: 1,
                              })}
                              %{percentual > 100 ? " · Excedido" : ""}
                            </>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </>
        )}
      </section>
      <ExpenseExplorer prioridade="centros" revision={revision} />
      <CostClassificationEditor onSaved={() => setRevision((v) => v + 1)} />
    </div>
  );
}
