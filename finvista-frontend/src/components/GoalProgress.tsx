import { useEffect, useState } from "react";
import {
  CartesianGrid,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { SpendingGoal } from "../services/spendingGoalService";
import {
  exportarMeta,
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
}: {
  meta: SpendingGoal;
  automatico?: boolean;
  resumo?: boolean;
}) {
  const [aberto, setAberto] = useState(automatico);
  const [dados, setDados] = useState<GoalTimeline | null>(null);
  const [erro, setErro] = useState("");
  const [tabela, setTabela] = useState(false);
  const [tentativa, setTentativa] = useState(0);
  useEffect(() => {
    if (!aberto) return;
    const controller = new AbortController();
    let ativo = true;
    obterAcompanhamento(meta.id, controller.signal)
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
  }, [aberto, meta.id, meta.gastoAtual, meta.dataReferencia, tentativa]);
  const uso = Number(meta.percentualUtilizado) || 0,
    alerta = Number(meta.percentualAlerta) || 0;
  const ritmo = dados?.ritmoIdealPercentual ?? ritmoMeta(meta);
  const estimativa = dados?.estimativaFim;
  const projecao =
    estimativa != null && meta.valorLimite > 0
      ? (estimativa / meta.valorLimite) * 100
      : null;
  const escala = Math.max(100, uso, projecao ?? 0);
  const posicao = (v: number) =>
    `${Math.max(0, Math.min(100, (v / escala) * 100))}%`;
  const risco =
    meta.status === "EXCEDIDA"
      ? "⚠ Limite ultrapassado"
      : meta.status === "ALERTA"
        ? "⚠ Faixa de alerta"
        : "✓ Dentro do limite";
  return (
    <div className="goal-progress-new">
      <div className="gp-heading">
        <strong>{periodoMeta(meta)}</strong>
        <span>
          {dataBR(meta.dataInicio)} — {dataBR(meta.dataFim)}
        </span>
      </div>
      <p className={`gp-risk gp-risk-${meta.status.toLowerCase()}`}>
        {meta.situacaoTemporal === "FUTURA"
          ? "Planejamento futuro · "
          : meta.situacaoTemporal === "ENCERRADA"
            ? "Resultado final · "
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
      <div className="gp-heading">
        <span>Registrado no período completo da meta</span>
        <strong>{percentual(uso)}%</strong>
      </div>
      <div
        className="gp-track"
        role="img"
        aria-label={`Registrado ${percentual(uso)} por cento; alerta ${alerta} por cento; ritmo ideal ${percentual(ritmo)} por cento`}
      >
        <div
          className="gp-fill"
          style={{ width: posicao(Math.min(uso, 100)) }}
        />
        {uso > 100 && (
          <div
            className="gp-over"
            style={{ left: posicao(100), width: posicao(uso - 100) }}
          />
        )}
        <i
          className="gp-mark gp-alert"
          style={{ left: posicao(alerta) }}
          title={`Alerta ${alerta}%`}
        />
        <i
          className="gp-mark gp-limit"
          style={{ left: posicao(100) }}
          title="Limite 100%"
        />
        {meta.situacaoTemporal === "EM_ANDAMENTO" && (
          <i
            className="gp-mark gp-rhythm"
            style={{ left: posicao(ritmo) }}
            title={`Ritmo ideal hoje ${percentual(ritmo)}%`}
          />
        )}
        {projecao != null && (
          <i
            className="gp-mark gp-estimate"
            style={{ left: posicao(projecao) }}
            title={`Estimativa final ${percentual(projecao)}%`}
          />
        )}
      </div>
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
      {uso > 100 && (
        <strong className="gp-risk-excedida">
          Excedente de{" "}
          {moedaMeta(Math.max(0, meta.gastoAtual - meta.valorLimite))}
        </strong>
      )}
      <button
        type="button"
        onClick={() => setAberto(!aberto)}
        aria-expanded={aberto}
      >
        {aberto ? "Ocultar evolução diária" : "Ver evolução diária"}
      </button>
      {aberto && (
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
              <div className="gp-stats">
                <div>
                  Registrado até {dataBR(dados.hoje)}
                  <strong>{moedaMeta(dados.registradoAteHoje)}</strong>
                </div>
                <div>
                  Registrado depois de hoje
                  <strong>{moedaMeta(dados.registradoDepoisHoje)}</strong>
                </div>
                {estimativa != null && (
                  <div>
                    Estimativa ao fim<strong>{moedaMeta(estimativa)}</strong>
                  </div>
                )}
              </div>
              <p>
                Valores contabilizados pela data de análise, incluindo
                lançamentos em aberto. A estimativa linear usa o ritmo até hoje;
                não soma novamente os registros futuros e não representa
                previsão de pagamento.
              </p>
              <div className="gp-actions">
                <button type="button" onClick={() => setTabela(!tabela)}>
                  {tabela ? "Ver gráfico" : "Ver como tabela"}
                </button>
                <button type="button" onClick={() => exportarMeta(dados)}>
                  CSV
                </button>
              </div>
              {tabela ? (
                <div className="gp-table">
                  <table>
                    <caption>Consumo diário da meta</caption>
                    <thead>
                      <tr>
                        <th>Data</th>
                        <th>Acumulado registrado</th>
                        <th>Ritmo ideal</th>
                        <th>Estimativa</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dados.pontos.map((p) => (
                        <tr key={p.data}>
                          <td>{dataBR(p.data)}</td>
                          <td>
                            {p.acumuladoRegistrado == null
                              ? "—"
                              : moedaMeta(p.acumuladoRegistrado)}
                          </td>
                          <td>{moedaMeta(p.ritmoIdeal)}</td>
                          <td>
                            {p.estimativa == null
                              ? "—"
                              : moedaMeta(p.estimativa)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="gp-chart">
                  <ResponsiveContainer width="100%" height="100%">
                    <LineChart
                      data={dados.pontos}
                      margin={{ top: 15, right: 16, left: 4, bottom: 5 }}
                    >
                      <CartesianGrid stroke="#e4eaf1" />
                      <XAxis
                        dataKey="data"
                        tickFormatter={(s) =>
                          String(s).slice(8) + "/" + String(s).slice(5, 7)
                        }
                        minTickGap={35}
                      />
                      <YAxis
                        width={65}
                        domain={["auto", "auto"]}
                        tickFormatter={(v) =>
                          Number(v).toLocaleString("pt-BR", {
                            notation: "compact",
                          })
                        }
                      />
                      <Tooltip
                        labelFormatter={(s) => dataBR(String(s))}
                        formatter={(v) => moedaMeta(Number(v))}
                      />
                      <ReferenceLine y={0} stroke="#94a3b8" />
                      <ReferenceLine
                        y={dados.limite}
                        stroke="#b45309"
                        label="Limite"
                      />
                      <Line
                        type="linear"
                        dataKey="acumuladoRegistrado"
                        name="Registrado"
                        stroke="#153856"
                        dot={false}
                        strokeWidth={2}
                      />
                      <Line
                        type="linear"
                        dataKey="ritmoIdeal"
                        name="Ritmo ideal"
                        stroke="#94a3b8"
                        dot={false}
                      />
                      <Line
                        type="linear"
                        dataKey="estimativa"
                        name="Estimativa"
                        stroke="#2563eb"
                        strokeDasharray="5 4"
                        dot={false}
                      />
                    </LineChart>
                  </ResponsiveContainer>
                </div>
              )}
              <div className="gp-legend">
                <span>Azul escuro: registrado</span>
                <span>Cinza: ritmo ideal</span>
                <span>Azul tracejado: estimativa</span>
                <span>Âmbar: limite</span>
              </div>
              <details>
                <summary>Consumo por categoria</summary>
                <ul>
                  {dados.categorias.map((c) => (
                    <li key={c.categoria}>
                      <span>{c.categoria}</span>
                      <strong>
                        {moedaMeta(c.valor)}
                        {dados.totalRegistrado > 0
                          ? ` · ${percentual((c.valor / dados.totalRegistrado) * 100)}%`
                          : ""}
                      </strong>
                    </li>
                  ))}
                </ul>
              </details>
            </>
          )}
        </div>
      )}
    </div>
  );
}
