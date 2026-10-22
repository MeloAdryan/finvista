import "../styles/financial-integrity.css";
import { useEffect, useState } from "react";
import { API_URL } from "../config/api";
import type { AutoRun, AutoStatus } from "../services/contaAzulAutoService";

type Historico = { execucoes: AutoRun[]; total: number; pagina: number };
type Revisao = {
  execucao: AutoRun;
  pendencias: AutoStatus["pendencias"];
  total: number;
  pagina: number;
};
const horario = (v: string) => new Date(v).toLocaleString("pt-BR");
export default function ContaAzulRunReview({
  clienteId,
  onSelect,
}: {
  clienteId: number;
  onSelect: (id: string) => void;
}) {
  const [aberto, setAberto] = useState(false);
  const [historico, setHistorico] = useState<Historico | null>(null);
  const [revisao, setRevisao] = useState<Revisao | null>(null);
  const [paginaHistorico, setPaginaHistorico] = useState(1);
  const [execucao, setExecucao] = useState("");
  const [pagina, setPagina] = useState(1);
  const [refresh, setRefresh] = useState(0);
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(false);
  useEffect(() => {
    if (!aberto) return;
    const c = new AbortController();
    void (async () => {
      try {
        const r = await fetch(
          `${API_URL}/api/integracoes/conta-azul/automatica/execucoes?clienteId=${clienteId}&pagina=${paginaHistorico}`,
          { credentials: "include", cache: "no-store", signal: c.signal },
        );
        if (!r.ok)
          throw new Error(
            "Não foi possível consultar as execuções anteriores. Confira a sessão e a versão do backend.",
          );
        const d: Historico = await r.json();
        if (!c.signal.aborted) {
          setHistorico(d);
          setExecucao("");
          setRevisao(null);
          setPagina(1);
          setErro("");
        }
      } catch (e) {
        if (!c.signal.aborted)
          setErro(
            e instanceof Error ? e.message : "Falha ao consultar histórico.",
          );
      }
    })();
    return () => c.abort();
  }, [aberto, clienteId, paginaHistorico, refresh]);
  useEffect(() => {
    if (!aberto || !execucao) return;
    const c = new AbortController();
    void (async () => {
      setCarregando(true);
      setRevisao(null);
      try {
        const r = await fetch(
          `${API_URL}/api/integracoes/conta-azul/automatica/execucoes/${execucao}/pendencias?clienteId=${clienteId}&pagina=${pagina}`,
          { credentials: "include", cache: "no-store", signal: c.signal },
        );
        if (!r.ok)
          throw new Error(
            "Não foi possível abrir as pendências desta execução.",
          );
        const d: Revisao = await r.json();
        if (d.execucao.id !== execucao)
          throw new Error("A execução selecionada mudou.");
        if (!c.signal.aborted) {
          setRevisao(d);
          setErro("");
        }
      } catch (e) {
        if (!c.signal.aborted)
          setErro(
            e instanceof Error ? e.message : "Falha ao consultar pendências.",
          );
      } finally {
        if (!c.signal.aborted) setCarregando(false);
      }
    })();
    return () => c.abort();
  }, [aberto, clienteId, execucao, pagina]);
  return (
    <section
      className="ca-run-review"
      aria-label="Pendências de execuções anteriores"
    >
      <h3>Pendências de todas as execuções</h3>
      <p>
        Uma nova rodada não resolve as ocorrências anteriores. Selecione a
        execução para conferir seus casos.
      </p>
      <button
        type="button"
        aria-expanded={aberto}
        onClick={() => setAberto((v) => !v)}
      >
        {aberto
          ? "Fechar revisão do histórico"
          : "Revisar execuções anteriores"}
      </button>
      {aberto && (
        <>
          <button type="button" onClick={() => setRefresh((v) => v + 1)}>
            Atualizar lista de execuções
          </button>
          {erro && <p role="alert">{erro}</p>}
          {historico && (
            <>
              <label>
                Execução a revisar{" "}
                <select
                  value={execucao}
                  onChange={(e) => {
                    setExecucao(e.target.value);
                    setPagina(1);
                    setRevisao(null);
                  }}
                >
                  <option value="">Selecione uma execução</option>
                  {historico.execucoes.map((r) => (
                    <option key={r.id} value={r.id}>
                      {horario(r.criadoEm)} · {r.pendentes} ocorrências para
                      conferir · {r.status}
                    </option>
                  ))}
                </select>
              </label>
              <div className="ca-auto-actions">
                <button
                  type="button"
                  disabled={paginaHistorico <= 1}
                  onClick={() => setPaginaHistorico((v) => v - 1)}
                >
                  Execuções mais recentes
                </button>
                <span>
                  Página {paginaHistorico} · {historico.total} execuções
                </span>
                <button
                  type="button"
                  disabled={paginaHistorico * 20 >= historico.total}
                  onClick={() => setPaginaHistorico((v) => v + 1)}
                >
                  Execuções mais antigas
                </button>
              </div>
            </>
          )}
          {carregando && <p role="status">Consultando pendências…</p>}
          {revisao && (
            <>
              <p>
                Ocorrências registradas em {horario(revisao.execucao.criadoEm)}:{" "}
                {revisao.total}. Este histórico não é um contador de pendências
                atuais: abra a parcela para consultar a situação de agora.
              </p>
              {revisao.total === 0 && (
                <p>Nenhuma ocorrência para conferir nesta execução.</p>
              )}
              <ul>
                {revisao.pendencias.map((p) => (
                  <li key={p.parcelaId}>
                    <strong>{p.descricao || p.parcelaId}</strong>
                    <p>{p.mensagem}</p>
                    <button type="button" onClick={() => onSelect(p.parcelaId)}>
                      Conferir situação atual da parcela
                    </button>
                  </li>
                ))}
              </ul>
              <div className="ca-auto-actions">
                <button
                  type="button"
                  disabled={pagina <= 1}
                  onClick={() => setPagina((v) => v - 1)}
                >
                  Pendências anteriores da execução
                </button>
                <span>Página {pagina}</span>
                <button
                  type="button"
                  disabled={pagina * 20 >= revisao.total}
                  onClick={() => setPagina((v) => v + 1)}
                >
                  Próximas pendências da execução
                </button>
              </div>
            </>
          )}
        </>
      )}
    </section>
  );
}
