import { useEffect, useRef, useState } from "react";
import { API_URL } from "../config/api";
import { apiFetch } from "../config/http";
import { object, pendingReport } from "../services/accountantReview";
import { exportarCSV } from "../services/dashboardAnalysisService";

export default function ContaAzulPendingDiagnostic({ clienteId, execucaoId }: { clienteId: number; execucaoId: string }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), [clienteId, execucaoId]);
  async function download() {
    request.current?.abort();
    const c = new AbortController(); request.current = c;
    setBusy(true); setError(""); setNotice("");
    try {
      const q = new URLSearchParams({ clienteId: String(clienteId) });
      const response = await apiFetch(`${API_URL}/api/integracoes/conta-azul/automatica/execucoes/${execucaoId}/diagnostico-pendencias?${q}`, {
        credentials: "include", cache: "no-store", signal: c.signal,
      });
      if (!response.ok) throw new Error("Não foi possível preparar o relatório de pendências.");
      const value: unknown = await response.json();
      if (c.signal.aborted) return;
      const data = object(value);
      if (data.clienteId !== clienteId || data.execucaoId !== execucaoId) throw new Error("A empresa ou execução mudou. Consulte novamente.");
      const rows = pendingReport(value);
      if (rows.length === 1) { setNotice("Nenhuma pendência disponível nesta execução no momento da consulta."); return; }
      exportarCSV(`conferencia-pendencias-${clienteId}.csv`, rows);
      setNotice("Relatório preparado. Valores da parcela aparecem uma única vez; os pagamentos são detalhados nas linhas seguintes.");
    } catch (e) {
      if (!c.signal.aborted) setError(e instanceof Error ? e.message : "Falha ao preparar o relatório.");
    } finally { if (!c.signal.aborted) setBusy(false); }
  }
  return <div>
    <button type="button" disabled={busy} onClick={() => void download()}>{busy ? "Preparando relatório…" : "Exportar relatório de pendências (CSV)"}</button>
    <p>Descrições, pagamentos, diferenças e motivos para conferência. A consulta não altera lançamentos nem resolve pendências.</p>
    {notice && <p role="status">{notice}</p>}
    {error && <p role="alert">{error}</p>}
  </div>;
}
