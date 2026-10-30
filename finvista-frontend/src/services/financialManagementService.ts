import { API_URL } from "../config/api";
import { apiFetch } from "../config/http";
export type Total = { receita: number; despesa: number; resultado: number };
export type OpenItem = {
  documento?: string | null;
  origem?: string | null;
  situacao?: string | null;
  competencia?: string | null;
  principalAberto?: number | null;
  id: number;
  descricao: string;
  tipo: string;
  vencimento: string | null;
  aberto: number | null;
  faixa: string;
};
export type Health = {
  clienteId: number;
  consultadoEm: string;
  processadorHabilitado: boolean;
  automacaoAtiva: boolean;
  sincronizacaoAmpla: boolean;
  ultimaSituacao: string;
  ultimaConcluida: string | null;
  atualizacaoAtrasada: boolean;
  totalPendencias: number;
  liquidoVinculado: number;
  principalSemLiquido: number;
  pendenciasSemValor: number;
  pendencias: {
    parcelaId: string;
    tipo: string;
    descricao: string;
    motivo: string;
    avaliadaEm: string;
    liquidoAssociado: number | null;
    principalAssociado: number | null;
  }[];
  fontes: {
    fonte: string;
    registros: number;
    primeiraData: string | null;
    ultimaData: string | null;
    atualizadoEm: string | null;
  }[];
  semSaldoAberto: number;
  semVencimento: number;
  aviso: string;
};
export type Aging = {
  clienteId: number;
  referencia: string;
  grupos: {
    tipo: string;
    faixa: string;
    quantidade: number;
    valorConhecido: number;
    semValor: number;
  }[];
  total: number;
  pagina: number;
  tamanho: number;
  itens: OpenItem[];
  aviso: string;
};
export type Forecast = {
  clienteId: number;
  hoje: string;
  inicio: string;
  fim: string;
  fonte: string;
  realizado: Total;
  compromissosFuturos: Total;
  vencidosAtuais: Total;
  meses: {
    mes: string;
    recebido: number;
    pago: number;
    receberPrevisto: number;
    pagarPrevisto: number;
    receberVencido: number;
    pagarVencido: number;
  }[];
  compromissos: OpenItem[];
  exclusoes: Record<string, number>;
  aviso: string;
};
export const dreGroups = [
  "RECEITA_OPERACIONAL",
  "DEDUCOES_RECEITA",
  "CUSTOS",
  "DESPESAS_OPERACIONAIS",
  "RECEITA_FINANCEIRA",
  "DESPESA_FINANCEIRA",
  "FORA_DRE",
] as const;
export type DreGroup = (typeof dreGroups)[number];
export type Dre = {
  clienteId: number;
  estado: string;
  categorias: {
    tipo: string;
    categoria: string;
    valor: number;
    grupo: DreGroup | null;
    id: string | null;
    versao: number | null;
  }[];
  demonstrativo: { nome: string; valor: number }[];
  naoMapeadas: number;
  foraReceitas: number;
  foraDespesas: number;
  exclusoes?: Record<string, number>;
  aviso: string;
};
export type RecordDetail = {
  clienteId: number;
  criterio: string;
  id: number;
  documento: string | null;
  descricao: string;
  tipo: string;
  origem: string;
  situacao: string;
  competencia: string | null;
  vencimento: string | null;
  principal: number | null;
  realizado: number | null;
  aberto: number | null;
  valorSelecionado: number | null;
  rateios: { categoria: string; centro: string; valor: number; data: string }[];
  baixas: {
    id: string;
    data: string;
    principal: number | null;
    liquido: number | null;
    selecionada: boolean;
  }[];
  arquivo: string | null;
  importadoEm: string | null;
  avisos: string[];
};
export async function managementGet<T extends { clienteId: number }>(
  endpoint: string,
  clienteId: number,
  params: Record<string, string> = {},
  signal?: AbortSignal,
): Promise<T> {
  const r = await apiFetch(
    `${API_URL}/api/gestao-financeira/${endpoint}?${new URLSearchParams(params)}`,
    { credentials: "include", cache: "no-store", signal },
  );
  const body = await r.json().catch(() => null);
  if (!r.ok)
    throw new Error(
      body?.mensagem || "Não foi possível consultar esta empresa.",
    );
  if (body?.clienteId !== clienteId)
    throw new Error("A empresa mudou. Consulte novamente.");
  return body as T;
}
export async function saveDre(body: unknown, signal?: AbortSignal) {
  const r = await apiFetch(
    `${API_URL}/api/admin/gestao-financeira/dre-mapeamentos`,
    {
      method: "POST",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal,
    },
  );
  const value = await r.json().catch(() => null);
  if (!r.ok)
    throw new Error(
      value?.mensagem || "Falha ao salvar. Consulte novamente antes de tentar.",
    );
}
export const money = (n: number | null | undefined) =>
  n == null
    ? "Não informado"
    : n.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
export const dateLabel = (s: string | null | undefined) =>
  s ? s.slice(0, 10).split("-").reverse().join("/") : "Não informado";

export type OpenPosition = {
  clienteId: number;
  referencia: string;
  consultadoEm: string;
  grupos: Aging["grupos"];
  itens: OpenItem[];
  aviso: string;
};
const openBandLabels: Record<string, string> = {
  HOJE: "Vence hoje",
  DIAS_1_30: "Vencido 1–30 dias",
  DIAS_31_60: "Vencido 31–60 dias",
  DIAS_61_90: "Vencido 61–90 dias",
  MAIS_90: "Vencido há mais de 90 dias",
  A_VENCER: "A vencer após hoje",
  SEM_SALDO: "Saldo líquido ausente ou inválido",
  SEM_VENCIMENTO: "Vencimento ausente ou inválido",
};
export function openPositionRows(data: OpenPosition): (string | number)[][] {
  return [
    [
      "Empresa",
      "Referência da posição",
      "Consultado em",
      "Registro FinVista",
      "ID externo da parcela",
      "Descrição",
      "Tipo",
      "Vencimento",
      "Saldo líquido em aberto",
      "Principal em aberto (não somar ao líquido)",
      "Faixa",
      "Origem",
    ],
    ...data.itens.map((x) => [
      data.clienteId,
      data.referencia,
      data.consultadoEm,
      x.id,
      x.documento ?? "",
      x.descricao,
      x.tipo === "RECEITA" ? "A receber" : "A pagar",
      x.vencimento ?? "",
      x.aberto ?? "",
      x.principalAberto ?? "",
      openBandLabels[x.faixa] ?? "Faixa a revisar",
      x.origem === "CONTA_AZUL_API"
        ? "Conta Azul — API"
        : x.origem === "EXCEL"
          ? "Arquivo Excel"
          : x.origem === "CSV"
            ? "Arquivo CSV"
            : "Outra fonte ou não informada",
    ]),
  ];
}
