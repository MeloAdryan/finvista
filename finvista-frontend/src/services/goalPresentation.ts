import type { SpendingGoal } from "./spendingGoalService";
export const moedaMeta = (v: number) =>
  Number(v).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
export const percentual = (v: number) =>
  Number(v).toLocaleString("pt-BR", { maximumFractionDigits: 1 });
export const periodoMeta = (m: SpendingGoal) =>
  m.situacaoTemporal === "ENCERRADA"
    ? "Encerrada"
    : m.situacaoTemporal === "FUTURA"
      ? "Futura"
      : m.situacaoTemporal === "EM_ANDAMENTO"
        ? "Em andamento"
        : "Atualize o backend";
