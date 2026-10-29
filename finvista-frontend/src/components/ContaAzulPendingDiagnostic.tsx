import { useEffect, useRef, useState } from "react";

import { API_URL } from "../config/api";

import { apiFetch } from "../config/http";



export default function ContaAzulPendingDiagnostic({ clienteId, execucaoId }: { clienteId: number; execucaoId: string }) {

  const [busy, setBusy] = useState(false);

  const [error, setError] = useState("");

  const request = useRef<AbortController | null>(null);

  useEffect(() => () => request.current?.abort(), [clienteId, execucaoId]);

  async function download() {

    request.current?.abort();

    const c = new AbortController(); request.current = c;

    setBusy(true); setError("");

    try {

      const q = new URLSearchParams({ clienteId: String(clienteId) });

      const response = await apiFetch(`${API_URL}/api/integracoes/conta-azul/automatica/execucoes/${execucaoId}/diagnostico-pendencias?${q}`, {

        credentials: "include", cache: "no-store", signal: c.signal,

      });

      if (!response.ok) throw new Error("Não foi possível preparar o diagnóstico das pendências.");

      const value = await response.json();

      if (c.signal.aborted) return;

      if (value.clienteId !== clienteId || value.execucaoId !== execucaoId) throw new Error("A empresa ou execução mudou. Consulte novamente.");

      const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: "application/json" }));

      const link = document.createElement("a"); link.href = url;

      link.download = `diagnostico-pendencias-${clienteId}-${execucaoId}.json`;

      link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);

    } catch (e) {

      if (!c.signal.aborted) setError(e instanceof Error ? e.message : "Falha no diagnóstico.");

    } finally { if (!c.signal.aborted) setBusy(false); }

  }

  return <div>

    <button type="button" disabled={busy} onClick={() => void download()}>{busy ? "Preparando diagnóstico…" : "Exportar diagnóstico das pendências"}</button>

    <p>Reavalia os dados arquivados desta execução. Não altera lançamentos nem resolve pendências.</p>

    {error && <p role="alert">{error}</p>}

  </div>;

}
