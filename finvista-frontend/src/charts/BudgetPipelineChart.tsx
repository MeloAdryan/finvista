import { useId, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  LabelList,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";

import type { BudgetPipelineData } from "../services/budgetPipelineService";
import {
  moedaOrcamento,
  dataOrcamento,
  percentualOrcamento,
  exportarOrcamentos,
} from "../services/budgetAnalysis";

import "../styles/budget-analysis.css";
import "../styles/budget-column-chart.css";

interface BudgetPipelineChartProps {
  dados: BudgetPipelineData[];
}

function valoresEixo(dados: BudgetPipelineData[]) {
  const valores = dados
    .flatMap((item) => [
      Number(item.valorPlanejado),
      Number(item.valorUtilizado),
    ])
    .filter(Number.isFinite);
  const minimo = Math.min(0, ...valores);
  const maximo = Math.max(0, ...valores);
  const amplitude = (maximo - minimo || 100) * 1.15;
  const bruto = amplitude / 5;
  const potencia = 10 ** Math.floor(Math.log10(bruto));
  const proporcao = bruto / potencia;
  const multiplicador =
    proporcao <= 1
      ? 1
      : proporcao <= 2
        ? 2
        : proporcao <= 2.5
          ? 2.5
          : proporcao <= 5
            ? 5
            : 10;
  const passo = multiplicador * potencia;
  const inferior = Math.floor((minimo * 1.1) / passo) * passo;
  const superior = Math.ceil(Math.max(maximo * 1.15, passo) / passo) * passo;
  const ticks = Array.from(
    { length: Math.round((superior - inferior) / passo) + 1 },
    (_, indice) => Number((inferior + indice * passo).toPrecision(12)),
  );
  return { domain: [inferior, superior] as [number, number], ticks };
}

const compacto = (valor: number) =>
  Number(valor).toLocaleString("pt-BR", {
    notation: "compact",
    maximumFractionDigits: 1,
  });

function rotuloValor(valor: unknown) {
  const numero = Number(valor);
  if (!Number.isFinite(numero)) return "";
  return numero === 0 ? "R$ 0" : `R$ ${compacto(numero)}`;
}

function NomeOrcamento({
  x = 0,
  y = 0,
  payload,
}: {
  x?: number;
  y?: number;
  payload?: { value: string };
}) {
  const nome = String(payload?.value ?? "");
  const linhas: string[] = [];
  for (const palavra of nome.split(" ")) {
    const ultima = linhas.length - 1;
    if (ultima >= 0 && `${linhas[ultima]} ${palavra}`.length <= 23) {
      linhas[ultima] += ` ${palavra}`;
    } else {
      linhas.push(palavra);
    }
  }
  const exibidas = linhas.slice(0, 3);
  if (linhas.length > 3) exibidas[2] += "…";
  return (
    <g transform={`translate(${x},${y})`}>
      <title>{nome}</title>
      <text textAnchor="middle" fill="#536c86" fontSize={11}>
        {exibidas.map((linha, indice) => (
          <tspan key={indice} x={0} dy={indice === 0 ? 18 : 15}>
            {linha}
          </tspan>
        ))}
      </text>
    </g>
  );
}

function DetalheOrcamento({
  active,
  payload,
}: {
  active?: boolean;
  payload?: readonly { payload?: BudgetPipelineData }[];
}) {
  const item = payload?.find((entrada) => entrada.payload)?.payload;
  if (!active || !item) return null;
  const excedente = Math.max(0, item.valorUtilizado - item.valorPlanejado);
  return (
    <div className="bc-tooltip">
      <strong>{item.nome}</strong>
      <small>
        {dataOrcamento(item.dataInicio)} — {dataOrcamento(item.dataFim)}
      </small>
      <dl>
        <div>
          <dt>Planejado</dt>
          <dd>{moedaOrcamento(item.valorPlanejado)}</dd>
        </div>
        <div>
          <dt>Utilizado</dt>
          <dd>{moedaOrcamento(item.valorUtilizado)}</dd>
        </div>
        <div>
          <dt>Disponível</dt>
          <dd>{moedaOrcamento(item.valorDisponivel)}</dd>
        </div>
        <div>
          <dt>Utilização</dt>
          <dd>{percentualOrcamento(item.percentualUtilizado)}%</dd>
        </div>
        <div>
          <dt>Excedente</dt>
          <dd>{moedaOrcamento(excedente)}</dd>
        </div>
      </dl>
      <small>
        {item.centroCusto || "Todos os centros"} ·{" "}
        {item.categoria || "Todas as categorias"}
      </small>
    </div>
  );
}

export default function BudgetPipelineChart({
  dados,
}: BudgetPipelineChartProps) {
  const [tabela, setTabela] = useState(false);
  const id = useId().replace(/:/g, "");
  const planejadoId = `budget-planejado-${id}`;
  const utilizadoId = `budget-utilizado-${id}`;
  const sombraId = `budget-sombra-${id}`;
  const eixo = valoresEixo(dados);
  const totalPlanejado = dados.reduce(
    (soma, item) => soma + Number(item.valorPlanejado),
    0,
  );
  const totalUtilizado = dados.reduce(
    (soma, item) => soma + Number(item.valorUtilizado),
    0,
  );
  const maior = dados.reduce<BudgetPipelineData | null>(
    (atual, item) =>
      atual === null ||
      Number(item.percentualUtilizado) > Number(atual.percentualUtilizado)
        ? item
        : atual,
    null,
  );
  const semConsumo = dados.filter((item) => Number(item.valorUtilizado) === 0);

  return (
    <section
      className="ba-comparison bc-panel"
      aria-labelledby={`bc-title-${id}`}
    >
      <div className="ba-heading">
        <div>
          <span className="ba-eyebrow">Controle orçamentário</span>
          <h2 id={`bc-title-${id}`}>Planejado × utilizado</h2>
          <p>
            Compare os valores em reais no período completo de cada orçamento.
          </p>
        </div>
        <div className="ba-actions">
          <button
            type="button"
            onClick={() => setTabela((atual) => !atual)}
            aria-pressed={tabela}
          >
            {tabela ? "Ver gráfico" : "Ver como tabela"}
          </button>
          <button
            type="button"
            onClick={() => exportarOrcamentos(dados)}
            disabled={!dados.length}
          >
            Exportar CSV
          </button>
        </div>
      </div>

      {!dados.length ? (
        <p className="ba-empty">
          Nenhum orçamento corresponde ao período selecionado.
        </p>
      ) : (
        <>
          <div className="bc-summary">
            <div>
              <span>Total planejado</span>
              <strong>{moedaOrcamento(totalPlanejado)}</strong>
            </div>
            <div>
              <span>Total utilizado</span>
              <strong>{moedaOrcamento(totalUtilizado)}</strong>
            </div>
            <div>
              <span>Disponível somado</span>
              <strong>{moedaOrcamento(totalPlanejado - totalUtilizado)}</strong>
            </div>
          </div>

          {tabela ? (
            <div className="ba-table-scroll">
              <table className="ba-table">
                <caption>Valores exatos dos orçamentos exibidos</caption>
                <thead>
                  <tr>
                    <th>Orçamento / critérios</th>
                    <th>Período</th>
                    <th>Planejado</th>
                    <th>Utilizado</th>
                    <th>Disponível</th>
                    <th>Utilização</th>
                    <th>Excedente</th>
                  </tr>
                </thead>
                <tbody>
                  {dados.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <strong>{item.nome}</strong>
                        <small>
                          {item.centroCusto || "Todos os centros"} ·{" "}
                          {item.categoria || "Todas as categorias"}
                        </small>
                      </td>
                      <td>
                        {dataOrcamento(item.dataInicio)} —{" "}
                        {dataOrcamento(item.dataFim)}
                      </td>
                      <td>{moedaOrcamento(item.valorPlanejado)}</td>
                      <td>{moedaOrcamento(item.valorUtilizado)}</td>
                      <td>{moedaOrcamento(item.valorDisponivel)}</td>
                      <td>{percentualOrcamento(item.percentualUtilizado)}%</td>
                      <td>
                        {moedaOrcamento(
                          Math.max(
                            0,
                            item.valorUtilizado - item.valorPlanejado,
                          ),
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="bc-plot-card">
              <div className="bc-plot-heading">
                <div className="bc-legend">
                  <span>
                    <i className="bc-planned-dot" />
                    Planejado
                  </span>
                  <span>
                    <i className="bc-used-dot" />
                    Utilizado
                  </span>
                </div>
                {maior && (
                  <div className="bc-highlight">
                    <span>Maior utilização</span>
                    <strong>{maior.nome}</strong>
                    <small>
                      {percentualOrcamento(maior.percentualUtilizado)}% do
                      limite
                    </small>
                  </div>
                )}
              </div>
              <div
                className="bc-scroll"
                tabIndex={0}
                aria-label="Gráfico de colunas dos orçamentos; role horizontalmente quando necessário"
              >
                <div
                  className="bc-chart"
                  style={{ minWidth: Math.max(300, dados.length * 190) }}
                >
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={dados}
                      barGap={12}
                      barCategoryGap="24%"
                      margin={{ top: 35, right: 24, left: 4, bottom: 5 }}
                    >
                      <defs>
                        <linearGradient
                          id={planejadoId}
                          x1="0"
                          y1="0"
                          x2="1"
                          y2="0"
                        >
                          <stop offset="0%" stopColor="#153856" />
                          <stop offset="45%" stopColor="#315879" />
                          <stop offset="100%" stopColor="#183b5b" />
                        </linearGradient>
                        <linearGradient
                          id={utilizadoId}
                          x1="0"
                          y1="0"
                          x2="1"
                          y2="0"
                        >
                          <stop offset="0%" stopColor="#ac6426" />
                          <stop offset="45%" stopColor="#df9954" />
                          <stop offset="100%" stopColor="#bc742e" />
                        </linearGradient>
                        <filter
                          id={sombraId}
                          x="-30%"
                          y="-20%"
                          width="160%"
                          height="160%"
                        >
                          <feDropShadow
                            dx="2"
                            dy="4"
                            stdDeviation="3"
                            floodColor="#183b5b"
                            floodOpacity="0.16"
                          />
                        </filter>
                      </defs>
                      <CartesianGrid vertical={false} stroke="#e1e8f0" />
                      <XAxis
                        dataKey="nome"
                        interval={0}
                        height={70}
                        tick={<NomeOrcamento />}
                        tickLine={false}
                        axisLine={{ stroke: "#b7c6d5" }}
                      />
                      <YAxis
                        width={65}
                        domain={eixo.domain}
                        ticks={eixo.ticks}
                        tickFormatter={compacto}
                        tick={{ fill: "#536c86", fontSize: 11 }}
                        tickLine={false}
                        axisLine={false}
                      />
                      <ReferenceLine y={0} stroke="#a5b6c8" />
                      <Tooltip
                        content={<DetalheOrcamento />}
                        cursor={{ fill: "#e8eff7", fillOpacity: 0.6 }}
                        wrapperStyle={{ zIndex: 20 }}
                        allowEscapeViewBox={{ x: true, y: true }}
                      />
                      <Bar
                        dataKey="valorPlanejado"
                        name="Planejado"
                        fill={`url(#${planejadoId})`}
                        filter={`url(#${sombraId})`}
                        maxBarSize={64}
                        radius={[7, 7, 0, 0]}
                      >
                        <LabelList
                          dataKey="valorPlanejado"
                          position="top"
                          formatter={rotuloValor}
                          fill="#153856"
                          fontSize={11}
                          offset={10}
                        />
                      </Bar>
                      <Bar
                        dataKey="valorUtilizado"
                        name="Utilizado"
                        fill={`url(#${utilizadoId})`}
                        filter={`url(#${sombraId})`}
                        maxBarSize={64}
                        radius={[7, 7, 0, 0]}
                      >
                        <LabelList
                          dataKey="valorUtilizado"
                          position="top"
                          formatter={rotuloValor}
                          fill="#995820"
                          fontSize={11}
                          offset={10}
                        />
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              </div>
              <p className="bc-hint">
                Passe o cursor sobre as colunas para consultar valores e
                critérios. No celular, role o gráfico quando houver vários
                orçamentos.
              </p>
            </div>
          )}

          {semConsumo.length > 0 && (
            <div className="bc-zero">
              <strong>Consumo registrado igual a zero</strong>
              <p>
                {semConsumo.map((item) => item.nome).join(" · ")}. A coluna
                utilizada permanece em zero; confira o período e os critérios
                cadastrados.
              </p>
            </div>
          )}
          <div className="bc-results">
            {dados.map((item) => {
              const excedente = Math.max(
                0,
                item.valorUtilizado - item.valorPlanejado,
              );
              return (
                <div
                  key={item.id}
                  className={excedente > 0 ? "bc-result-exceeded" : ""}
                >
                  <strong>{item.nome}</strong>
                  <span>
                    {dataOrcamento(item.dataInicio)} —{" "}
                    {dataOrcamento(item.dataFim)}
                  </span>
                  <b>
                    {percentualOrcamento(item.percentualUtilizado)}% utilizado
                  </b>
                  <span>
                    {excedente > 0
                      ? `Excedente de ${moedaOrcamento(excedente)}`
                      : `Disponível: ${moedaOrcamento(item.valorDisponivel)}`}
                  </span>
                </div>
              );
            })}
          </div>
          <p className="ba-note">
            Valores contabilizados pela data de análise, incluindo lançamentos
            em aberto. Cores identificam planejado e utilizado; o sombreamento é
            igual para todas as colunas. Consulte a utilização individual quando
            os orçamentos tiverem critérios ou períodos diferentes.
          </p>
        </>
      )}
    </section>
  );
}
