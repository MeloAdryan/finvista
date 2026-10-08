export const moedaFinanceira = (valor: number) =>
  Number(valor).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
export const compactoFinanceiro = (valor: number) =>
  Number(valor).toLocaleString("pt-BR", {
    notation: "compact",
    maximumFractionDigits: 1,
  });
export function eixoFinanceiro(valores: number[]) {
  const validos = valores.filter(Number.isFinite);
  const minimo = Math.min(0, ...validos),
    maximo = Math.max(0, ...validos);
  const bruto = (maximo - minimo || 100) / 5;
  const potencia = 10 ** Math.floor(Math.log10(bruto));
  const proporcao = bruto / potencia;
  const passo =
    (proporcao <= 1 ? 1 : proporcao <= 2 ? 2 : proporcao <= 5 ? 5 : 10) *
    potencia;
  const inferior = Math.floor(minimo / passo) * passo,
    superior = Math.ceil((maximo || passo) / passo) * passo;
  const ticks = Array.from(
    { length: Math.round((superior - inferior) / passo) + 1 },
    (_, i) => Number((inferior + i * passo).toPrecision(12)),
  );
  return { domain: [inferior, superior] as [number, number], ticks };
}
export function periodoISO(mes: string) {
  const [nome, ano] = mes.split("/");
  const nomes = [
    "jan",
    "fev",
    "mar",
    "abr",
    "mai",
    "jun",
    "jul",
    "ago",
    "set",
    "out",
    "nov",
    "dez",
  ];
  const numero =
    nomes.indexOf(nome?.trim().toLocaleLowerCase("pt-BR").replace(".", "")) + 1;
  return numero && ano ? `${ano}-${String(numero).padStart(2, "0")}` : "";
}
export function mesAtualSaoPaulo() {
  const partes = new Intl.DateTimeFormat("en", {
    timeZone: "America/Sao_Paulo",
    year: "numeric",
    month: "2-digit",
  }).formatToParts(new Date());
  return `${partes.find((p) => p.type === "year")?.value}-${partes.find((p) => p.type === "month")?.value}`;
}
export function prepararEvolucao<
  T extends {
    mes: string;
    entradas: number;
    saidas: number;
    saldoFinal: number;
  },
>(dados: T[]) {
  let acumulado = 0;
  return dados.map((item) => {
    const resultado = Number(item.entradas) - Number(item.saidas);
    acumulado += resultado;
    return {
      ...item,
      resultado,
      acumuladoPeriodo: acumulado,
      acumuladoHistorico: Number(item.saldoFinal),
    };
  });
}
export function exportarFinanceiro(
  nome: string,
  linhas: (string | number | null)[][],
) {
  const campo = (v: string | number | null) => {
    let texto =
      typeof v === "number" ? v.toFixed(2).replace(".", ",") : String(v ?? "");
    if (typeof v === "string" && /^[\s]*[=+@-]/.test(texto))
      texto = "'" + texto;
    return '"' + texto.replaceAll('"', '""') + '"';
  };
  const texto =
    "\uFEFF" + linhas.map((l) => l.map(campo).join(";")).join("\r\n");
  const url = URL.createObjectURL(
    new Blob([texto], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = nome;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
