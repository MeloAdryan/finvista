export function descreverRegistros(atual?: number, anterior?: number) {
  if (atual == null || anterior == null)
    return "Presença de registros não informada pela API.";
  if (atual === 0 && anterior === 0)
    return "Sem registros nos dois períodos para os filtros aplicados.";
  if (anterior === 0)
    return "Sem registros no período anterior para os filtros aplicados.";
  if (atual === 0)
    return "Sem registros no período atual para os filtros aplicados.";
  return `${atual} registro${atual === 1 ? "" : "s"} no período atual e ${anterior} no anterior.`;
}
