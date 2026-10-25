import { useState } from "react";
import {
  summarizeStatement,
  planStatement,
  importStatement,
  type Statement,
  type StatementPlan,
  type StatementImported,
} from "../services/contaAzulStatementService";
import type { Cliente } from "../services/clientService";
import "../styles/conta-azul-statement.css";

const money = (cents: number) =>
  (cents / 100).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const date = (v: string | null) =>
  v ? v.split("-").reverse().join("/") : "Não informada";
export default function ContaAzulStatementPanel({
  statement,
  file,
  cliente,
}: {
  statement: Statement;
  file?: File;
  cliente: Cliente | null;
}) {
  const [start, setStart] = useState("");
  const [end, setEnd] = useState("");
  const [page, setPage] = useState(0);
  const [plan, setPlan] = useState<StatementPlan | null>(null);
  const [saved, setSaved] = useState<StatementImported | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const [error, setError] = useState("");
  async function prepare() {
    if (!cliente || !file || busy) return;
    setBusy(true);
    setError("");
    setConfirmed(false);
    setPlan(null);
    setSaved(null);
    try {
      const next = await planStatement(file, cliente.id);
      if (next.clienteId !== cliente.id)
        throw new Error("A empresa mudou. Atualize a página.");
      setPlan(next);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Falha na prévia.");
    } finally {
      setBusy(false);
    }
  }
  async function apply() {
    if (
      !plan ||
      !file ||
      !confirmed ||
      busy ||
      plan.conflitos.length ||
      !cliente ||
      plan.clienteId !== cliente.id
    )
      return;
    setBusy(true);
    setError("");
    try {
      setSaved(await importStatement(file, plan));
      setPlan(null);
      setConfirmed(false);
    } catch (e) {
      setPlan(null);
      setConfirmed(false);
      setError(
        `${e instanceof Error ? e.message : "Falha na inclusão."} Gere outra prévia para conferir o que está gravado.`,
      );
    } finally {
      setBusy(false);
    }
  }
  const invalidRange = !!start && !!end && start > end;
  const lines = invalidRange
    ? []
    : statement.linhas.filter(
        (line) =>
          (!start && !end) ||
          (line.movimento &&
            (!start || line.movimento >= start) &&
            (!end || line.movimento <= end)),
      );
  const totals = summarizeStatement(lines);
  const pages = Math.max(1, Math.ceil(lines.length / 20));
  const currentPage = Math.min(page, pages - 1);
  const cards: [string, number][] = [
    ["Entradas financeiras", totals.entradasFinanceiras],
    ["Saídas financeiras", totals.saidasFinanceiras],
    ["Entradas de transferências", totals.entradasTransferencias],
    ["Saídas de transferências", totals.saidasTransferencias],
    ["Entradas de outras origens", totals.entradasOutras],
    ["Saídas de outras origens", totals.saidasOutras],
  ];
  return (
    <section
      className="ca-statement"
      aria-label="Conferência do extrato financeiro"
    >
      <h3>Extrato financeiro reconhecido</h3>
      <p>
        Confira as movimentações e prepare a inclusão abaixo. O extrato alimenta
        os gráficos e cartões das páginas no critério{" "}
        <strong>Baixa — extrato importado</strong>. Parcelas e baixas da API
        ficam em suas próprias fontes, sem somar o mesmo pagamento duas vezes.
      </p>
      <p>
        Competência e vencimento usam parcelas de Contas a pagar / Contas a
        receber. Uma movimentação não informa necessariamente o valor integral
        ou o saldo aberto da parcela. A posição em aberto e os saldos bancários
        mantêm suas consultas próprias.
      </p>
      <div className="ca-statement-filters">
        <label>
          Movimento inicial
          <input
            type="date"
            value={start}
            onChange={(e) => {
              setStart(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <label>
          Movimento final
          <input
            type="date"
            value={end}
            onChange={(e) => {
              setEnd(e.target.value);
              setPage(0);
            }}
          />
        </label>
        <button
          type="button"
          onClick={() => {
            setStart("");
            setEnd("");
            setPage(0);
          }}
        >
          Todo o arquivo
        </button>
      </div>
      {invalidRange && (
        <p role="alert">A data inicial deve ser anterior ou igual à final.</p>
      )}
      <p>
        Arquivo: <strong>{statement.arquivo}</strong> ·{" "}
        {statement.linhas.length} movimentações no arquivo · {lines.length} no
        recorte · {totals.validas} somadas · {totals.invalidas} com erro, fora
        dos totais.
      </p>
      {statement.linhas.some((line) => !line.movimento) && (
        <p role="alert">
          Há linhas sem data válida. Consulte “Todo o arquivo” para revisar os
          erros.
        </p>
      )}
      <div className="ca-statement-summary">
        {cards.map(([label, value]) => (
          <div key={label}>
            <span>{label}</span>
            <strong>{money(value)}</strong>
          </div>
        ))}
      </div>
      <p>
        Saídas totais:{" "}
        <strong>
          {money(
            totals.saidasFinanceiras +
              totals.saidasTransferencias +
              totals.saidasOutras,
          )}
        </strong>{" "}
        · Saldo líquido das movimentações:{" "}
        <strong>
          {money(
            totals.entradasFinanceiras +
              totals.entradasTransferencias +
              totals.entradasOutras -
              totals.saidasFinanceiras -
              totals.saidasTransferencias -
              totals.saidasOutras,
          )}
        </strong>
      </p>
      <p>
        Os totais usam a data de movimento e o valor líquido de cada linha, sem
        somar juros e descontos novamente. Transferências ficam separadas; sua
        natureza precisa ser conferida. Esta leitura não comprova cobertura de
        todo o período ou de todas as contas.
      </p>
      {file && (
        <div
          className="ca-statement-import"
          aria-label="Incluir movimentações do extrato"
        >
          <h4>Incluir no FinVista</h4>
          <p>
            Destino: <strong>{cliente?.nome ?? "Selecione uma empresa"}</strong>
            . A inclusão considera{" "}
            <strong>
              todo o arquivo ({statement.linhas.length} movimentações)
            </strong>
            , independentemente do recorte acima. Transferências e campos
            originais serão preservados para consulta; transferências não entram
            como despesas nos indicadores.
          </p>
          <p>
            O arquivo não fornece um ID único de movimentação. A conferência
            compara data, conta, identificação, descrição, origem e valor,
            preservando a quantidade de linhas idênticas. Correspondências não
            comprovam identidade; confira antes de confirmar. Possíveis
            correções de valores ou rateios bloqueiam a inclusão para revisão.
          </p>
          <button
            type="button"
            disabled={
              busy ||
              !cliente ||
              statement.linhas.some((l) => l.erros.length > 0)
            }
            onClick={() => void prepare()}
          >
            {busy ? "Processando…" : "Preparar inclusão do extrato"}
          </button>
          {error && <p role="alert">{error}</p>}
          {plan && (
            <div role="status">
              <p>
                <strong>{plan.clienteNome}</strong> · {plan.total} no arquivo ·{" "}
                <strong>{plan.novas} novas</strong> · {plan.existentes} já
                existentes. Prévia válida até{" "}
                {new Date(plan.validade).toLocaleTimeString("pt-BR")}.
              </p>
              {plan.conflitos.length > 0 && (
                <ul>
                  {plan.conflitos.map((c, i) => (
                    <li key={i}>{c}</li>
                  ))}
                </ul>
              )}
              {plan.conflitos.length === 0 && plan.novas > 0 && (
                <>
                  <label className="ca-statement-confirm">
                    <input
                      type="checkbox"
                      checked={confirmed}
                      disabled={busy}
                      onChange={(e) => setConfirmed(e.target.checked)}
                    />
                    Conferi a empresa, as correspondências e autorizo incluir as{" "}
                    {plan.novas} movimentações novas de todo o arquivo.
                  </label>
                  <button
                    type="button"
                    disabled={!confirmed || busy}
                    onClick={() => void apply()}
                  >
                    Confirmar inclusão do extrato
                  </button>
                </>
              )}
              {plan.novas === 0 && plan.conflitos.length === 0 && (
                <p>
                  Todo o arquivo já está incluído. Nenhuma movimentação será
                  duplicada.
                </p>
              )}
            </div>
          )}
          {saved && (
            <p role="status">
              <strong>Inclusão concluída:</strong> {saved.incluidas} novas e{" "}
              {saved.existentes} já existentes. Abra o Painel executivo,
              selecione “Baixa — extrato importado”, escolha o período importado
              e aplique o filtro para conferir seus gráficos e cartões.
            </p>
          )}
        </div>
      )}
      <div
        className="ca-statement-table"
        tabIndex={0}
        aria-label="Movimentações do arquivo"
      >
        <table>
          <thead>
            <tr>
              <th>Linha</th>
              <th>Movimento</th>
              <th>Descrição / classificação</th>
              <th>Competência / vencimento</th>
              <th>Origem / conta</th>
              <th>Situação</th>
              <th>Valor líquido</th>
            </tr>
          </thead>
          <tbody>
            {lines
              .slice(currentPage * 20, currentPage * 20 + 20)
              .map((line) => (
                <tr key={line.numero}>
                  <td>{line.numero}</td>
                  <td>{date(line.movimento)}</td>
                  <td>
                    {line.descricao}
                    <small>
                      {line.tipo} ·{" "}
                      {line.categorias.join("; ") || "Sem categoria"}
                    </small>
                    <small>{line.centros.join("; ") || "Sem centro"}</small>
                    {line.campos?.["arquivo de importacao"] && (
                      <small>
                        Arquivo: {line.campos["arquivo de importacao"]}
                      </small>
                    )}
                    {line.erros.length > 0 && (
                      <strong role="alert">{line.erros.join(" ")}</strong>
                    )}
                    {line.campos && (
                      <details>
                        <summary>Ver dados originais</summary>
                        <dl>
                          {Object.entries(line.campos).map(([key, value]) => (
                            <div key={key}>
                              <dt>{key}</dt>
                              <dd>{value || "Não informado"}</dd>
                            </div>
                          ))}
                        </dl>
                      </details>
                    )}
                  </td>
                  <td>
                    {date(line.competencia)}
                    <small>{date(line.vencimento)}</small>
                  </td>
                  <td>
                    {line.origem}
                    <small>{line.conta}</small>
                  </td>
                  <td>{line.situacao}</td>
                  <td>
                    {line.valor === null
                      ? "Inválido"
                      : money(Math.round(Number(line.valor) * 100))}
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>
      {lines.length === 0 && <p>Nenhuma movimentação neste recorte.</p>}
      <nav aria-label="Páginas do extrato">
        <button
          type="button"
          disabled={currentPage === 0}
          onClick={() => setPage(currentPage - 1)}
        >
          Anterior
        </button>
        <span>
          Página {currentPage + 1} de {pages}
        </span>
        <button
          type="button"
          disabled={currentPage + 1 >= pages}
          onClick={() => setPage(currentPage + 1)}
        >
          Próxima
        </button>
      </nav>
    </section>
  );
}
