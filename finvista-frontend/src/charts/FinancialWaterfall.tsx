import { useId, useState } from "react";
import type { DashboardAnalysis } from "../services/dashboardAnalysisService";
import { prepararCascata } from "../services/analysisChartData";
import {
  eixoFinanceiro,
  exportarFinanceiro,
  moedaFinanceira,
  compactoFinanceiro,
} from "../services/financialVisualization";
import "../styles/financial-waterfall.css";
import { descreverRegistros } from "../services/comparisonCoverage";
export default function FinancialWaterfall({
  analise,
  onCategoria,
}: {
  analise: DashboardAnalysis;
  onCategoria: (nome: string) => void;
}) {
  const [todas, setTodas] = useState(false);
  const id = useId();
  const anterior = analise.indicadores.despesaMesAnterior ?? 0,
    atual = analise.indicadores.despesa;
  const cascata = prepararCascata(anterior, atual, analise.variacoes, todas);
  const barras = cascata.barras.map((b) => ({
    ...b,
    texto: `${!b.total && b.valor > 0 ? "+" : ""}${moedaFinanceira(b.valor)}`,
  }));
  const eixo = eixoFinanceiro(barras.flatMap((b) => b.intervalo));
  const data = (s: string) => s.split("-").reverse().join("/");
  const diferenca =
    (Math.round(atual * 100) - Math.round(anterior * 100)) / 100;
  const percentual = anterior > 0 ? (diferenca / anterior) * 100 : null;
  const numero = (n: number) =>
    Math.abs(n).toLocaleString("pt-BR", { maximumFractionDigits: 2 });
  const titulo =
    diferenca === 0
      ? "O que mudou: despesas sem variação"
      : `O que mudou: despesas ${diferenca < 0 ? "caíram" : "aumentaram"} ${moedaFinanceira(Math.abs(diferenca))}${percentual === null ? "" : ` (${diferenca < 0 ? "−" : "+"}${numero(percentual)}%)`}`;
  const rotuloPeriodo = (inicio: string, fim: string) => {
    const d = new Date(`${inicio}T12:00:00`);
    const f = new Date(`${fim}T12:00:00`);
    const ultimoDia = new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate();
    if (
      d.getDate() === 1 &&
      f.getFullYear() === d.getFullYear() &&
      f.getMonth() === d.getMonth() &&
      f.getDate() === ultimoDia
    )
      return d.toLocaleDateString("pt-BR", { month: "long", year: "numeric" });
    return `${data(inicio)} — ${data(fim)}`;
  };
  const largura = Math.max(900, barras.length * 132 + 100);
  const altura = 480;
  const margem = { esquerda: 92, direita: 24, topo: 64, fundo: 104 };
  const faixa = (largura - margem.esquerda - margem.direita) / barras.length;
  const barraLargura = Math.min(76, faixa * 0.56);
  const [minimo, maximo] = eixo.domain;
  const y = (valor: number) =>
    margem.topo +
    ((maximo - valor) / (maximo - minimo || 1)) *
      (altura - margem.topo - margem.fundo);
  const x = (indice: number) => margem.esquerda + faixa * (indice + 0.5);
  const escolher = (categoria: string) => {
    if (categoria)
      onCategoria(analise.categoria === categoria ? "" : categoria);
  };
  const linhasNome = (nome: string) => {
    const palavras = nome.split(/\s+/);
    const linhas: string[] = [];
    let linha = "";
    for (const palavra of palavras) {
      if (linha && `${linha} ${palavra}`.length > 19) {
        linhas.push(linha);
        linha = palavra;
      } else linha = linha ? `${linha} ${palavra}` : palavra;
    }
    if (linha) linhas.push(linha);
    return linhas.length > 3
      ? [...linhas.slice(0, 2), `${linhas[2].slice(0, 17)}…`]
      : linhas;
  };
  return (
    <section
      className="executive-panel wf-panel"
      aria-labelledby={`wf-title-${id}`}
    >
      <p className="ax-note">
        {descreverRegistros(
          analise.registros?.despesasAtual,
          analise.registros?.despesasAnterior,
        )}{" "}
        As diferenças refletem os registros disponíveis; não confirmam
        importação completa.
      </p>
      <div className="wf-heading">
        <div>
          <h2 id={`wf-title-${id}`}>{titulo}</h2>
          <p>
            {data(analise.inicioAnterior)} — {data(analise.fimAnterior)} →{" "}
            {data(analise.inicio)} — {data(analise.fim)} ·{" "}
            {analise.categoria || "Todas as categorias"}
          </p>
          <p className="wf-legend">
            <span>● Totais</span>
            <span>▼ Despesa diminuiu</span>
            <span>▲ Despesa aumentou</span>
          </p>
        </div>
        <div className="wf-actions">
          <button type="button" onClick={() => setTodas(!todas)}>
            {todas ? "Agrupar menores mudanças" : "Abrir todas as mudanças"}
          </button>
          <button
            type="button"
            onClick={() =>
              exportarFinanceiro("variacoes-despesas.csv", [
                ["Categoria", "Anterior", "Atual", "Diferença"],
                ...analise.variacoes.map((v) => [
                  v.categoria,
                  v.anterior,
                  v.atual,
                  v.diferenca,
                ]),
              ])
            }
          >
            CSV
          </button>
          {analise.categoria && (
            <button type="button" onClick={() => onCategoria("")}>
              Limpar categoria
            </button>
          )}
        </div>
      </div>
      {!cascata.conciliado ? (
        <p role="alert">
          Os dados da comparação não conciliam com o total. Confira o
          detalhamento antes de usar a cascata.
        </p>
      ) : (
        <div
          className="wf-scroll"
          tabIndex={0}
          aria-label="Cascata de despesas, com rolagem horizontal"
        >
          <svg
            className="wf-chart"
            width={largura}
            height={altura}
            style={{ minWidth: largura }}
            viewBox={`0 0 ${largura} ${altura}`}
            aria-labelledby={`wf-title-${id} wf-desc-${id}`}
          >
            <desc id={`wf-desc-${id}`}>
              De {moedaFinanceira(anterior)} para {moedaFinanceira(atual)}.
              Categorias ordenadas por variação absoluta. Valores completos
              disponíveis na tabela abaixo.
            </desc>
            {eixo.ticks.map((tick) => (
              <g key={tick} className="wf-grid">
                <line
                  x1={margem.esquerda}
                  x2={largura - margem.direita}
                  y1={y(tick)}
                  y2={y(tick)}
                />
                <text x={margem.esquerda - 12} y={y(tick) + 4} textAnchor="end">
                  {compactoFinanceiro(tick)}
                </text>
              </g>
            ))}
            <line
              className="wf-zero"
              x1={margem.esquerda}
              x2={largura - margem.direita}
              y1={y(0)}
              y2={y(0)}
            />
            {barras.slice(0, -1).map((_, i) => {
              const saldo =
                i === 0
                  ? anterior
                  : anterior +
                    barras
                      .slice(1, i + 1)
                      .reduce((s, item) => s + item.delta, 0);
              return (
                <line
                  key={i}
                  className="wf-connector"
                  x1={x(i) + barraLargura / 2}
                  x2={x(i + 1) - barraLargura / 2}
                  y1={y(saldo)}
                  y2={y(saldo)}
                />
              );
            })}
            {barras.map((b, i) => {
              const cor = b.total
                ? "#2e75d3"
                : b.delta < 0
                  ? "#16803c"
                  : b.delta > 0
                    ? "#ce3540"
                    : "#64748b";
              const nome =
                i === 0
                  ? rotuloPeriodo(analise.inicioAnterior, analise.fimAnterior)
                  : i === barras.length - 1
                    ? rotuloPeriodo(analise.inicio, analise.fim)
                    : b.nome;
              const texto = b.total
                ? moedaFinanceira(b.valor)
                : `${b.delta < 0 ? "▼" : b.delta > 0 ? "▲" : "="} ${moedaFinanceira(Math.abs(b.valor))}`;
              const topo = y(b.intervalo[1]);
              const base = y(b.intervalo[0]);
              const selecionada = Boolean(
                b.categoria && analise.categoria === b.categoria,
              );
              return (
                <g
                  key={`${b.nome}-${i}`}
                  className={b.categoria ? "wf-category" : undefined}
                  role={b.categoria ? "button" : undefined}
                  tabIndex={b.categoria ? 0 : undefined}
                  aria-label={`${nome}: ${texto}${b.categoria ? ". Filtrar painel por esta categoria" : ""}`}
                  aria-pressed={b.categoria ? selecionada : undefined}
                  onClick={() => escolher(b.categoria)}
                  onKeyDown={(event) => {
                    if (
                      b.categoria &&
                      (event.key === "Enter" || event.key === " ")
                    ) {
                      event.preventDefault();
                      escolher(b.categoria);
                    }
                  }}
                >
                  <title>
                    {nome}: {texto}
                  </title>
                  <rect
                    className="wf-hit"
                    x={x(i) - faixa / 2 + 2}
                    y={margem.topo - 35}
                    width={faixa - 4}
                    height={altura - margem.topo + 25}
                    rx={6}
                  />
                  <rect
                    className="wf-bar"
                    x={x(i) - barraLargura / 2}
                    y={topo}
                    width={barraLargura}
                    height={Math.max(1.5, base - topo)}
                    fill={cor}
                  />
                  <text
                    className="wf-value"
                    x={x(i)}
                    y={topo - 12}
                    textAnchor="middle"
                    fill={b.total ? "#18334d" : cor}
                  >
                    {texto}
                  </text>
                  <text
                    className="wf-category-name"
                    x={x(i)}
                    y={altura - margem.fundo + 25}
                    textAnchor="middle"
                  >
                    {linhasNome(nome).map((linha, indice) => (
                      <tspan key={indice} x={x(i)} dy={indice ? 17 : 0}>
                        {linha}
                      </tspan>
                    ))}
                  </text>
                </g>
              );
            })}
          </svg>
        </div>
      )}
      <p className="wf-note">
        Azul: totais · vermelho: aumento de despesas · verde: redução. A
        sequência segue as maiores mudanças em valor absoluto, não a ordem
        cronológica. Clique numa categoria para aplicar o filtro ao painel.
        “Outras” preserva a soma das categorias agrupadas.
      </p>
      <details>
        <summary>
          Ver todas as categorias e valores ({analise.variacoes.length})
        </summary>
        <div className="wf-table">
          <table>
            <thead>
              <tr>
                <th>Categoria</th>
                <th>Anterior</th>
                <th>Atual</th>
                <th>Diferença</th>
              </tr>
            </thead>
            <tbody>
              {analise.variacoes.map((v) => (
                <tr key={v.categoria}>
                  <th>
                    <button
                      type="button"
                      onClick={() =>
                        onCategoria(
                          analise.categoria === v.categoria ? "" : v.categoria,
                        )
                      }
                    >
                      {v.categoria}
                    </button>
                  </th>
                  <td>{moedaFinanceira(v.anterior)}</td>
                  <td>{moedaFinanceira(v.atual)}</td>
                  <td>{moedaFinanceira(v.diferenca)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </section>
  );
}
