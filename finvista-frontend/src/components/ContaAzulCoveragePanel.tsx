import "../styles/financial-integrity.css";
import { useEffect, useState, type FormEvent } from "react";
import { API_URL } from "../config/api";
import ContaAzulParcelDetailPanel from "./ContaAzulParcelDetailPanel";
type Parcela = {
  id: string;
  descricao: string;
  competencia: string | null;
  vencimento: string | null;
};
type Consulta = {
  clienteId: number;
  inicio: string;
  fim: string;
  vencimentoInicio: string;
  vencimentoFim: string;
  pagina: number;
  totalItens: number;
  tamanhoPagina: number;
  aviso: string;
  itens: {
    parcela: Parcela;
    vinculoApiLocal: boolean;
    competenciaInformada: boolean;
  }[];
};
type Filtro = {
  vencimentoInicio: string;
  vencimentoFim: string;
  inicio: string;
  fim: string;
  tipo: string;
  pagina: number;
};
export default function ContaAzulCoveragePanel({
  clienteId,
}: {
  clienteId: number;
}) {
  const [vencimentoInicio, setVencimentoInicio] = useState("");
  const [vencimentoFim, setVencimentoFim] = useState("");
  const [inicio, setInicio] = useState("");
  const [fim, setFim] = useState("");
  const [tipo, setTipo] = useState("DESPESA");
  const [filtro, setFiltro] = useState<Filtro | null>(null);
  const [dados, setDados] = useState<Consulta | null>(null);
  const [erro, setErro] = useState("");
  const [busy, setBusy] = useState(false);
  const [parcela, setParcela] = useState<string | null>(null);
  useEffect(() => {
    if (!filtro) return;
    const c = new AbortController();
    void (async () => {
      setBusy(true);
      setDados(null);
      try {
        const params = new URLSearchParams({
          ...filtro,
          pagina: String(filtro.pagina),
          clienteId: String(clienteId),
        });
        const r = await fetch(
          `${API_URL}/api/integracoes/conta-azul/cobertura-competencia?${params}`,
          { credentials: "include", cache: "no-store", signal: c.signal },
        );
        if (!r.ok)
          throw new Error(
            "Não foi possível consultar a cobertura. Confira a autorização, a sessão e um intervalo de até 366 dias.",
          );
        const d: Consulta = await r.json();
        if (d.clienteId !== clienteId)
          throw new Error("A empresa selecionada mudou.");
        if (!c.signal.aborted) {
          setDados(d);
          setErro("");
        }
      } catch (e) {
        if (!c.signal.aborted)
          setErro(e instanceof Error ? e.message : "Falha na consulta.");
      } finally {
        if (!c.signal.aborted) setBusy(false);
      }
    })();
    return () => c.abort();
  }, [filtro, clienteId]);
  function consultar(e: FormEvent) {
    e.preventDefault();
    if (
      !inicio ||
      !fim ||
      fim < inicio ||
      !vencimentoInicio ||
      !vencimentoFim ||
      vencimentoFim < vencimentoInicio
    ) {
      setErro(
        "Informe os intervalos de competência e vencimento em ordem válida.",
      );
      return;
    }
    setParcela(null);
    setFiltro({
      inicio,
      fim,
      vencimentoInicio,
      vencimentoFim,
      tipo,
      pagina: 1,
    });
  }
  return (
    <details className="ca-coverage">
      <summary>Conferir cobertura por competência</summary>
      <p>
        A API exige também uma janela de vencimentos. Informe os dois
        intervalos, de até 366 dias cada, e repita para outras janelas
        necessárias. Uma parcela da competência escolhida pode vencer fora do
        intervalo autorizado na automação.
      </p>
      <form onSubmit={consultar} className="fe-inputs">
        <label>
          Competência inicial
          <input
            required
            type="date"
            value={inicio}
            onChange={(e) => setInicio(e.target.value)}
          />
        </label>
        <label>
          Competência final
          <input
            required
            type="date"
            value={fim}
            onChange={(e) => setFim(e.target.value)}
          />
        </label>
        <label>
          Vencimento inicial da conferência
          <input
            required
            type="date"
            value={vencimentoInicio}
            onChange={(e) => setVencimentoInicio(e.target.value)}
          />
        </label>
        <label>
          Vencimento final da conferência
          <input
            required
            type="date"
            value={vencimentoFim}
            onChange={(e) => setVencimentoFim(e.target.value)}
          />
        </label>
        <label>
          Tipo
          <select value={tipo} onChange={(e) => setTipo(e.target.value)}>
            <option value="DESPESA">Despesas</option>
            <option value="RECEITA">Receitas</option>
          </select>
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Consultando…" : "Consultar por competência"}
        </button>
      </form>
      {erro && <p role="alert">{erro}</p>}
      {dados && (
        <>
          <p>
            Competência consultada: {dados.inicio} a {dados.fim}. Vencimentos
            consultados: {dados.vencimentoInicio} a {dados.vencimentoFim}.
          </p>
          <p role="status">
            {dados.totalItens} parcelas informadas na fonte; página{" "}
            {dados.pagina}. {dados.aviso}
          </p>
          <p>
            Sem vínculo API local não significa parcela nova: ela pode existir
            por Excel ou cadastro manual. Use a conferência individual antes de
            incluir.
          </p>
          <ul>
            {dados.itens.map((i) => (
              <li key={i.parcela.id}>
                <strong>{i.parcela.descricao || i.parcela.id}</strong> ·
                Competência:{" "}
                {i.parcela.competencia ||
                  "Não retornada na listagem; conferir detalhe"}{" "}
                · Vencimento: {i.parcela.vencimento || "Não informado"}
                <p>
                  {i.vinculoApiLocal
                    ? "Vínculo API local encontrado; confira valores e status."
                    : "Sem vínculo API local identificado; precisa de conferência."}
                </p>
                <button type="button" onClick={() => setParcela(i.parcela.id)}>
                  Conferir parcela e preparar sincronização
                </button>
              </li>
            ))}
          </ul>
          <button
            type="button"
            disabled={busy || dados.pagina <= 1}
            onClick={() =>
              setFiltro((f) => f && { ...f, pagina: f.pagina - 1 })
            }
          >
            Página anterior da cobertura
          </button>
          <button
            type="button"
            disabled={
              busy || dados.pagina * dados.tamanhoPagina >= dados.totalItens
            }
            onClick={() =>
              setFiltro((f) => f && { ...f, pagina: f.pagina + 1 })
            }
          >
            Próxima página da cobertura
          </button>
        </>
      )}
      {parcela && (
        <ContaAzulParcelDetailPanel
          key={`${clienteId}:${parcela}`}
          clienteId={clienteId}
          parcelaId={parcela}
          panelId={`cobertura-${parcela}`}
          fechar={() => setParcela(null)}
        />
      )}
    </details>
  );
}
