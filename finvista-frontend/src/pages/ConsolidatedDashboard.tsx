import { useEffect, useState, type FormEvent } from "react";
import { API_URL } from "../config/api";
import { apiFetch } from "../config/http";
import {
  criterionNames,
  todayInBrazil,
} from "../services/financialViewService";
import "../styles/financial-evolution.css";

type Totais = { receita: number; despesa: number; resultado: number };
type Consolidado = {
  totais: Totais;
  empresas: { id: number; nome: string; totais: Totais }[];
};

const moeda = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
});

export default function ConsolidatedDashboard({
  usuarioId,
}: {
  usuarioId?: number;
}) {
  const [filtro, setFiltro] = useState(() => {
    const hoje = todayInBrazil();
    return {
      inicio: hoje.slice(0, 7) + "-01",
      fim: hoje,
      criterio: "COMPETENCIA",
    };
  });
  const [consulta, setConsulta] = useState(filtro);
  const [dados, setDados] = useState<Consolidado | null>(null);
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(true);

  useEffect(() => {
    const controlador = new AbortController();
    async function carregar() {
      try {
        const resposta = await apiFetch(
          `${API_URL}/api/analises/financeira/consolidado?${new URLSearchParams({ ...consulta, ...(usuarioId == null ? {} : { usuarioId: String(usuarioId) }) })}`,
          {
            credentials: "include",
            cache: "no-store",
            signal: controlador.signal,
          },
        );
        if (!resposta.ok)
          throw new Error("Não foi possível consultar as empresas.");
        const resultado: Consolidado = await resposta.json();
        if (!controlador.signal.aborted) setDados(resultado);
      } catch {
        if (!controlador.signal.aborted)
          setErro(
            "Não foi possível carregar o consolidado. Aplique os filtros para tentar novamente.",
          );
      } finally {
        if (!controlador.signal.aborted) setCarregando(false);
      }
    }
    void carregar();
    return () => controlador.abort();
  }, [consulta, usuarioId]);

  function aplicar(evento: FormEvent) {
    evento.preventDefault();
    setErro("");
    setDados(null);
    setCarregando(true);
    setConsulta({ ...filtro });
  }

  const porBaixa =
    consulta.criterio === "BAIXA" || consulta.criterio === "BAIXA_EXTRATO";
  const receita = porBaixa ? "Recebimentos" : "Receitas";
  const despesa = porBaixa ? "Pagamentos" : "Despesas";

  return (
    <div className="dashboard fe-page">
      <section className="fe-card">
        <h1>Todas as empresas</h1>
        <p>
          Visão de consulta das empresas autorizadas. Os totais somam os valores
          registrados em cada empresa, sem eliminação contábil de operações
          entre empresas.
        </p>
        <form className="fe-inputs" onSubmit={aplicar}>
          <label>
            Critério
            <select
              value={filtro.criterio}
              onChange={(e) =>
                setFiltro({ ...filtro, criterio: e.target.value })
              }
            >
              {Object.entries(criterionNames).map(([valor, nome]) => (
                <option key={valor} value={valor}>
                  {nome}
                </option>
              ))}
            </select>
          </label>
          <label>
            Data inicial
            <input
              type="date"
              required
              value={filtro.inicio}
              max={filtro.fim}
              onChange={(e) => setFiltro({ ...filtro, inicio: e.target.value })}
            />
          </label>
          <label>
            Data final
            <input
              type="date"
              required
              value={filtro.fim}
              min={filtro.inicio}
              onChange={(e) => setFiltro({ ...filtro, fim: e.target.value })}
            />
          </label>
          <button type="submit" disabled={carregando}>
            Aplicar filtros
          </button>
        </form>
        {carregando && <p role="status">Consultando empresas…</p>}
        {erro && <p role="alert">{erro}</p>}
      </section>
      {dados && (
        <section className="fe-card">
          <h2>Resultado por empresa</h2>
          <p>{dados.empresas.length} empresas no recorte.</p>
          <div
            className="fe-table"
            tabIndex={0}
            role="region"
            aria-label="Valores por empresa"
          >
            <table>
              <thead>
                <tr>
                  <th scope="col">Empresa</th>
                  <th scope="col">{receita}</th>
                  <th scope="col">{despesa}</th>
                  <th scope="col">Resultado</th>
                </tr>
              </thead>
              <tbody>
                {dados.empresas.map(({ id, nome, totais }) => (
                  <tr key={id}>
                    <th scope="row">{nome}</th>
                    <td>{moeda.format(totais.receita)}</td>
                    <td>{moeda.format(totais.despesa)}</td>
                    <td>{moeda.format(totais.resultado)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <th scope="row">Total</th>
                  <td>{moeda.format(dados.totais.receita)}</td>
                  <td>{moeda.format(dados.totais.despesa)}</td>
                  <td>{moeda.format(dados.totais.resultado)}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
