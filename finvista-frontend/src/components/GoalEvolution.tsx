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
import {
  exportarMeta,
  type GoalTimeline,
} from "../services/spendingGoalTimelineService";
import { moedaMeta, percentual } from "../services/goalPresentation";
const dataBR = (s: string) => s.split("-").reverse().join("/");
interface Props {
  dados: GoalTimeline;
  tabela: boolean;
  aoAlternarTabela: () => void;
  aoAmpliar?: () => void;
}
export default function GoalEvolution({
  dados,
  tabela,
  aoAlternarTabela,
  aoAmpliar,
}: Props) {
  const estimativa = dados.estimativaFim;
  const [reduzirMovimento, setReduzirMovimento] = useState(
    () => window.matchMedia("(prefers-reduced-motion: reduce)").matches,
  );
  useEffect(() => {
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const change = () => setReduzirMovimento(media.matches);
    media.addEventListener("change", change);
    return () => media.removeEventListener("change", change);
  }, []);
  return (
    <>
      <h4 className="gp-evolution-title">Evolução diária</h4>
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
        Valores contabilizados pela data de análise, incluindo lançamentos em
        aberto. A estimativa linear usa o ritmo até hoje; não soma novamente os
        registros futuros e não representa previsão de pagamento.
      </p>
      <div className="gp-actions">
        {aoAmpliar && (
          <button type="button" aria-haspopup="dialog" onClick={aoAmpliar}>
            Ampliar evolução
          </button>
        )}
        <button type="button" onClick={aoAlternarTabela}>
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
                    {p.estimativa == null ? "—" : moedaMeta(p.estimativa)}
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
              onClick={aoAmpliar}
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
              <ReferenceLine y={dados.limite} stroke="#b45309" label="Limite" />
              <Line
                isAnimationActive={!reduzirMovimento}
                type="linear"
                dataKey="acumuladoRegistrado"
                name="Registrado"
                stroke="#153856"
                dot={false}
                strokeWidth={2}
              />
              <Line
                isAnimationActive={!reduzirMovimento}
                type="linear"
                dataKey="ritmoIdeal"
                name="Ritmo ideal"
                stroke="#94a3b8"
                dot={false}
              />
              <Line
                isAnimationActive={!reduzirMovimento}
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
  );
}
