import type { ExpenseRow } from "./expenseExplorerService";
export function agruparCategorias(linhas: ExpenseRow[]) {
  const grupos = new Map<
    string,
    {
      nome: string;
      atual: number;
      anterior: number;
      diferenca: number;
      filhos: ExpenseRow[];
    }
  >();
  for (const linha of linhas) {
    const codigo = linha.nome.match(/^\s*(\d+\.\d+)(?=\D|$)/)?.[1];
    const nome = codigo ? `Grupo ${codigo}` : "Sem prefixo numérico";
    const grupo = grupos.get(nome) ?? {
      nome,
      atual: 0,
      anterior: 0,
      diferenca: 0,
      filhos: [],
    };
    grupo.atual += linha.atual;
    grupo.anterior += linha.anterior;
    grupo.diferenca += linha.diferenca;
    grupo.filhos.push(linha);
    grupos.set(nome, grupo);
  }
  return [...grupos.values()].sort((a, b) => b.atual - a.atual);
}
export function prepararCascata(
  anterior: number,
  atual: number,
  variacoes: { categoria: string; diferenca: number }[],
  todas = false,
) {
  const centavos = (v: number) => Math.round(v * 100);
  const ordenadas = [...variacoes].sort(
    (a, b) => Math.abs(b.diferenca) - Math.abs(a.diferenca),
  );
  const escolhidas = todas ? ordenadas : ordenadas.slice(0, 6);
  const demais = todas ? [] : ordenadas.slice(6);
  const movimentos = escolhidas.map((v) => ({
    nome: v.categoria,
    delta: centavos(v.diferenca),
    categoria: v.categoria,
  }));
  if (demais.length)
    movimentos.push({
      nome: `Outras categorias (${demais.length})`,
      delta: demais.reduce((s, v) => s + centavos(v.diferenca), 0),
      categoria: "",
    });
  let acumulado = centavos(anterior);
  const barras = [
    {
      nome: "Anterior",
      delta: 0,
      intervalo: [Math.min(0, acumulado) / 100, Math.max(0, acumulado) / 100],
      categoria: "",
      total: true,
      valor: anterior,
    },
  ];
  for (const m of movimentos) {
    const inicio = acumulado;
    acumulado += m.delta;
    barras.push({
      nome: m.nome,
      delta: m.delta / 100,
      intervalo: [
        Math.min(inicio, acumulado) / 100,
        Math.max(inicio, acumulado) / 100,
      ],
      categoria: m.categoria,
      total: false,
      valor: m.delta / 100,
    });
  }
  barras.push({
    nome: "Atual",
    delta: 0,
    intervalo: [
      Math.min(0, centavos(atual)) / 100,
      Math.max(0, centavos(atual)) / 100,
    ],
    categoria: "",
    total: true,
    valor: atual,
  });
  return { barras, conciliado: acumulado === centavos(atual) };
}
