import { accountName, date, money, object, paymentCheck, statusLabel, text } from "../services/accountantReview";

export default function ContaAzulFinancialSource({ tipo, dados }: { tipo: string; dados: unknown }) {
  const p = object(dados);
  if (tipo !== "PARCELA") {
    if (!Array.isArray(p.itens)) return <p>Os detalhes deste registro não estão disponíveis em formato de conferência. Solicite a revisão dos dados recebidos.</p>;
    if (!p.itens.length) return <p>Nenhum movimento encontrado nesta consulta.</p>;
    return <div className="ca-accountant-table" tabIndex={0} role="region" aria-label={tipo === "TRANSFERENCIA" ? "Transferências" : "Saldos iniciais"}>
      <table><caption>{tipo === "TRANSFERENCIA" ? "Transferências recebidas" : "Saldos iniciais recebidos"}</caption>
        <thead><tr><th>Data</th><th>Descrição</th><th>{tipo === "TRANSFERENCIA" ? "Conta de origem" : "Conta financeira"}</th>{tipo === "TRANSFERENCIA" && <th>Conta de destino</th>}<th>Valor informado</th></tr></thead>
        <tbody>{p.itens.map((item, index) => {
          const row = object(item);
          return <tr key={index}><td>{date(row.data ?? row.data_transferencia ?? row.data_saldo)}</td>
            <td>{text(row.descricao, tipo === "TRANSFERENCIA" ? "Transferência entre contas" : "Saldo inicial")}</td>
            <td>{accountName(row.conta_origem ?? row.conta_financeira_origem ?? row.conta_financeira)}</td>
            {tipo === "TRANSFERENCIA" && <td>{accountName(row.conta_destino ?? row.conta_financeira_destino)}</td>}
            <td className="ca-accountant-money">{money(row.valor ?? row.saldo_inicial)}</td></tr>;
        })}</tbody>
      </table><p>Datas, nomes e valores ausentes permanecem sinalizados. Transferências e saldos iniciais ficam separados das receitas e despesas dos indicadores.</p>
    </div>;
  }
  const event = object(p.evento);
  const check = paymentCheck(p.valor_pago, p.baixas);
  const composition = object(p.valor_composicao);
  return <div className="ca-accountant-record">
    <dl className="ca-accountant-summary">
      <div><dt>Situação</dt><dd>{statusLabel(p.status)}</dd></div>
      <div><dt>Competência</dt><dd>{date(event.data_competencia)}</dd></div>
      <div><dt>Vencimento</dt><dd>{date(p.data_vencimento)}</dd></div>
      <div><dt>Pago / recebido informado</dt><dd>{money(p.valor_pago)}</dd></div>
      <div><dt>Em aberto informado</dt><dd>{money(p.nao_pago)}</dd></div>
      <div><dt>Líquido da composição</dt><dd>{money(composition.valor_liquido)}</dd></div>
    </dl>
    <div className={`ca-accountant-check ${check.difference !== null && check.difference !== 0 ? "ca-accountant-warning" : ""}`}>
      <strong>Conferência dos pagamentos</strong>
      <dl className="ca-accountant-summary">
        <div><dt>Pago / recebido informado</dt><dd>{money(check.paid)}</dd></div>
        <div><dt>Soma dos pagamentos detalhados</dt><dd>{money(check.sum)}</dd></div>
        <div><dt>Diferença</dt><dd>{money(check.difference)}</dd></div>
      </dl>
      <p>{check.difference === null ? "Não há informações suficientes para conferir a soma dos pagamentos. Os campos ausentes não são considerados zero."
        : check.difference !== 0 ? "O valor pago informado e os pagamentos detalhados não conferem. Revise as baixas e prepare uma nova conferência da parcela."
        : "Os valores desta soma conferem. Isso não confirma os demais critérios nem a conciliação com o extrato."}</p>
    </div>
    {check.rows.length > 0 ? <div className="ca-accountant-table" tabIndex={0} role="region" aria-label="Pagamentos detalhados">
      <table><caption>Pagamentos detalhados</caption>
        <thead><tr><th>Data do pagamento</th><th>Conta financeira</th><th>Bruto</th><th>Juros</th><th>Multa</th><th>Desconto</th><th>Taxa</th><th>Valor líquido</th></tr></thead>
        <tbody>{check.rows.map((payment, index) => <tr key={index}>
          <td>{date(object(payment).data_pagamento)}</td><td>{accountName(object(payment).conta_financeira)}</td>
          {["valor_bruto", "juros", "multa", "desconto", "taxa"].map(key => <td key={key} className="ca-accountant-money">{money(object(object(payment).valor_composicao)[key])}</td>)}
          <td className="ca-accountant-money">{money(check.amounts[index])}</td></tr>)}</tbody>
      </table></div> : <p>Nenhum pagamento detalhado disponível nesta consulta arquivada.</p>}
    {Array.isArray(event.rateio) && event.rateio.length > 0 && <details>
      <summary>Categorias informadas no evento</summary>
      <p>A distribuição precisa ser conferida para esta parcela. Esta consulta não aplica rateios.</p>
      <div className="ca-accountant-table" tabIndex={0} role="region" aria-label="Categorias do evento">
        <table><thead><tr><th>Categoria</th><th>Valor informado</th></tr></thead>
          <tbody>{event.rateio.map((value, index) => <tr key={index}><td>{text(object(value).nome_categoria)}</td>
            <td className="ca-accountant-money">{money(object(value).valor)}</td></tr>)}</tbody></table>
      </div>
    </details>}
  </div>;
}
