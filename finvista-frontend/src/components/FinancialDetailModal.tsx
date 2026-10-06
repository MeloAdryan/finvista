import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  getFinancialDetail,
  type FinancialDetailData,
  type FinancialDetailKind,
} from "../services/financialDetailService";
import "../styles/financial-detail.css";

interface Props {
  tipo: FinancialDetailKind;
  valorDoCartao: number;
  onFechar: () => void;
}

const moeda = (valor: number | null) =>
  valor === null
    ? "Não informado"
    : valor.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const data = (valor: string | null) =>
  valor ? valor.split("-").reverse().join("/") : "Não informada";

function FinancialDetailModal({ tipo, valorDoCartao, onFechar }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const fechar = useRef<HTMLButtonElement>(null);
  const mesConsultado = useRef<string | undefined>(undefined);
  const tituloId = useId();
  const explicacaoId = useId();
  const [pagina, setPagina] = useState(0);
  const [tentativa, setTentativa] = useState(0);
  const [dados, setDados] = useState<FinancialDetailData | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");

  useEffect(() => {
    const elemento = dialog.current;
    const anterior = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    elemento?.showModal();
    fechar.current?.focus();
    return () => {
      elemento?.close();
      document.body.style.overflow = overflow;
      if (anterior instanceof HTMLElement && anterior.isConnected)
        anterior.focus();
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    async function carregar() {
      setCarregando(true);
      setErro("");
      setDados(null);
      try {
        const resultado = await getFinancialDetail(
          tipo,
          pagina,
          mesConsultado.current,
          controller.signal,
        );
        if (!controller.signal.aborted) {
          mesConsultado.current = resultado.mes;
          setDados(resultado);
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setErro(
            error instanceof Error
              ? error.message
              : "Falha ao consultar os lançamentos.",
          );
        }
      } finally {
        if (!controller.signal.aborted) setCarregando(false);
      }
    }
    void carregar();
    return () => controller.abort();
  }, [tipo, pagina, tentativa]);

  const visivel =
    dados?.pagina === pagina && dados.tipo === tipo ? dados : null;
  const divergiu =
    visivel !== null &&
    Math.round(visivel.total * 100) !== Math.round(valorDoCartao * 100);
  const nome = tipo === "RECEITA" ? "receita" : "despesa";

  return createPortal(
    <dialog
      ref={dialog}
      className="financial-detail-modal"
      aria-labelledby={tituloId}
      aria-describedby={explicacaoId}
      onCancel={(event) => {
        event.preventDefault();
        onFechar();
      }}
      onMouseDown={(event) => event.stopPropagation()}
      onClick={(event) => {
        if (event.target !== event.currentTarget) return;
        const limites = event.currentTarget.getBoundingClientRect();
        if (
          event.clientX < limites.left ||
          event.clientX > limites.right ||
          event.clientY < limites.top ||
          event.clientY > limites.bottom
        )
          onFechar();
      }}
    >
      <header className="financial-detail-header">
        <div>
          <span>CONFERÊNCIA DO CARTÃO</span>
          <h2 id={tituloId}>De onde vem a {nome}?</h2>
        </div>
        <button ref={fechar} type="button" onClick={onFechar}>
          Fechar
        </button>
      </header>

      <div className="financial-detail-body">
        <p id={explicacaoId} className="financial-detail-formula">
          Total = soma dos valores contabilizados dos lançamentos do período.
          Cada lançamento entra uma única vez, mesmo quando tem vários rateios.
          Lançamentos em aberto também podem compor esse total; ele não
          representa somente pagamentos ou recebimentos.
        </p>

        {carregando && <p role="status">Consultando os lançamentos...</p>}
        {erro && (
          <div role="alert" className="financial-detail-error">
            <p>{erro}</p>
            <button
              type="button"
              onClick={() => setTentativa((valor) => valor + 1)}
            >
              Tentar novamente
            </button>
          </div>
        )}

        {!carregando && !erro && visivel && (
          <>
            <div className="financial-detail-summary">
              <div>
                <span>Período pela data de análise</span>
                <strong>
                  {data(visivel.dataInicial)} até {data(visivel.dataFinal)}
                </strong>
              </div>
              <div>
                <span>Total de todas as páginas</span>
                <strong>{moeda(visivel.total)}</strong>
              </div>
              <div>
                <span>Lançamentos que compõem o total</span>
                <strong>{visivel.quantidadeLancamentos}</strong>
              </div>
            </div>

            {divergiu && (
              <p role="status" className="financial-detail-warning">
                O total consultado difere do cartão ({moeda(valorDoCartao)}). Os
                dados ou o mês de referência podem ter mudado. Feche esta janela
                e atualize o painel para conferir novamente.
              </p>
            )}

            {visivel.quantidadeLancamentos === 0 ? (
              <p>Nenhum lançamento compõe este cartão no período.</p>
            ) : visivel.itens.length === 0 ? (
              <div>
                <p>
                  Esta página ficou sem lançamentos. Consulte o início da lista.
                </p>
                <button type="button" onClick={() => setPagina(0)}>
                  Voltar à primeira página
                </button>
              </div>
            ) : (
              <div className="financial-detail-table-wrap">
                <table className="financial-detail-table">
                  <caption>Lançamentos que compõem o total de {nome}</caption>
                  <thead>
                    <tr>
                      <th scope="col">Lançamento</th>
                      <th scope="col">Data de análise</th>
                      <th scope="col">Situação</th>
                      <th scope="col">Valor no cartão</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visivel.itens.map((item, indice) => (
                      <tr key={item.id ?? `${pagina}-${indice}`}>
                        <td data-label="Lançamento">
                          <strong>{item.descricao || "Sem descrição"}</strong>
                          {item.id !== null && (
                            <small>Lançamento #{item.id}</small>
                          )}
                          <details>
                            <summary>Mais informações</summary>
                            <dl>
                              <div>
                                <dt>Vencimento</dt>
                                <dd>{data(item.vencimento)}</dd>
                              </div>
                              <div>
                                <dt>Data de realização</dt>
                                <dd>{data(item.realizacao)}</dd>
                              </div>
                              <div>
                                <dt>Valor original informado</dt>
                                <dd>{moeda(item.valorOriginal)}</dd>
                              </div>
                              <div>
                                <dt>Principal realizado informado</dt>
                                <dd>{moeda(item.principalRealizado)}</dd>
                              </div>
                              <div>
                                <dt>Principal em aberto informado</dt>
                                <dd>{moeda(item.principalAberto)}</dd>
                              </div>
                            </dl>
                            <p>
                              Campos não informados não são tratados como zero.
                              A coluna “Valor no cartão” é a que entra na soma.
                            </p>
                          </details>
                        </td>
                        <td data-label="Data de análise">
                          {data(item.dataAnalise)}
                        </td>
                        <td data-label="Situação">
                          {item.situacao || "Não informada"}
                        </td>
                        <td
                          data-label="Valor no cartão"
                          className="financial-detail-amount"
                        >
                          {moeda(item.valorContabilizado)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            {visivel.totalPaginas > 0 && (
              <nav
                className="financial-detail-pagination"
                aria-label="Páginas dos lançamentos"
              >
                <button
                  type="button"
                  disabled={pagina === 0}
                  onClick={() => setPagina((valor) => valor - 1)}
                >
                  Anterior
                </button>
                <span>
                  Página {pagina + 1} de {visivel.totalPaginas} ·{" "}
                  {visivel.quantidadeLancamentos} lançamentos
                </span>
                <button
                  type="button"
                  disabled={pagina + 1 >= visivel.totalPaginas}
                  onClick={() => setPagina((valor) => valor + 1)}
                >
                  Próxima
                </button>
              </nav>
            )}
          </>
        )}
      </div>
    </dialog>,
    document.body,
  );
}

export default FinancialDetailModal;
