import { useEffect, useRef, useState, type FormEvent } from "react";
import { getHistory, type HistoryData } from "../services/historyService";
import { obterReferenciaFinanceira } from "../services/financialReferenceService";
import HistoryChart from "../charts/HistoryChart";
import "../styles/history.css";

const moeda = (value: number) =>
  value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
function recuarMeses(mes: string, quantidade: number) {
  const [ano, numero] = mes.split("-").map(Number);
  const date = new Date(Date.UTC(ano, numero - 1 - quantidade, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
}
function nomeMes(mes: string) {
  if (!mes) return "Sem limite";
  const [ano, numero] = mes.split("-").map(Number);
  return new Date(Date.UTC(ano, numero - 1, 1)).toLocaleDateString("pt-BR", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

export default function History() {
  const [inicio, setInicio] = useState("");
  const [fim, setFim] = useState("");
  const [referencia, setReferencia] = useState("");
  const [aplicado, setAplicado] = useState<{
    inicio: string;
    fim: string;
  } | null>(null);
  const [dados, setDados] = useState<HistoryData[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [pagina, setPagina] = useState(0);
  const request = useRef<AbortController | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    request.current = controller;
    async function abrir() {
      try {
        const ref = await obterReferenciaFinanceira();
        if (controller.signal.aborted) return;
        const ultimo = `${ref.ano}-${String(ref.mes).padStart(2, "0")}`;
        const primeiro = recuarMeses(ultimo, 11);
        setReferencia(ultimo);
        setInicio(primeiro);
        setFim(ultimo);
        const result = await getHistory(primeiro, ultimo, controller.signal);
        if (!controller.signal.aborted) {
          setDados(result);
          setAplicado({ inicio: primeiro, fim: ultimo });
        }
      } catch (error) {
        if (!controller.signal.aborted)
          setErro(
            error instanceof Error
              ? error.message
              : "Não foi possível carregar o histórico.",
          );
      } finally {
        if (!controller.signal.aborted) setCarregando(false);
      }
    }
    void abrir();
    return () => {
      controller.abort();
      request.current?.abort();
    };
  }, []);

  async function carregar(primeiro: string, ultimo: string) {
    if (primeiro && ultimo && primeiro > ultimo) {
      setErro("O mês inicial deve ser anterior ou igual ao mês final.");
      return;
    }
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    setCarregando(true);
    setErro(null);
    try {
      const result = await getHistory(
        primeiro || undefined,
        ultimo || undefined,
        controller.signal,
      );
      if (!controller.signal.aborted) {
        setDados(result);
        setAplicado({ inicio: primeiro, fim: ultimo });
        setPagina(0);
      }
    } catch (error) {
      if (!controller.signal.aborted)
        setErro(
          error instanceof Error
            ? error.message
            : "Não foi possível aplicar o período.",
        );
    } finally {
      if (!controller.signal.aborted) setCarregando(false);
    }
  }
  function atalho(meses: number | null) {
    const ultimo = meses === null ? "" : referencia;
    const primeiro = meses === null ? "" : recuarMeses(referencia, meses - 1);
    setInicio(primeiro);
    setFim(ultimo);
    void carregar(primeiro, ultimo);
  }
  function aplicar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void carregar(inicio, fim);
  }
  const receita = dados.reduce((s, d) => s + d.receita, 0);
  const despesa = dados.reduce((s, d) => s + d.despesa, 0);
  const resultado = dados.reduce((s, d) => s + d.resultado, 0);
  const totalPaginas = Math.max(1, Math.ceil(dados.length / 12));
  const linhas = dados.slice(pagina * 12, pagina * 12 + 12);
  const periodo = aplicado
    ? !aplicado.inicio && !aplicado.fim
      ? "Todo o histórico"
      : `${nomeMes(aplicado.inicio)} até ${nomeMes(aplicado.fim)}`
    : "Período ainda não carregado";

  return (
    <div className="fv-history">
      <header className="fv-history-heading">
        <span>EVOLUÇÃO FINANCEIRA</span>
        <h1>Histórico financeiro</h1>
        <p>
          Compare os meses e acompanhe a evolução dos valores contabilizados.
        </p>
      </header>
      <section
        className="fv-history-panel"
        aria-labelledby="history-filter-title"
      >
        <div className="fv-history-panel-heading">
          <div>
            <h2 id="history-filter-title">Escolha o período</h2>
            <p>
              Comece pelos meses recentes ou consulte um intervalo
              personalizado.
            </p>
          </div>
          <span className="fv-history-badge">Visão mensal</span>
        </div>
        <div className="fv-history-shortcuts" aria-label="Períodos rápidos">
          {[6, 12, 24].map((meses) => (
            <button
              key={meses}
              type="button"
              disabled={carregando || !referencia}
              onClick={() => atalho(meses)}
            >
              {meses} meses até a referência
            </button>
          ))}
          <button
            type="button"
            disabled={carregando}
            onClick={() => atalho(null)}
          >
            Todo o histórico
          </button>
        </div>
        <form className="fv-history-filter" onSubmit={aplicar}>
          <label htmlFor="history-start">
            Mês inicial
            <input
              id="history-start"
              type="month"
              value={inicio}
              onChange={(e) => setInicio(e.target.value)}
              disabled={carregando}
            />
          </label>
          <label htmlFor="history-end">
            Mês final
            <input
              id="history-end"
              type="month"
              value={fim}
              onChange={(e) => setFim(e.target.value)}
              disabled={carregando}
            />
          </label>
          <button
            className="fv-history-primary"
            type="submit"
            disabled={carregando}
          >
            {carregando ? "Carregando..." : "Aplicar período"}
          </button>
        </form>
        <p className="fv-history-applied" aria-live="polite">
          Exibindo: <strong>{periodo}</strong>
        </p>
        <p className="fv-history-note">
          Valores contabilizados podem incluir lançamentos em aberto. Esta
          análise não representa o saldo bancário. Todo o histórico pode incluir
          meses futuros.
        </p>
        {erro && (
          <div className="fv-history-error" role="alert">
            <p>{erro}</p>
            <button
              type="button"
              disabled={carregando}
              onClick={() => void carregar(inicio, fim)}
            >
              Tentar novamente
            </button>
          </div>
        )}
      </section>
      <div aria-busy={carregando}>
        {carregando && (
          <p className="fv-history-status" role="status">
            Atualizando o histórico. Aguarde para conferir os valores.
          </p>
        )}
        {!carregando && aplicado && (
          <>
            <section
              className="fv-history-kpis"
              aria-label="Totais do período exibido"
            >
              <article>
                <span>Receita acumulada</span>
                <strong>{moeda(receita)}</strong>
                <small>No período exibido</small>
              </article>
              <article>
                <span>Despesa acumulada</span>
                <strong>{moeda(despesa)}</strong>
                <small>No período exibido</small>
              </article>
              <article
                className={
                  resultado < 0 ? "fv-history-negative" : "fv-history-positive"
                }
              >
                <span>Resultado acumulado</span>
                <strong>{moeda(resultado)}</strong>
                <small>Receita menos despesa</small>
              </article>
            </section>
            {dados.length === 0 ? (
              <section className="fv-history-panel fv-history-empty">
                <h2>Nenhum mês retornado</h2>
                <p>Experimente outro período ou consulte todo o histórico.</p>
              </section>
            ) : (
              <>
                <HistoryChart dados={dados} />
                <section
                  className="fv-history-panel"
                  aria-labelledby="history-months-title"
                >
                  <div className="fv-history-panel-heading">
                    <div>
                      <h2 id="history-months-title">Mês a mês</h2>
                      <p>Os mesmos valores do gráfico, em detalhes.</p>
                    </div>
                    <span className="fv-history-badge">
                      {dados.length} meses
                    </span>
                  </div>
                  <table className="fv-history-table">
                    <caption className="fv-history-sr">
                      Valores mensais do período {periodo}
                    </caption>
                    <thead>
                      <tr>
                        <th scope="col">Mês</th>
                        <th scope="col">Receita</th>
                        <th scope="col">Despesa</th>
                        <th scope="col">Resultado</th>
                        <th scope="col">Margem</th>
                      </tr>
                    </thead>
                    <tbody>
                      {linhas.map((d) => (
                        <tr key={d.periodo}>
                          <th scope="row">{d.periodo}</th>
                          <td data-label="Receita">{moeda(d.receita)}</td>
                          <td data-label="Despesa">{moeda(d.despesa)}</td>
                          <td
                            data-label="Resultado"
                            className={
                              d.resultado < 0
                                ? "fv-history-negative"
                                : "fv-history-positive"
                            }
                          >
                            {moeda(d.resultado)}
                          </td>
                          <td data-label="Margem">
                            {d.receita === 0
                              ? "Não aplicável"
                              : `${d.margem.toLocaleString("pt-BR", { maximumFractionDigits: 2 })}%`}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <nav
                    className="fv-history-pagination"
                    aria-label="Paginação dos meses"
                  >
                    <button
                      type="button"
                      disabled={pagina === 0}
                      onClick={() => setPagina((p) => p - 1)}
                    >
                      Anterior
                    </button>
                    <span aria-live="polite">
                      Página {pagina + 1} de {totalPaginas}
                    </span>
                    <button
                      type="button"
                      disabled={pagina + 1 >= totalPaginas}
                      onClick={() => setPagina((p) => p + 1)}
                    >
                      Próxima
                    </button>
                  </nav>
                </section>
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}
