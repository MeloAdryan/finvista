import { useEffect, useState } from "react";
import { API_URL } from "../config/api";
import { apiFetch } from "../config/http";
type Tipo = "PARCELA" | "TRANSFERENCIA" | "SALDO_INICIAL";
type Fonte = {
  tipo: string;
  chave: string;
  capturadoEm: string;
  dados: unknown;
};
type Resposta = {
  clienteId: number;
  execucaoId: string;
  status: string;
  registros: Fonte[];
  total: number;
  pagina: number;
};
export default function ContaAzulSourceReview({
  clienteId,
  execucaoId,
}: {
  clienteId: number;
  execucaoId: string;
}) {
  const [aberto, setAberto] = useState(false),
    [tipo, setTipo] = useState<Tipo>("PARCELA"),
    [pagina, setPagina] = useState(1);
  const [dados, setDados] = useState<Resposta | null>(null),
    [erro, setErro] = useState("");
  useEffect(() => {
    if (!aberto) return;
    const c = new AbortController();
    void (async () => {
      try {
        const q = new URLSearchParams({
          clienteId: String(clienteId),
          tipo,
          pagina: String(pagina),
        });
        const r = await apiFetch(
          `${API_URL}/api/integracoes/conta-azul/automatica/execucoes/${execucaoId}/fontes?${q}`,
          { credentials: "include", cache: "no-store", signal: c.signal },
        );
        if (!r.ok)
          throw new Error(
            "Não foi possível consultar as movimentações preservadas.",
          );
        const value: Resposta = await r.json();
        if (value.clienteId !== clienteId || value.execucaoId !== execucaoId)
          throw new Error("A empresa ou execução mudou. Atualize a página.");
        if (!c.signal.aborted) setDados(value);
      } catch (e) {
        if (!c.signal.aborted)
          setErro(e instanceof Error ? e.message : "Falha na consulta.");
      }
    })();
    return () => c.abort();
  }, [aberto, clienteId, execucaoId, tipo, pagina]);
  return (
    <details
      onToggle={(e) => {
        if (e.target !== e.currentTarget) return;
        setDados(null);
        setErro("");
        setAberto(e.currentTarget.open);
      }}
    >
      <summary>Conferir dados financeiros sincronizados</summary>
      <p>
        Fontes preservadas desta execução. Execução concluída não certifica
        conciliação com um relatório externo. Dados de uma execução em andamento
        podem estar incompletos.
      </p>
      <label>
        Tipo de informação{" "}
        <select
          value={tipo}
          onChange={(e) => {
            setDados(null);
            setErro("");
            setTipo(e.target.value as Tipo);
            setPagina(1);
          }}
        >
          <option value="PARCELA">Parcelas e respectivas baixas</option>
          <option value="TRANSFERENCIA">Transferências entre contas</option>
          <option value="SALDO_INICIAL">Saldos iniciais</option>
        </select>
      </label>
      {erro && <p role="alert">{erro}</p>}
      {!dados && !erro && aberto && <p role="status">Consultando…</p>}
      {dados && (
        <>
          <p>
            Execução: {dados.status} · {dados.total} registros de captura. Uma
            captura de transferências ou saldos iniciais pode conter uma página
            com vários movimentos.
          </p>
          {dados.registros.map((r) => (
            <details key={`${r.tipo}:${r.chave}`}>
              <summary>
                {r.tipo} · {r.chave} ·{" "}
                {new Date(r.capturadoEm).toLocaleString("pt-BR")}
              </summary>
              <pre
                style={{
                  whiteSpace: "pre-wrap",
                  overflowWrap: "anywhere",
                  maxHeight: 400,
                  overflow: "auto",
                }}
              >
                {JSON.stringify(r.dados, null, 2)}
              </pre>
            </details>
          ))}
          {dados.total === 0 && (
            <p>Nenhuma captura deste tipo nesta execução.</p>
          )}
          <button
            type="button"
            disabled={pagina === 1}
            onClick={() => {
              setDados(null);
              setErro("");
              setPagina((p) => p - 1);
            }}
          >
            Anterior
          </button>
          <span> Página {pagina} </span>
          <button
            type="button"
            disabled={pagina * 5 >= dados.total}
            onClick={() => {
              setDados(null);
              setErro("");
              setPagina((p) => p + 1);
            }}
          >
            Próxima
          </button>
        </>
      )}
    </details>
  );
}
