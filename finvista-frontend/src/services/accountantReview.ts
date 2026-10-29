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
export function paymentCheck(paid: unknown, payments: unknown) {
  const paidCents = cents(paid);
  const rows = Array.isArray(payments) ? payments : [];
  const amounts = rows.map(paymentAmount);
  const complete = Array.isArray(payments) && amounts.every(v => v !== null);
  const sum = complete ? amounts.reduce<number>((total, v) => total + cents(v)!, 0) : null;
  return { rows, amounts, paid: paidCents === null ? null : paidCents / 100,
    sum: sum === null ? null : sum / 100,
    difference: sum === null || paidCents === null ? null : (paidCents - sum) / 100 };
}
export function accountName(value: unknown) {
  // Identifiers alone are not account names. Do not expose provider UUIDs as labels.
  const a = object(value);
  return text(a.nome ?? a.descricao, "Nome da conta não informado");
}
export function pendingReport(payload: unknown): (string | number)[][] {
  const data = object(payload);
  if (!Array.isArray(data.pendencias)) throw new Error("O relatório recebido não contém a lista de pendências.");
  const output: (string | number)[][] = [["Referência da parcela", "Descrição", "Tipo", "Situação da conferência",
    "Valor pago informado (uma vez por parcela)", "Soma dos pagamentos detalhados (uma vez por parcela)",
    "Diferença: informado menos detalhado (uma vez por parcela)", "Data do pagamento", "Valor do pagamento",
    "Motivos para conferência", "Consulta arquivada (horário de Brasília)", "Bruto da baixa", "Juros da baixa", "Multa da baixa", "Desconto da baixa", "Taxa da baixa", "Líquido informado na baixa"]];
  for (const value of data.pendencias) {
    const p = object(value);
    const check = paymentCheck(p.pagoInformado, p.capturadoEm ? p.baixas : undefined);
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
    ]));
  }
  return output;
}
