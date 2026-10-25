import { useContext, useEffect, useState } from "react";
import { FinancialViewContext } from "../contexts/financialViewContext";
import GoalEvolution from "./GoalEvolution";
import GoalUsageGauge from "./GoalUsageGauge";
import GoalDetailDialog from "./GoalDetailDialog";
import type { SpendingGoal } from "../services/spendingGoalService";
import {
  obterAcompanhamento,
  type GoalTimeline,
} from "../services/spendingGoalTimelineService";
import "../styles/goal-progress.css";
import {
  moedaMeta,
  percentual,
  periodoMeta,
} from "../services/goalPresentation";
const dataBR = (s: string) => s.split("-").reverse().join("/");
function ritmoMeta(m: SpendingGoal) {
  const inicio = Date.parse(m.dataInicio),
    fim = Date.parse(m.dataFim),
    hoje = Date.parse(m.dataReferencia);
  if (![inicio, fim, hoje].every(Number.isFinite) || fim < inicio) return 0;
  return Math.max(
    0,
    Math.min(
      100,
      ((hoje - inicio + 86400000) / (fim - inicio + 86400000)) * 100,
    ),
  );
}
export default function GoalProgress({
  meta,
  automatico = false,
  resumo = false,
  evolucaoEmDialogo = false,
}: {
  meta: SpendingGoal;
  automatico?: boolean;
  resumo?: boolean;
  evolucaoEmDialogo?: boolean;
}) {
  const viewFilter = useContext(FinancialViewContext);
  const [aberto, setAberto] = useState(automatico);
  const [expandido, setExpandido] = useState(false);
  const ampliar = () => {
    setAberto(true);
    setExpandido(true);
  };
  const [dados, setDados] = useState<GoalTimeline | null>(null);
  const [erro, setErro] = useState("");
  const [tabela, setTabela] = useState(false);
  const [tentativa, setTentativa] = useState(0);
  useEffect(() => {
    if (!aberto) return;
    const controller = new AbortController();
    let ativo = true;
    obterAcompanhamento(
      meta.id,
      controller.signal,
      viewFilter ?? undefined,
      meta,
    )
      .then((d) => {
        if (ativo) {
          setDados(d);
          setErro("");
        }
      })
      .catch((e) => {
        if (ativo && !controller.signal.aborted)
          setErro(
            e instanceof Error ? e.message : "Falha ao carregar evolução.",
          );
      });
    return () => {
      ativo = false;
      controller.abort();
    };
  }, [aberto, meta, viewFilter, tentativa]);
  const uso = Number(meta.percentualUtilizado) || 0,
    alerta = Number(meta.percentualAlerta) || 0;
  const ritmo = dados?.ritmoIdealPercentual ?? ritmoMeta(meta);
  const estimativa = dados?.estimativaFim;
  const projecao =
    estimativa != null && meta.valorLimite > 0
      ? (estimativa / meta.valorLimite) * 100
      : null;
  const risco =
    meta.status === "EXCEDIDA"
      ? "⚠ Limite ultrapassado"
      : meta.status === "ALERTA"
        ? "⚠ Faixa de alerta"
        : "✓ Dentro do limite";
  return (
    <div className={`goal-progress-new gp-state-${meta.status.toLowerCase()}`}>
      {resumo && (
        <div className="gp-heading">
          <strong>{periodoMeta(meta)}</strong>
          <span>
            {dataBR(meta.dataInicio)} — {dataBR(meta.dataFim)}
          </span>
        </div>
      )}
      <p className={`gp-risk gp-risk-${meta.status.toLowerCase()}`}>
        {meta.situacaoTemporal === "FUTURA"
          ? "Planejamento futuro · "
          : meta.situacaoTemporal === "ENCERRADA"
            ? viewFilter
              ? "Resultado no recorte · "
              : "Resultado final · "
            : ""}
        {risco}
      </p>
      {resumo && (
        <div className="gp-stats">
          <div>
            Limite<strong>{moedaMeta(meta.valorLimite)}</strong>
          </div>
          <div>
            Registrado na meta<strong>{moedaMeta(meta.gastoAtual)}</strong>
          </div>
          <div>
            Disponível<strong>{moedaMeta(meta.saldoMeta)}</strong>
          </div>
        </div>
      )}
      <GoalUsageGauge
        meta={meta}
        ritmo={meta.situacaoTemporal === "EM_ANDAMENTO" ? ritmo : null}
        projecao={projecao}
        aoAmpliar={ampliar}
      />
      <div className="gp-legend">
        <span>Alerta: {alerta}%</span>
        <span>Limite: 100%</span>
        {meta.situacaoTemporal === "EM_ANDAMENTO" && (
          <span>Ritmo ideal hoje: {percentual(ritmo)}%</span>
        )}
        {projecao != null && (
          <span>Estimativa final: {percentual(projecao)}%</span>
        )}
      </div>
      <div
        className={`gp-bottom-actions ${uso > 100 ? "gp-excess-banner" : ""}`}
      >
        {uso > 100 && (
          <strong className="gp-risk-excedida">
            Excedente de{" "}
            {moedaMeta(Math.max(0, meta.gastoAtual - meta.valorLimite))}
          </strong>
        )}
        <button
          type="button"
          onClick={() => (evolucaoEmDialogo ? ampliar() : setAberto(!aberto))}
          aria-haspopup={evolucaoEmDialogo ? "dialog" : undefined}
          aria-expanded={evolucaoEmDialogo ? undefined : aberto}
        >
          {!evolucaoEmDialogo && aberto
            ? "Ocultar evolução diária"
            : "Ver evolução diária"}
        </button>
      </div>
      {aberto && !evolucaoEmDialogo && (
        <div className="gp-evolution">
          {erro ? (
            <p role="alert">
              {erro}{" "}
              <button type="button" onClick={() => setTentativa(tentativa + 1)}>
                Tentar novamente
              </button>
            </p>
          ) : !dados ? (
            <p role="status">Carregando evolução…</p>
          ) : (
            <>
              <GoalEvolution
                dados={dados}
                tabela={tabela}
                aoAlternarTabela={() => setTabela((v) => !v)}
                aoAmpliar={ampliar}
              />
            </>
          )}
        </div>
      )}
      {expandido && (
        <GoalDetailDialog
          meta={meta}
          aoFechar={() => setExpandido(false)}
          rotuloFechar={
            evolucaoEmDialogo ? "Fechar e voltar ao painel" : undefined
          }
        >
          <div className="goal-detail-summary">
            <GoalUsageGauge
              meta={meta}
              ritmo={meta.situacaoTemporal === "EM_ANDAMENTO" ? ritmo : null}
              projecao={projecao}
            />
            <div>
              <p className={`gp-risk gp-risk-${meta.status.toLowerCase()}`}>
                {risco}
              </p>
              <div className="gp-stats">
                <div>
                  Limite<strong>{moedaMeta(meta.valorLimite)}</strong>
                </div>
                <div>
                  Utilizado total<strong>{moedaMeta(meta.gastoAtual)}</strong>
                </div>
                <div>
                  Saldo total<strong>{moedaMeta(meta.saldoMeta)}</strong>
                </div>
              </div>
              <p>
                Alerta: {alerta}% · Limite: 100%. O medidor considera o consumo
                total da meta, sem alteração pelos filtros.
              </p>
              {uso > 100 && (
                <p className="gp-risk-excedida">
                  Excedente de{" "}
                  {moedaMeta(Math.max(0, meta.gastoAtual - meta.valorLimite))}
                </p>
              )}
            </div>
          </div>
          <div className="gp-evolution">
            {erro ? (
              <p role="alert">
                {erro}{" "}
                <button
                  type="button"
                  onClick={() => setTentativa((v) => v + 1)}
                >
                  Tentar novamente
                </button>
              </p>
            ) : !dados ? (
              <p role="status">Carregando evolução…</p>
            ) : (
              <GoalEvolution
                dados={dados}
                tabela={tabela}
                aoAlternarTabela={() => setTabela((v) => !v)}
              />
            )}
          </div>
        </GoalDetailDialog>
      )}
    </div>
  );
}
