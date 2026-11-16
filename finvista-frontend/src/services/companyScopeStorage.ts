interface EscopoEmpresas {
  modo: "individual" | "todas";
  usuarioAtendido?: number;
}

function chave(usuarioId: number) {
  return `finvista:escopo-empresas:v1:${usuarioId}`;
}

export function lerEscopoEmpresas(usuarioId: number): EscopoEmpresas | null {
  try {
    const salvo = sessionStorage.getItem(chave(usuarioId));
    if (!salvo) return null;
    const escopo = JSON.parse(salvo) as Partial<EscopoEmpresas> | null;
    if (!escopo || (escopo.modo !== "individual" && escopo.modo !== "todas"))
      return null;
    const usuarioAtendido = escopo.usuarioAtendido;
    if (
      usuarioAtendido !== undefined &&
      (!Number.isSafeInteger(usuarioAtendido) || usuarioAtendido <= 0)
    )
      return null;
    return { modo: escopo.modo, usuarioAtendido };
  } catch {
    return null;
  }
}

export function salvarEscopoEmpresas(
  usuarioId: number,
  escopo: EscopoEmpresas | null,
) {
  try {
    if (escopo)
      sessionStorage.setItem(chave(usuarioId), JSON.stringify(escopo));
    else sessionStorage.removeItem(chave(usuarioId));
  } catch {
    return;
  }
}
