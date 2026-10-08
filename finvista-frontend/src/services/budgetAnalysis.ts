import type { BudgetPipelineData } from "./budgetPipelineService";

export const moedaOrcamento = (valor: number) =>
  Number(valor).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
export const dataOrcamento = (data: string) =>
  data.split("-").reverse().join("/");
export const percentualOrcamento = (valor: number) =>
  Number(valor).toLocaleString("pt-BR", {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  });
export const chavePeriodo = (item: BudgetPipelineData) =>
  `${item.dataInicio}|${item.dataFim}`;
export interface FiltroOrcamento {
  modo: "TODOS" | "EXATO" | "INTERSECAO";
  inicio: string;
  fim: string;
}
export function filtrarOrcamentos(
  dados: BudgetPipelineData[],
  filtro: FiltroOrcamento,
) {
  return dados
    .filter(
      (item) =>
        filtro.modo === "TODOS" ||
        (filtro.modo === "EXATO"
          ? item.dataInicio === filtro.inicio && item.dataFim === filtro.fim
          : item.dataInicio <= filtro.fim && item.dataFim >= filtro.inicio),
    )
    .sort(
      (a, b) =>
        Number(b.percentualUtilizado) - Number(a.percentualUtilizado) ||
        b.dataInicio.localeCompare(a.dataInicio) ||
        a.nome.localeCompare(b.nome),
    );
}
const normalizar = (valor: string | null) =>
  valor?.trim().toLocaleLowerCase("pt-BR") || "";
export function detectarSobreposicoes(dados: BudgetPipelineData[]) {
  const pares: { primeiro: BudgetPipelineData; segundo: BudgetPipelineData }[] =
    [];
  for (let i = 0; i < dados.length; i++)
    for (let j = i + 1; j < dados.length; j++) {
      const a = dados[i],
        b = dados[j];
      const coincide = (x: string | null, y: string | null) =>
        !normalizar(x) || !normalizar(y) || normalizar(x) === normalizar(y);
      if (
        a.dataInicio <= b.dataFim &&
        b.dataInicio <= a.dataFim &&
        coincide(a.categoria, b.categoria) &&
        coincide(a.centroCusto, b.centroCusto)
      )
        pares.push({ primeiro: a, segundo: b });
    }
  return pares;
}
export function exportarOrcamentos(dados: BudgetPipelineData[]) {
  const linhas: (string | number)[][] = [
    [
      "ID",
      "Orçamento",
      "Início",
      "Fim",
      "Centro de custo",
      "Categoria",
      "Planejado (R$)",
      "Utilizado (R$)",
      "Disponível (R$)",
      "Utilização (%)",
      "Excedente (R$)",
    ],
    ...dados.map((item) => [
      item.id,
      item.nome,
      item.dataInicio,
      item.dataFim,
      item.centroCusto ?? "Todos",
      item.categoria ?? "Todas",
      ...[
        item.valorPlanejado,
        item.valorUtilizado,
        item.valorDisponivel,
        item.percentualUtilizado,
        Math.max(0, item.valorUtilizado - item.valorPlanejado),
      ].map((v) => Number(v).toFixed(2).replace(".", ",")),
    ]),
  ];
  const csv =
    "\uFEFF" +
    linhas
      .map((l) =>
        l.map((v) => '"' + String(v).replaceAll('"', '""') + '"').join(";"),
      )
      .join("\r\n");
  const url = URL.createObjectURL(
    new Blob([csv], { type: "text/csv;charset=utf-8" }),
  );
  const link = document.createElement("a");
  link.href = url;
  link.download = "orcamentos-filtrados.csv";
  link.click();
  URL.revokeObjectURL(url);
}
