import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAnalysisPeriod } from "../contexts/analysisPeriodContext";
import { brl, dataBR } from "../services/dashboardAnalysisService";
import {
  getFinancialAlerts,
  type FinancialAlerts,
} from "../services/financialAlertsService";
import "../styles/financial-alerts.css";

const gravidades = {
  INFORMATIVO: "Informativo",
  ATENCAO: "Atenção",
  CRITICO: "Crítico",
};
const motivosComparacao = {
  DISPONIVEL:
    "A comparação usa os registros disponíveis; a cobertura completa das importações não foi confirmada.",
  SEM_REGISTROS:
    "Aumento de despesas não avaliado: faltam registros em um dos períodos.",
  BASE_NAO_POSITIVA:
    "Aumento de despesas não avaliado: o total anterior precisa ser positivo.",
  PERIODO_EM_ANDAMENTO:
    "Aumento de despesas não avaliado: o período selecionado ainda não terminou.",
};

export default function FinancialAlertsPanel({
  clienteId,
  inicio,
  fim,
  categoria,
}: {
  clienteId: number;
  inicio: string;
  fim: string;
  categoria: string | null;
}) {
  const { atualizarPeriodo } = useAnalysisPeriod();
  const [dados, setDados] = useState<FinancialAlerts | null>(null);
  const [erro, setErro] = useState("");
  const [tentativa, setTentativa] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    async function carregar() {
      try {
        const resultado = await getFinancialAlerts(
          inicio,
          fim,
          categoria,
          controller.signal,
        );
        if (controller.signal.aborted) return;
        if (
          resultado.clienteId !== clienteId ||
          resultado.inicio !== inicio ||
          resultado.fim !== fim ||
          (resultado.categoria ?? "") !== (categoria ?? "")
        ) {
          throw new Error(
            "A empresa ou os filtros mudaram. Atualize o painel.",
          );
        }
        setDados(resultado);
      } catch (error) {
        if (!controller.signal.aborted) {
          setErro(
            error instanceof Error
              ? error.message
              : "Falha ao consultar alertas.",
          );
        }
      }
    }
    void carregar();
    return () => controller.abort();
  }, [clienteId, inicio, fim, categoria, tentativa]);

  return (
    <section className="fa-panel" aria-labelledby="financial-alerts-title">
      <div className="fa-heading">
        <div>
          <span className="fa-label">Acompanhamento financeiro</span>
          <h2 id="financial-alerts-title">Pontos de atenção · competência</h2>
        </div>
        {dados && (
          <span className="fa-count">
            {dados.total === 0
              ? "Nenhum aviso"
              : `${dados.alertas.length} de ${dados.total} avisos`}
          </span>
        )}
        <Link to="/alertas">Ver central de alertas</Link>
      </div>
      <details className="fa-method">
        <summary>Como os alertas são avaliados</summary>
        <p className="fa-note">
          Os alertas cadastrados usam competência, independentemente do critério
          analítico dos gráficos. Metas e orçamentos usam seus períodos e
          escopos completos. O filtro de categoria do painel se aplica ao
          aumento de despesas. Valores registrados podem incluir lançamentos em
          aberto.
        </p>
      </details>
      {erro ? (
        <div role="status" className="fa-state">
          <p>{erro}</p>
          <button
            type="button"
            onClick={() => {
              setErro("");
              setTentativa(tentativa + 1);
            }}
          >
            Tentar novamente
          </button>
        </div>
      ) : !dados ? (
        <p role="status">Consultando pontos de atenção...</p>
      ) : (
        <>
          {dados.alertas.length === 0 ? (
            <div className="fa-state fa-empty">
              <span className="fa-empty-icon" aria-hidden="true">
                ✓
              </span>
              <div>
                <strong>Nenhum alerta nas regras avaliadas</strong>
                <p>
                  Consulte os critérios de avaliação para entender a cobertura
                  deste período.
                </p>
              </div>
            </div>
          ) : (
            <div className="fa-grid">
              {dados.alertas.map((alerta) => {
                const aumento = alerta.tipo === "AUMENTO_DESPESAS";
                const destino = aumento
                  ? "/despesas"
                  : alerta.tipo === "META_LIMITE"
                    ? `/metas#meta-${alerta.entidadeId}`
                    : `/orcamentos?orcamento=${alerta.entidadeId}`;
                return (
                  <article
                    key={alerta.id}
                    className={`fa-card fa-${alerta.gravidade.toLowerCase()}`}
                  >
                    <div className="fa-badges">
                      <span className="fa-severity">
                        {gravidades[alerta.gravidade]}
                      </span>
                      {alerta.encerrado && <span>Período encerrado</span>}
                    </div>
                    <h3>{alerta.titulo}</h3>
                    <p>{alerta.motivo}</p>
                    <p className="fa-period">
                      {dataBR(alerta.inicio)} — {dataBR(alerta.fim)}
                    </p>
                    <strong className="fa-value">
                      {alerta.percentual.toLocaleString("pt-BR", {
                        maximumFractionDigits: 2,
                      })}
                      %<span>{aumento ? " de aumento" : " utilizado"}</span>
                    </strong>
                    <details>
                      <summary>Ver detalhes</summary>
                      <dl>
                        <div>
                          <dt>Registrado</dt>
                          <dd>{brl(alerta.valorAtual)}</dd>
                        </div>
                        <div>
                          <dt>{aumento ? "Período anterior" : "Limite"}</dt>
                          <dd>{brl(alerta.valorBase)}</dd>
                        </div>
                        <div>
                          <dt>
                            {aumento
                              ? "Aumento"
                              : alerta.diferenca > 0
                                ? "Excedente"
                                : "Saldo disponível"}
                          </dt>
                          <dd>{brl(Math.abs(alerta.diferenca))}</dd>
                        </div>
                        <div>
                          <dt>Limiar do aviso</dt>
                          <dd>
                            {alerta.limiarPercentual.toLocaleString("pt-BR")}%
                          </dd>
                        </div>
                      </dl>
                      {alerta.inicioAnterior && alerta.fimAnterior && (
                        <p>
                          Comparação: {dataBR(alerta.inicioAnterior)} —{" "}
                          {dataBR(alerta.fimAnterior)}.
                        </p>
                      )}
                      <p>
                        Categoria: {alerta.categoria ?? "Todas"}. Centro:{" "}
                        {alerta.centroCusto ?? "Todos"}.
                      </p>
                    </details>
                    <Link
                      to={destino}
                      onClick={() => {
                        if (aumento)
                          atualizarPeriodo({
                            inicio: alerta.inicio,
                            fim: alerta.fim,
                            categoria: alerta.categoria ?? undefined,
                          });
                      }}
                    >
                      {aumento
                        ? "Ver despesas"
                        : alerta.tipo === "META_LIMITE"
                          ? "Ver meta"
                          : "Ver orçamento"}
                    </Link>
                  </article>
                );
              })}
            </div>
          )}
          <p className="fa-note fa-comparison" role="status">
            {motivosComparacao[dados.comparacao]}
          </p>
          {dados.total > dados.alertas.length && (
            <p className="fa-note">
              Exibindo os três avisos prioritários. Consulte Metas e Orçamentos
              para os demais detalhes.
            </p>
          )}
        </>
      )}
    </section>
  );
}
