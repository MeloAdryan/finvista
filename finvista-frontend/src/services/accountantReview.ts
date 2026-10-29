export type FinancialObject = Record<string, unknown>;
export const object = (value: unknown): FinancialObject =>
  value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as FinancialObject : {};
export const text = (value: unknown, fallback = "Não informado") =>
  typeof value === "string" && value.trim() ? value.trim() : fallback;
export function cents(value: unknown): number | null {
  if (typeof value !== "number" || !Number.isFinite(value)) return null;
  const rounded = Math.round(value * 100);
  return Number.isSafeInteger(rounded) && Math.abs(value * 100 - rounded) < 0.001 ? rounded : null;
}
export const money = (value: unknown) => {
  const n = cents(value);
  return n === null ? "Não informado" : (n / 100).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
};
export function date(value: unknown) {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}(T.*)?$/.test(value)) return "Não informada";
  const [year, month, day] = value.slice(0, 10).split("-");
  return `${day}/${month}/${year}`;
}
export function dateTime(value: unknown) {
  if (typeof value !== "string" || !/^\d{4}-\d{2}-\d{2}T/.test(value)) return "Não informada";
  const instant = new Date(value);
  return Number.isFinite(instant.getTime()) ? new Intl.DateTimeFormat("pt-BR", {
    timeZone: "America/Sao_Paulo", dateStyle: "short", timeStyle: "short",
  }).format(instant) : "Não informada";
}
export function statusLabel(value: unknown) {
  const labels: Record<string, string> = {
    QUITADO: "Quitado", RECEBIDO: "Recebido", PENDENTE: "Em aberto", EM_ABERTO: "Em aberto",
    ATRASADO: "Vencido", RECEBIDO_PARCIAL: "Recebido parcialmente", CANCELADO: "Cancelado",
    EM_ANDAMENTO: "Em andamento", AGUARDANDO_REPETICAO: "Aguardando nova tentativa",
    CONCLUIDA: "Concluída", CONCLUIDA_COM_PENDENCIAS: "Concluída com pendências",
    PARCIAL: "Cobertura incompleta", CANCELADA: "Cancelada", BLOQUEADO: "Conferência necessária",
    REGISTRO_IDENTIFICADO: "Registro localizado", CORRESPONDENCIA_POSSIVEL: "Possível registro existente",
    SEM_CORRESPONDENCIA: "Sem correspondência confirmada", SEM_FONTE_ARQUIVADA: "Detalhes ainda indisponíveis",
    FONTE_INVALIDA: "Detalhes indisponíveis para conferência", IDENTIDADE_DIVERGENTE: "Referência divergente",
  };
  return typeof value === "string" ? labels[value] ?? "Situação não reconhecida" : "Não informada";
}
export function paymentAmount(payment: unknown): number | null {
  const c = object(object(payment).valor_composicao);
  if (cents(c.valor_liquido) !== null) return c.valor_liquido as number;
  const parts = [c.valor_bruto, c.juros, c.multa, c.desconto].map(cents);
  // No assumption about fees or missing components. This is a display calculation only.
  if (parts.some(n => n === null) || cents(c.taxa) !== 0) return null;
  return (parts[0]! + parts[1]! + parts[2]! - parts[3]!) / 100;
}
export function paymentCheck(paid: unknown, payments: unknown, parcel?: unknown) {
  const paidCents = cents(paid);
  const rows = Array.isArray(payments) ? payments : [];
  const amounts = rows.map(paymentAmount);
  const complete = Array.isArray(payments) && amounts.every(v => v !== null);
  const sum = complete ? amounts.reduce<number>((total, v) => total + cents(v)!, 0) : null;
  const gross = rows.map(row => cents(object(object(row).valor_composicao).valor_bruto));
  const grossSum = Array.isArray(payments) && gross.every(v => v !== null)
    ? gross.reduce<number>((total, value) => total + value!, 0) : null;
  const p = object(parcel), c = object(p.valor_composicao);
  const nominal = cents(c.valor_bruto), open = cents(p.nao_pago);
  const explicitZeroAdjustments = [c.juros, c.multa, c.desconto, c.taxa].every(v => cents(v) === 0);
  const componentsClose = rows.every(row => {
    const part = object(object(row).valor_composicao);
    const [b, j, m, d, fee] = [part.valor_bruto, part.juros, part.multa, part.desconto, part.taxa].map(cents);
    const net = cents(paymentAmount(row));
    return [b,j,m,d].every(v => v !== null && v >= 0) && fee === 0 && net !== null && net >= 0 && b!+j!+m!-d! === net;
  });
  const principalBasis = paidCents !== null && paidCents >= 0 && grossSum === paidCents && sum !== null
    && nominal !== null && nominal >= 0 && open !== null && open >= 0 && nominal === paidCents + open
    && cents(c.valor_liquido) === nominal && explicitZeroAdjustments && componentsClose
    && (p.valor_total_liquido == null || cents(p.valor_total_liquido) === nominal);
  const compared = principalBasis ? grossSum : sum;
  return { rows, amounts, paid: paidCents === null ? null : paidCents / 100,
    sum: sum === null ? null : sum / 100,
    principal: grossSum === null ? null : grossSum / 100,
    adjustment: sum === null || grossSum === null ? null : (sum - grossSum) / 100,
    basis: principalBasis ? "PRINCIPAL" : "LIQUIDO",
    difference: compared === null || paidCents === null ? null : (paidCents - compared) / 100 };
}
export function accountName(value: unknown) {
  // Identifiers alone are not account names. Do not expose provider UUIDs as labels.
  const a = object(value);
  return text(a.nome ?? a.descricao, "Nome da conta não informado");
}
export function allocationReview(parcel: unknown) {
  const rows = object(object(parcel).evento).rateio;
  if (!Array.isArray(rows)) return "Categorias não informadas";
  return rows.map(value => {
    const category = object(value), centers = category.rateio_centro_custo;
    const detail = Array.isArray(centers) ? centers.map(v => {
      const center = object(v);
      return `${text(center.nome_centro_custo, "Centro sem nome")}: bruto ${money(center.valor_bruto)}, líquido ${money(center.valor)}`;
    }).join("; ") || "Sem centros informados" : "Distribuição de centros não informada";
    return `${text(category.nome_categoria, "Categoria sem nome")}: bruto ${money(category.valor_bruto)}, líquido ${money(category.valor)}. Centros: ${detail}`;
  }).join(" | ");
}
export function pendingReport(payload: unknown): (string | number)[][] {
  const data = object(payload);
  if (!Array.isArray(data.pendencias)) throw new Error("O relatório recebido não contém a lista de pendências.");
  const output: (string | number)[][] = [["Referência da parcela", "Descrição", "Tipo", "Situação da conferência",
    "Valor pago informado (uma vez por parcela)", "Soma dos pagamentos detalhados (uma vez por parcela)",
    "Diferença na base comparada (uma vez por parcela)", "Data do pagamento", "Valor do pagamento",
    "Motivos para conferência", "Consulta arquivada (horário de Brasília)", "Bruto da baixa", "Juros da baixa", "Multa da baixa", "Desconto da baixa", "Taxa da baixa", "Líquido informado na baixa",
    "Base comparada", "Principal das baixas (uma vez por parcela)", "Ajuste líquido das baixas (uma vez por parcela)", "Categorias e centros para conferência"]];
  for (const value of data.pendencias) {
    const p = object(value);
    const check = paymentCheck(p.pagoInformado, p.capturadoEm ? p.baixas : undefined, p.parcelaArquivada);
    const reasons = Array.isArray(p.impedimentos) ? p.impedimentos.filter(v => typeof v === "string").join(" | ") : "";
    const rows = check.rows.length ? check.rows : [null];
    rows.forEach((payment, index) => output.push([
      text(p.parcelaId), text(p.descricao), p.tipo === "RECEITA" ? "Recebimento" : p.tipo === "DESPESA" ? "Pagamento" : "Não informado",
      statusLabel(p.situacaoPlano), index ? "" : check.paid ?? "Não informado",
      index ? "" : check.sum ?? "Não informado", index ? "" : check.difference ?? "Não calculável",
      payment === null ? "Sem pagamentos detalhados" : date(object(payment).data_pagamento),
      payment === null ? "" : check.amounts[index] ?? "Não informado",
      index ? "" : reasons || text(p.mensagemOriginal, "Revisar os documentos e preparar uma nova conferência."),
      dateTime(p.capturadoEm),
      ...["valor_bruto", "juros", "multa", "desconto", "taxa", "valor_liquido"].map(key => {
        if (payment === null) return "";
        const value = object(object(payment).valor_composicao)[key];
        return cents(value) === null ? "Não informado" : value as number;
      }),
      check.basis === "PRINCIPAL" ? "Principal informado versus principal das baixas" : "Pago informado versus líquido das baixas",
      index ? "" : check.principal ?? "Não informado", index ? "" : check.adjustment ?? "Não calculável",
      index ? "" : allocationReview(p.parcelaArquivada),
    ]));
  }
  return output;
}
