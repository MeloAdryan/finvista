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

/** Presentation of the already-filtered expense groups; original groups remain available. */
export function prepararComposicaoExecutiva(
  rows: {
    categoria: string;
    atual: number;
    anterior: number;
    diferenca: number;
  }[],
  receita: number,
  despesa: number,
  anterior: number,
  limite = 6,
) {
  const cents = (n: number) => Math.round(n * 100);
  const finitos = [
    receita,
    despesa,
    anterior,
    ...rows.flatMap((r) => [r.atual, r.anterior, r.diferenca]),
  ].every(Number.isFinite);
  const categorias = rows
    .map((r) => ({ ...r }))
    .sort(
      (a, b) =>
        b.atual - a.atual || a.categoria.localeCompare(b.categoria, "pt-BR"),
    );
  const soma = categorias.reduce((n, r) => n + cents(r.atual), 0);
  const somaAnterior = categorias.reduce((n, r) => n + cents(r.anterior), 0);
  const fecha = finitos && soma === cents(despesa);
  const fechaAnterior = finitos && somaAnterior === cents(anterior);
  const positivas = categorias.filter((r) => r.atual > 0);
  const negativos = categorias.some((r) => r.atual < 0);
  const quantidade = Math.max(1, Math.floor(limite));
  const principais = categorias
    .filter((r) => r.atual !== 0 || r.anterior !== 0)
    .slice(0, quantidade);
  const restantes = categorias
    .filter((r) => r.atual !== 0 || r.anterior !== 0)
    .slice(quantidade);
  const grupos = principais.map((r) => ({ ...r, agrupado: false }));
  if (restantes.length)
    grupos.push({
      categoria: `Outras categorias (${restantes.length})`,
      atual: restantes.reduce((n, r) => n + cents(r.atual), 0) / 100,
      anterior: restantes.reduce((n, r) => n + cents(r.anterior), 0) / 100,
      diferenca:
        restantes.reduce((n, r) => n + cents(r.atual) - cents(r.anterior), 0) /
        100,
      agrupado: true,
    });
  let acumulado = 0;
  const pareto = categorias.map((r) => {
    acumulado += cents(r.atual);
    return {
      ...r,
      percentual: despesa > 0 ? (r.atual / despesa) * 100 : null,
      acumulado:
        fecha && !negativos && despesa > 0
          ? (acumulado / cents(despesa)) * 100
          : null,
    };
  });
  const passos = fecha
    ? grupos
    : [
        {
          categoria: "Despesas do recorte",
          atual: despesa,
          anterior,
          diferenca: despesa - anterior,
          agrupado: true,
        },
      ];
  let saldo = cents(receita);
  const barras = [
    {
      nome: "Receita",
      valor: receita,
      inicio: 0,
      fim: receita,
      total: true,
      categoria: "",
    },
  ];
  for (const g of passos) {
    const inicio = saldo;
    saldo -= cents(g.atual);
    barras.push({
      nome: g.categoria,
      valor: -g.atual,
      inicio: inicio / 100,
      fim: saldo / 100,
      total: false,
      categoria: g.agrupado ? "" : g.categoria,
    });
  }
  barras.push({
    nome: "Resultado",
    valor: saldo / 100,
    inicio: 0,
    fim: saldo / 100,
    total: true,
    categoria: "",
  });
  return {
    categorias,
    grupos,
    pareto,
    barras,
    fecha,
    fechaAnterior,
    negativos,
    finito: finitos,
    diferenca: (cents(despesa) - soma) / 100,
    maior: positivas[0] ?? null,
  };
}
