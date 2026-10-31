import { apiFetch } from "../config/http";
import { API_URL } from "../config/api";

export interface StatementLine {
  numero: number;
  movimento: string | null;
  competencia: string | null;
  vencimento: string | null;
  descricao: string;
  tipo: string;
  origem: string;
  conta: string;
  situacao: string;
  valor: number | string | null;
  categorias: string[];
  centros: string[];
  erros: string[];
  campos?: Record<string, string>;
}
export interface Statement {
  reconhecido: boolean;
  arquivo: string;
  linhas: StatementLine[];
  hoje?: string;
}
export interface StatementPlan {
  token: string;
  validade: string;
  clienteId: number;
  clienteNome: string;
  arquivo: string;
  total: number;
  novas: number;
  existentes: number;
  conflitos: string[];
  inicioHistorico?: string | null;
  revisadas?: number;
  avisos?: string[];
}
export interface StatementImported {
  clienteId: number;
  incluidas: number;
  existentes: number;
  total: number;
}
async function submit<T>(
  path: string,
  file: File,
  clienteId: number,
  fields: Record<string, string> = {},
): Promise<T> {
  const form = new FormData();
  form.append("arquivo", file);
  form.append("clienteId", String(clienteId));
  for (const [key, value] of Object.entries(fields)) form.append(key, value);
  const response = await apiFetch(
    `${API_URL}/api/importacoes/conta-azul/extrato/${path}`,
    {
      method: "POST",
      credentials: "include",
      body: form,
    },
  );
  const data = await response.json().catch(() => null);
  if (!response.ok)
    throw new Error(
      data?.mensagem ||
        data?.detail ||
        data?.message ||
        data?.erro ||
        "Não foi possível concluir. Gere outra prévia antes de tentar novamente.",
    );
  return data as T;
}
export const planStatement = (
  file: File,
  clienteId: number,
  inicioHistorico = "",
) =>
  submit<StatementPlan>(
    "planejar",
    file,
    clienteId,
    inicioHistorico ? { inicioHistorico } : {},
  );
export const importStatement = (file: File, plan: StatementPlan) =>
  submit<StatementImported>("importar", file, plan.clienteId, {
    token: plan.token,
    confirmado: "true",
  });
export async function getStoredStatements(
  clienteId: number,
): Promise<Statement> {
  const response = await apiFetch(
    `${API_URL}/api/importacoes/conta-azul/extrato/movimentos?clienteId=${clienteId}`,
    { credentials: "include", cache: "no-store" },
  );
  if (!response.ok)
    throw new Error("Não foi possível consultar os extratos incluídos.");
  const data = await response.json();
  if (!data || data.reconhecido !== true || !Array.isArray(data.linhas))
    throw new Error("Resposta inesperada ao consultar extratos.");
  return data as Statement;
}
export async function inspectStatement(
  file: File,
  signal?: AbortSignal,
): Promise<Statement | null> {
  if (!/\.xlsx?$/i.test(file.name)) return null;
  const form = new FormData();
  form.append("arquivo", file);
  const response = await apiFetch(
    `${API_URL}/api/importacoes/conta-azul/extrato/conferencia`,
    {
      method: "POST",
      credentials: "include",
      body: form,
      signal,
    },
  );
  const data = await response.json().catch(() => null);
  if (!response.ok)
    throw new Error(
      data?.mensagem ||
        data?.detail ||
        data?.message ||
        data?.erro ||
        "Não foi possível conferir o extrato. Verifique se o backend local foi atualizado.",
    );
  if (
    !data ||
    typeof data.reconhecido !== "boolean" ||
    !Array.isArray(data.linhas)
  )
    throw new Error("Resposta inesperada ao conferir o arquivo.");
  return data.reconhecido ? (data as Statement) : null;
}

const normalize = (v: string) =>
  v
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .trim()
    .replace(/\s+/g, " ")
    .toLowerCase();
export function summarizeStatement(lines: StatementLine[]) {
  const result = {
    entradasFinanceiras: 0,
    saidasFinanceiras: 0,
    entradasTransferencias: 0,
    saidasTransferencias: 0,
    entradasOutras: 0,
    saidasOutras: 0,
    invalidas: 0,
    validas: 0,
  };
  for (const line of lines) {
    if (line.erros.length || line.valor === null || !line.movimento) {
      result.invalidas++;
      continue;
    }
    const value = Math.round(Number(line.valor) * 100);
    if (!Number.isSafeInteger(value))
      throw new Error("Valor fora do limite de conferência.");
    const origin = normalize(line.origem);
    const group =
      origin === "lancamento financeiro"
        ? "Financeiras"
        : origin === "transferencia"
          ? "Transferencias"
          : "Outras";
    const key = `${value < 0 ? "saidas" : "entradas"}${group}` as
      | "entradasFinanceiras"
      | "saidasFinanceiras"
      | "entradasTransferencias"
      | "saidasTransferencias"
      | "entradasOutras"
      | "saidasOutras";
    result[key] += Math.abs(value);
    if (!Number.isSafeInteger(result[key]))
      throw new Error("Total fora do limite de conferência.");
    result.validas++;
  }
  return result;
}

export const statementToday = () =>
  new Intl.DateTimeFormat("sv-SE", { timeZone: "America/Sao_Paulo" }).format(
    new Date(),
  );
export function statementCashExclusion(
  line: StatementLine,
  today: string,
  start?: string,
) {
  if (line.erros.length || line.valor === null || !line.movimento)
    return "Dados inválidos";
  if (line.movimento > today) return "Movimento futuro: fora do realizado";
  const historical =
    start || line.campos?.["finvista:inicio historico conferido"];
  if (historical && line.movimento < historical)
    return "Anterior ao início histórico conferido";
  if (normalize(line.origem) !== "lancamento financeiro")
    return "Origem separada dos indicadores";
  if (
    !["receita", "despesa"].includes(normalize(line.tipo)) ||
    !["conciliado", "quitado", "pago", "recebido"].includes(
      normalize(line.situacao),
    )
  )
    return "Sem situação de pagamento reconhecida";
  return null;
}
export function summarizeRealizedStatement(
  lines: StatementLine[],
  today: string,
  start?: string,
) {
  const result = { receita: 0, despesa: 0, movimentos: 0, excluidos: 0 };
  for (const line of lines) {
    if (statementCashExclusion(line, today, start)) {
      result.excluidos++;
      continue;
    }
    const key = normalize(line.tipo) === "despesa" ? "despesa" : "receita";
    const value =
      Math.round(Number(line.valor) * 100) * (key === "despesa" ? -1 : 1);
    if (
      !Number.isSafeInteger(value) ||
      !Number.isSafeInteger(result[key] + value)
    )
      throw new Error("Valor fora do limite de conferência.");
    result[key] += value;
    result.movimentos++;
  }
  return result;
}
