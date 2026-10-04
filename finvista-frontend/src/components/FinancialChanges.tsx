import { useEffect, useState } from "react";
import {
  getFinancialChanges,
  type FinancialChangesData,
} from "../services/financialChangesService";
import "../styles/financial-changes.css";

const moeda = (valor: number) =>
  valor.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });

const percentual = (valor: number) =>
  Math.abs(valor).toLocaleString("pt-BR", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });

const mes = (periodo: string) =>
  new Intl.DateTimeFormat("pt-BR", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${periodo}-01T12:00:00Z`));

function FinancialChanges() {
  const [dados, setDados] = useState<FinancialChangesData | null>(null);
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [tentativa, setTentativa] = useState(0);
  const [mostrarTodas, setMostrarTodas] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    async function carregar() {
      setCarregando(true);
      setErro("");
      try {
        const resultado = await getFinancialChanges(controller.signal);
        if (!controller.signal.aborted) {
          setDados(resultado);
          setMostrarTodas(false);
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setErro(error instanceof Error ? error.message : "Falha ao carregar.");
        }
      } finally {
        if (!controller.signal.aborted) setCarregando(false);
      }
    }
    void carregar();
    return () => controller.abort();
  }, [tentativa]);

  if (carregando) {
    return <section className="financial-changes" aria-busy="true">
      <h2>O que mudou?</h2><p>Comparando as despesas dos dois meses...</p>
    </section>;
  }

  if (erro) {
    return <section className="financial-changes">
      <h2>O que mudou?</h2>
      <p role="alert">{erro}</p>
      <button type="button" onClick={() => setTentativa((valor) => valor + 1)}>
        Tentar novamente
      </button>
    </section>;
  }

  if (!dados) return null;
  const categorias = mostrarTodas ? dados.categorias : dados.categorias.slice(0, 5);
  const classe = dados.diferenca > 0 ? "increase"
    : dados.diferenca < 0 ? "decrease" : "neutral";

  const resumo = dados.lancamentosAtual === 0 && dados.lancamentosAnterior === 0
    ? "Sem despesas registradas nos dois meses."
    : dados.diferenca === 0
      ? "O total de despesas permaneceu igual."
      : `As despesas ${dados.diferenca > 0 ? "aumentaram" : "diminuíram"} ${moeda(Math.abs(dados.diferenca))}.`;

  return (
    <section className="financial-changes">
      <header>
        <span className="financial-changes-eyebrow">COMPARAÇÃO MENSAL</span>
        <h2>O que mudou?</h2>
        <p>{mes(dados.mesAtual)} em relação a {mes(dados.mesAnterior)}</p>
      </header>

      <p className={`financial-changes-result financial-changes-${classe}`}>
        {resumo}
        {dados.variacaoPercentual !== null && dados.diferenca !== 0 && (
          <span> Variação de {percentual(dados.variacaoPercentual)}%.</span>
        )}
      </p>

      <div className="financial-changes-totals">
        <div><span>Mês anterior</span><strong>{moeda(dados.despesaAnterior)}</strong></div>
        <div><span>Mês de referência</span><strong>{moeda(dados.despesaAtual)}</strong></div>
      </div>

      {dados.lancamentosAnterior === 0 && dados.lancamentosAtual > 0 && (
        <p className="financial-changes-note">
          Não há despesas registradas no mês anterior. Isso não confirma que a importação desse mês está completa.
        </p>
      )}
      {dados.lancamentosAtual === 0 && dados.lancamentosAnterior > 0 && (
        <p className="financial-changes-note">
          Não há despesas registradas no mês de referência. Confira se a importação está completa.
        </p>
      )}

      {dados.categorias.length > 0 ? (
        <details className="financial-changes-details">
          <summary>Ver categorias que mudaram ({dados.categorias.length})</summary>
          <p className="financial-changes-note">
            Ordenadas pelo maior impacto em reais. Aumentos e reduções podem se compensar.
          </p>
          <ul>
            {categorias.map((item) => (
              <li key={item.categoria}>
                <div>
                  <strong>{item.categoria}</strong>
                  <small>De {moeda(item.valorAnterior)} para {moeda(item.valorAtual)}</small>
                </div>
                <span className={`financial-changes-delta financial-changes-${item.diferenca > 0 ? "increase" : "decrease"}`}>
                  {item.diferenca > 0 ? "↑ Aumento" : "↓ Redução"}
                  <strong>{moeda(Math.abs(item.diferenca))}</strong>
                </span>
              </li>
            ))}
          </ul>
          <p className="financial-changes-note" aria-live="polite">
            Exibindo {categorias.length} de {dados.categorias.length} categorias com variação.
          </p>
          {dados.categorias.length > 5 && (
            <button type="button" aria-expanded={mostrarTodas}
              onClick={() => setMostrarTodas((valor) => !valor)}>
              {mostrarTodas ? "Mostrar menos" : "Ver todas as variações"}
            </button>
          )}
        </details>
      ) : (
        <p className="financial-changes-note">Nenhuma variação por categoria nos dois meses.</p>
      )}
    </section>
  );
}

export default FinancialChanges;