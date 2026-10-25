import { FinancialViewContext } from "../contexts/financialViewContext";
import { viewRange } from "../services/financialViewService";
import { useContext, useEffect, useState, type FormEvent } from "react";
import {
  getProjectionPlanning,
  type ProjectionInputs,
  type ProjectionPlanning,
} from "../services/projectionPlanningService";
import { moedaFinanceira } from "../services/financialVisualization";
import ProjectionChart from "../charts/ProjectionChart";
import "../styles/dashboard.css";
import "../styles/financial-evolution.css";

export default function Projection() {
  const filtroVisual = useContext(FinancialViewContext);
  const faixa = filtroVisual ? viewRange(filtroVisual) : null;
  const [dados, setDados] = useState<ProjectionPlanning | null>(null);
  const [inputs, setInputs] = useState<ProjectionInputs>({});
  const [formulario, setFormulario] = useState({
    receita: "",
    saldo: "0",
    variacao: "20",
  });
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    let ativo = true;
    async function carregar() {
      setCarregando(true);
      try {
        const resposta = await getProjectionPlanning(inputs, controller.signal);
        if (ativo) {
          setDados(resposta);
          setErro("");
          setFormulario({
            receita: String(resposta.receitaMensalEsperada),
            saldo: String(resposta.saldoInicial),
            variacao: String(resposta.variacaoPercentual),
          });
        }
      } catch (e) {
        if (ativo && !controller.signal.aborted)
          setErro(
            e instanceof Error ? e.message : "Falha ao simular projeção.",
          );
      } finally {
        if (ativo) setCarregando(false);
      }
    }
    void carregar();
    return () => {
      ativo = false;
      controller.abort();
    };
  }, [inputs]);
  function aplicar(e: FormEvent) {
    e.preventDefault();
    const receita =
      formulario.receita.trim() === "" ? undefined : Number(formulario.receita);
    const saldo = Number(formulario.saldo),
      variacao = Number(formulario.variacao);
    if (
      (receita != null && (!Number.isFinite(receita) || receita < 0)) ||
      !Number.isFinite(saldo) ||
      !Number.isFinite(variacao) ||
      variacao < 0 ||
      variacao > 100
    ) {
      setErro(
        "Informe receita não negativa, saldo válido e variação entre 0 e 100%.",
      );
      return;
    }
    setInputs({
      receitaMensal: receita,
      saldoInicial: saldo,
      variacaoPercentual: variacao,
    });
  }
  const mesesVisiveis =
    dados?.meses.filter(
      (m) =>
        !faixa ||
        (m.periodo >= faixa.inicio.slice(0, 7) &&
          m.periodo <= faixa.fim.slice(0, 7)),
    ) ?? [];
  const primeiroIndice =
    dados && mesesVisiveis.length
      ? dados.meses.findIndex((m) => m.periodo === mesesVisiveis[0].periodo)
      : 0;
  const dadosVisiveis = dados
    ? {
        ...dados,
        meses: mesesVisiveis,
        recorteVisual: !!faixa,
        inicio: mesesVisiveis.length
          ? `${mesesVisiveis[0].periodo}-01`
          : dados.inicio,
        fim: mesesVisiveis.length
          ? new Date(
              Date.UTC(
                Number(mesesVisiveis.at(-1)!.periodo.slice(0, 4)),
                Number(mesesVisiveis.at(-1)!.periodo.slice(5, 7)),
                0,
                12,
              ),
            )
              .toISOString()
              .slice(0, 10)
          : dados.fim,
        saldosAnteriores:
          primeiroIndice > 0
            ? {
                base: dados.meses[primeiroIndice - 1].saldoBase,
                conservador: dados.meses[primeiroIndice - 1].saldoConservador,
                otimista: dados.meses[primeiroIndice - 1].saldoOtimista,
              }
            : undefined,
      }
    : null;
  return (
    <div className="dashboard fe-page">
      <section className="fe-card">
        <span className="fe-eyebrow">Premissas da simulação</span>
        <h1>Projeção financeira de seis meses</h1>
        <p className="fe-note">
          As despesas são as já cadastradas. Receitas complementares são
          hipóteses, não lançamentos novos. Os campos não alteram o banco.
        </p>
        <form className="fe-inputs" onSubmit={aplicar}>
          <label>
            Receita total esperada por mês (R$)
            <input
              type="number"
              min="0"
              max="9999999999999.99"
              step="0.01"
              disabled={carregando}
              value={formulario.receita}
              placeholder="Média dos últimos três meses"
              onChange={(e) =>
                setFormulario({ ...formulario, receita: e.target.value })
              }
            />
          </label>
          <label>
            Saldo inicial informado (R$)
            <input
              type="number"
              min="-9999999999999.99"
              max="9999999999999.99"
              step="0.01"
              disabled={carregando}
              value={formulario.saldo}
              onChange={(e) =>
                setFormulario({ ...formulario, saldo: e.target.value })
              }
            />
          </label>
          <label>
            Variação da receita nos cenários (%)
            <input
              type="number"
              min="0"
              max="100"
              step="0.01"
              disabled={carregando}
              value={formulario.variacao}
              onChange={(e) =>
                setFormulario({ ...formulario, variacao: e.target.value })
              }
            />
          </label>
          <button type="submit" disabled={carregando}>
            Simular
          </button>
        </form>
        <div className="fe-actions">
          <button
            type="button"
            disabled={carregando}
            onClick={() =>
              setInputs({
                saldoInicial: Number(formulario.saldo) || 0,
                variacaoPercentual: Number(formulario.variacao) || 0,
              })
            }
          >
            Usar média histórica
          </button>
          <button
            type="button"
            disabled={carregando}
            onClick={() =>
              setInputs({
                receitaMensal: 0,
                saldoInicial: 0,
                variacaoPercentual: 0,
              })
            }
          >
            Somente registros, sem complementos
          </button>
        </div>
        {dados && (
          <p className="fe-note">
            Média de receita registrada nos três meses completos anteriores:{" "}
            {moedaFinanceira(dados.mediaReceitaMensal)}. Meses sem receita
            entram como zero. O valor sugerido fica em zero se a média líquida
            for negativa.
          </p>
        )}
        {erro && (
          <p role="alert" className="fe-error">
            {erro}{" "}
            <button type="button" onClick={() => setInputs({ ...inputs })}>
              Tentar novamente
            </button>
          </p>
        )}
        {carregando && <p role="status">Calculando simulação…</p>}
      </section>
      {!carregando &&
        !erro &&
        dadosVisiveis &&
        (mesesVisiveis.length ? (
          <>
            <p className="fe-note">
              Meses completos da simulação que cruzam o filtro. Os saldos
              preservam os meses anteriores da simulação. O filtro não altera a
              base por competência nem cria projeções fora do horizonte de seis
              meses.
            </p>
            <ProjectionChart dados={dadosVisiveis} />
          </>
        ) : (
          <p role="status">
            Nenhum mês da projeção cruza o período escolhido. Use Todo período
            para exibir os seis meses disponíveis.
          </p>
        ))}
    </div>
  );
}
