import ContaAzulParcelDetailPanel from "./ContaAzulParcelDetailPanel";
import { useEffect, useRef, useState, useId } from "react";
import type { Cliente } from "../services/clientService";
import {
  consultarFinanceiroContaAzul,
  type ConsultaContaAzul,
  type FiltroContaAzul,
  type TipoConsulta,
  type ClassificacaoContaAzul,
} from "../services/contaAzulFinancialService";
import "../styles/conta-azul-financial.css";

const money = (value: number | null) =>
  value == null
    ? "Não informado"
    : value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const date = (value: string | null) =>
  value ? value.split("-").reverse().join("/") : "Não informada";
const names = (items: ClassificacaoContaAzul[] | null) =>
  items?.map((v) => v.nome || v.id || "Sem identificação").join(", ") ||
  "Não informados";

export default function ContaAzulFinancialPanel({
  cliente,
}: {
  cliente: Cliente | null;
}) {
  const [inicio, setInicio] = useState("");
  const [fim, setFim] = useState("");
  const [tipo, setTipo] = useState<TipoConsulta>("DESPESA");
  const [resultado, setResultado] = useState<ConsultaContaAzul | null>(null);
  const [erro, setErro] = useState("");
  const [busy, setBusy] = useState(false);
  const [detalheId, setDetalheId] = useState<string | null>(null);
  const detailPanelId = useId();
  const pending = useRef<AbortController | null>(null);
  useEffect(() => () => pending.current?.abort(), []);

  async function carregar(filtro: FiltroContaAzul, pagina: number) {
    pending.current?.abort();
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true);
    setErro("");
    setResultado(null);
    setDetalheId(null);
    try {
      const value = await consultarFinanceiroContaAzul(
        filtro,
        pagina,
        controller.signal,
      );
      if (!controller.signal.aborted) setResultado(value);
    } catch (e) {
      if (!controller.signal.aborted)
        setErro(e instanceof Error ? e.message : "Falha na consulta.");
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  function consultar(event: React.FormEvent) {
    event.preventDefault();
    if (!cliente) return;
    if (!inicio || !fim || inicio > fim) {
      setErro("Informe um intervalo de vencimentos válido.");
      return;
    }
    void carregar({ clienteId: cliente.id, tipo, inicio, fim }, 1);
  }
  const pages = resultado
    ? Math.max(1, Math.ceil(resultado.totalItens / resultado.tamanhoPagina))
    : 1;
  return (
    <section className="ca-financial" aria-busy={busy}>
      <h2>Consultar parcelas na Conta Azul</h2>
      <p>
        Consulta por <strong>vencimento</strong>, separada das análises do
        FinVista. Os resultados não alteram os lançamentos cadastrados.
      </p>
      <form onSubmit={consultar} className="ca-financial-filters">
        <label>
          Tipo
          <select
            value={tipo}
            disabled={busy}
            onChange={(e) => setTipo(e.target.value as TipoConsulta)}
          >
            <option value="DESPESA">Contas a pagar</option>
            <option value="RECEITA">Contas a receber</option>
          </select>
        </label>
        <label>
          Vencimento inicial
          <input
            type="date"
            required
            value={inicio}
            disabled={busy}
            onChange={(e) => setInicio(e.target.value)}
          />
        </label>
        <label>
          Vencimento final
          <input
            type="date"
            required
            min={inicio || undefined}
            value={fim}
            disabled={busy}
            onChange={(e) => setFim(e.target.value)}
          />
        </label>
        <button type="submit" disabled={!cliente || busy}>
          {busy ? "Consultando…" : "Consultar parcelas"}
        </button>
      </form>
      <p>
        É necessário autorizar a empresa no painel acima. O intervalo máximo é
        de 366 dias.
      </p>
      {erro && (
        <p role="alert" className="ca-financial-error">
          {erro}
        </p>
      )}
      {resultado && (
        <>
          <p aria-live="polite">
            <strong>
              {resultado.totalItens} parcelas informadas pela Conta Azul.
            </strong>{" "}
            Vencimentos: {date(resultado.inicio)} a {date(resultado.fim)} ·{" "}
            {resultado.tipo === "DESPESA"
              ? "Contas a pagar"
              : "Contas a receber"}{" "}
            · consulta em{" "}
            {new Date(resultado.consultadoEm).toLocaleString("pt-BR")}.
          </p>
          <p>
            Exibindo {resultado.itens.length} parcelas desta página. Categorias
            e centros não informam valores de rateio nesta listagem.
          </p>
          {resultado.itens.length === 0 ? (
            <p>Nenhuma parcela nesta página para o período consultado.</p>
          ) : (
            <div className="ca-financial-table">
              <table>
                <caption>
                  Parcelas consultadas — página {resultado.pagina}
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Detalhe</th>
                    <th scope="col">Parcela</th>
                    <th scope="col">Vencimento / competência</th>
                    <th scope="col">Status</th>
                    <th scope="col">Total</th>
                    <th scope="col">Pago / recebido</th>
                    <th scope="col">Pendente</th>
                    <th scope="col">Categorias / centros</th>
                  </tr>
                </thead>
                <tbody>
                  {resultado.itens.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <button
                          type="button"
                          aria-expanded={detalheId === item.id}
                          aria-controls={
                            detalheId === item.id ? detailPanelId : undefined
                          }
                          onClick={() =>
                            setDetalheId((value) =>
                              value === item.id ? null : item.id,
                            )
                          }
                        >
                          Ver detalhes
                        </button>
                      </td>
                      <td>
                        {item.descricao || "Sem descrição"}
                        <small>{item.id}</small>
                      </td>
                      <td>
                        {date(item.data_vencimento)}
                        <small>
                          Competência: {date(item.data_competencia)}
                        </small>
                      </td>
                      <td>
                        {item.status_traduzido ||
                          item.status ||
                          "Não informado"}
                      </td>
                      <td className="ca-financial-money">
                        {money(item.total)}
                      </td>
                      <td className="ca-financial-money">{money(item.pago)}</td>
                      <td className="ca-financial-money">
                        {money(item.nao_pago)}
                      </td>
                      <td>
                        {names(item.categorias)}
                        <small>Centros: {names(item.centros_custo)}</small>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {detalheId && (
            <ContaAzulParcelDetailPanel
              key={`${resultado.clienteId}:${detalheId}`}
              clienteId={resultado.clienteId}
              parcelaId={detalheId}
              panelId={detailPanelId}
              fechar={() => setDetalheId(null)}
            />
          )}
          <div className="ca-financial-pages">
            <button
              type="button"
              disabled={busy || resultado.pagina <= 1}
              onClick={() => void carregar(resultado, resultado.pagina - 1)}
            >
              Anterior
            </button>
            <span>
              Página {resultado.pagina} de {pages}
            </span>
            <button
              type="button"
              disabled={busy || resultado.pagina >= pages}
              onClick={() => void carregar(resultado, resultado.pagina + 1)}
            >
              Próxima
            </button>
          </div>
          <p>
            Os registros podem mudar entre consultas. Esta conferência ainda não
            aplica importação nem compara com os arquivos já importados.
          </p>
        </>
      )}
    </section>
  );
}
