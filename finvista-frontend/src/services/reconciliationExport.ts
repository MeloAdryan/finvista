export function uniqueReconciliationRows(
  rows: (string | number)[][],
): (string | number)[][] {
  if (!rows.length) return [];
  const rank = (status: string | number) =>
    status === "DIVERGENCIA_POR_ID"
      ? 6
      : status === "ID_BAIXA_DATA_TIPO_VALOR"
        ? 5
        : String(status).startsWith("SUGESTAO")
          ? 4
          : status === "AMBIGUIDADE"
            ? 3
            : status === "CANDIDATO_NAO_VINCULADO"
              ? 2
              : 1;
  const movements = new Map<string, (string | number)[]>();
  for (const row of rows.slice(1)) {
    const key = JSON.stringify([row[1], row[2]]);
    const before = movements.get(key);
    if (!before || rank(row[0]) > rank(before[0])) movements.set(key, row);
  }
  return [rows[0], ...movements.values()];
}
