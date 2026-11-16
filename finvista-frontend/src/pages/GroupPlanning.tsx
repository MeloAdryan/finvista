import { useContext, useEffect, useState, type FormEvent } from "react";
import { API_URL } from "../config/api";
import { FinancialViewContext } from "../contexts/financialViewContext";
import { moedaOrcamento } from "../services/budgetAnalysis";
import type { Cliente } from "../services/clientService";
import {
  initialViewFilter,
  savedViewFilter,
  viewRange,
  type ViewLimit,
} from "../services/financialViewService";
import "../styles/dashboard.css";
import "../styles/budget-analysis.css";

type AberturaEmpresa = {
  empresas: Cliente[];
  onEmpresa: (empresa: Cliente) => Promise<void>;
};

type Orcamento = {
  empresaId: number;
  empresa: string;
  categoria: string | null;
  centro: string | null;
  limite: ViewLimit;
};

type Conferencia = {
  empresaId: number;
  empresa: string;
  situacao: string;
  movimentosApi: number;
  movimentosExtrato: number;
  divergencias: number;
  ambiguidades: number;
  somenteApi: number;
  somenteExtrato: number;
  pendenciasEmpresa: number | null;
};

function useConsultaGrupo<T>(pagina: string, params: string | null) {
  const [consulta, setConsulta] = useState<{
    params: string;
    dados?: T[];
    erro?: string;
  } | null>(null);
  const [tentativa, setTentativa] = useState(0);

  useEffect(() => {
    if (params === null) return;
    const controller = new AbortController();
    async function carregar(params: string) {
      try {
        const resposta = await fetch(
          `${API_URL}/api/analises/grupo/${pagina}?${params}`,
          {
            credentials: "include",
            cache: "no-store",
            signal: controller.signal,
          },
        );
        if (!resposta.ok) throw new Error();
        const dados = (await resposta.json()) as T[];
        if (!controller.signal.aborted) setConsulta({ params, dados });
      } catch {
        if (!controller.signal.aborted)
          setConsulta({
            params,
            erro: "Não foi possível consultar as empresas vinculadas.",
          });
      }
    }
    void carregar(params);
    return () => controller.abort();
  }, [pagina, params, tentativa]);

  const atual = consulta?.params === params ? consulta : null;
  return {
    dados: atual?.dados,
    erro: atual?.erro,
    carregando: params !== null && !atual,
    tentarNovamente: () => {
      setConsulta(null);
      setTentativa((v) => v + 1);
    },
  };
}

function AbrirEmpresa({
  empresas,
  onEmpresa,
  empresaId,
}: AberturaEmpresa & { empresaId: number }) {
  const empresa = empresas.find((item) => item.id === empresaId);
  if (!empresa) return null;
  return (
    <button
      type="button"
      aria-label={`Abrir ${empresa.nome}`}
      onClick={() => void onEmpresa(empresa)}
    >
      Abrir empresa
    </button>
  );
}

export function GroupBudgets({ empresas, onEmpresa }: AberturaEmpresa) {
  const filtro = useContext(FinancialViewContext);
  const params = new URLSearchParams();
  if (filtro) {
    const {
      criterio,
      periodo,
      situacao,
      referencia,
      inicio,
      fim,
      usuarioAtendido,
    } = filtro;
    for (const [name, value] of Object.entries({
      criterio,
      periodo,
      situacao,
      referencia,
      inicio,
      fim,
      usuarioAtendido,
    })) {
      if (value != null && value !== "") params.set(name, String(value));
    }
  }
  const consulta = useConsultaGrupo<Orcamento>(
    "orcamentos",
    filtro ? params.toString() : null,
  );
  return (
    <div className="dashboard">
      <section className="ba-filter">
        <h1>Orçamentos por empresa</h1>
        <p>
          O consumo de cada orçamento segue o período aplicado e os lançamentos
          da própria empresa.
        </p>
        {consulta.carregando && <p role="status">Carregando orçamentos…</p>}
        {consulta.erro && (
          <p role="alert">
            {consulta.erro}{" "}
            <button type="button" onClick={consulta.tentarNovamente}>
              Tentar novamente
            </button>
          </p>
        )}
        {consulta.dados?.length === 0 && <p>Nenhum orçamento no período.</p>}
        {!!consulta.dados?.length && (
          <div
            className="ba-table-scroll"
            role="region"
            aria-label="Orçamentos por empresa"
            tabIndex={0}
          >
            <table className="ba-table">
              <thead>
                <tr>
                  <th scope="col">Empresa</th>
                  <th scope="col">Orçamento</th>
                  <th scope="col">Planejado</th>
                  <th scope="col">Registrado</th>
                  <th scope="col">Excedente</th>
                  <th scope="col">Acesso</th>
                </tr>
              </thead>
              <tbody>
                {consulta.dados.map(
                  ({ empresa, empresaId, limite, categoria, centro }) => (
                    <tr key={`${empresaId}:${limite.id}`}>
                      <td>{empresa}</td>
                      <th scope="row">
                        {limite.nome}
                        <div>
                          {[categoria, centro].filter(Boolean).join(" · ")}
                        </div>
                        <small>
                          {dataBR(limite.inicio)} a {dataBR(limite.fim)}
                          {limite.recorteParcial ? " · recorte parcial" : ""}
                        </small>
                      </th>
                      <td>{moedaOrcamento(limite.planejado)}</td>
                      <td>{moedaOrcamento(limite.consumoPeriodo)}</td>
                      <td>
                        {moedaOrcamento(
                          Math.max(0, limite.consumoPeriodo - limite.planejado),
                        )}
                      </td>
                      <td>
                        <AbrirEmpresa
                          empresas={empresas}
                          onEmpresa={onEmpresa}
                          empresaId={empresaId}
                        />
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}

function dataBR(data: string) {
  return data.split("-").reverse().join("/");
}

function situacaoConferencia(situacao: string) {
  if (situacao === "SEM_DADOS_COMPARAVEIS") return "Sem dados comparáveis";
  if (situacao.startsWith("DIVERGENCIA")) return "Com divergências";
  if (situacao === "TOTAIS_IGUAIS_VINCULOS_NAO_CERTIFICADOS")
    return "Totais iguais, vínculos a conferir";
  return "Vínculos conferidos, cobertura não certificada";
}

export function GroupConference({
  empresas,
  onEmpresa,
  clienteId,
  usuarioAtendido,
}: AberturaEmpresa & { clienteId?: number; usuarioAtendido?: number }) {
  const [periodo, setPeriodo] = useState(
    () =>
      viewRange(savedViewFilter(clienteId)) ?? viewRange(initialViewFilter())!,
  );
  const [draft, setDraft] = useState(periodo);
  const params = new URLSearchParams(periodo);
  if (usuarioAtendido != null)
    params.set("usuarioAtendido", String(usuarioAtendido));
  const consulta = useConsultaGrupo<Conferencia>(
    "conferencia",
    params.toString(),
  );
  function aplicar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPeriodo(draft);
  }
  return (
    <div className="dashboard">
      <section className="ba-filter">
        <h1>Conferência financeira por empresa</h1>
        <p>
          Compare as baixas da API com os extratos de cada empresa. Abra uma
          empresa para analisar e conciliar os lançamentos.
        </p>
        <form className="ba-filter-grid" onSubmit={aplicar}>
          <label>
            Data inicial{" "}
            <input
              type="date"
              required
              value={draft.inicio}
              max={draft.fim}
              onChange={(event) =>
                setDraft({ ...draft, inicio: event.target.value })
              }
            />
          </label>
          <label>
            Data final{" "}
            <input
              type="date"
              required
              value={draft.fim}
              min={draft.inicio}
              onChange={(event) =>
                setDraft({ ...draft, fim: event.target.value })
              }
            />
          </label>
          <button type="submit">Aplicar período</button>
        </form>
        <p>
          Período aplicado {dataBR(periodo.inicio)} a {dataBR(periodo.fim)}.
          Pendências da sincronização consideram toda a empresa.
        </p>
        {consulta.carregando && <p role="status">Carregando conferência…</p>}
        {consulta.erro && (
          <p role="alert">
            {consulta.erro}{" "}
            <button type="button" onClick={consulta.tentarNovamente}>
              Tentar novamente
            </button>
          </p>
        )}
        {consulta.dados && (
          <div
            className="ba-table-scroll"
            role="region"
            aria-label="Conferência por empresa"
            tabIndex={0}
          >
            <table className="ba-table">
              <thead>
                <tr>
                  <th scope="col">Empresa</th>
                  <th scope="col">Situação</th>
                  <th scope="col">API / Extrato</th>
                  <th scope="col">Divergências / Ambiguidades</th>
                  <th scope="col">Só API / Só extrato</th>
                  <th scope="col">Pendências da sincronização</th>
                  <th scope="col">Acesso</th>
                </tr>
              </thead>
              <tbody>
                {consulta.dados.map((item) => (
                  <tr key={item.empresaId}>
                    <th scope="row">{item.empresa}</th>
                    <td>{situacaoConferencia(item.situacao)}</td>
                    <td>
                      {item.movimentosApi} / {item.movimentosExtrato}
                    </td>
                    <td>
                      {item.divergencias} / {item.ambiguidades}
                    </td>
                    <td>
                      {item.somenteApi} / {item.somenteExtrato}
                    </td>
                    <td>{item.pendenciasEmpresa ?? "—"}</td>
                    <td>
                      <AbrirEmpresa
                        empresas={empresas}
                        onEmpresa={onEmpresa}
                        empresaId={item.empresaId}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  );
}
