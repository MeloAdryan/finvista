import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import ContaAzulReconciliationPanel from "../components/ContaAzulReconciliationPanel";
import FinancialRecordDialog from "../components/FinancialRecordDialog";
import {
  managementGet,
  saveDre,
  money,
  dateLabel,
  dreGroups,
  type Health,
  type Aging,
  type Forecast,
  type Dre,
  type DreGroup,
  type OpenItem,
} from "../services/financialManagementService";
import {
  getFinancialView,
  initialViewFilter,
  criterionNames,
  type FinancialView,
  type ViewCriterion,
} from "../services/financialViewService";
import "../styles/financial-management.css";
const bands: Record<string, string> = {
  HOJE: "Vence hoje",
  DIAS_1_30: "1–30 dias",
  DIAS_31_60: "31–60 dias",
  DIAS_61_90: "61–90 dias",
  MAIS_90: "Mais de 90 dias",
  A_VENCER: "A vencer",
  SEM_SALDO: "Saldo não informado",
  SEM_VENCIMENTO: "Sem vencimento",
};
const tabs = [
  "Saúde dos dados",
  "Conciliação",
  "Atrasos",
  "Detalhamento",
  "Realizado × previsto",
  "DRE gerencial",
] as const;
type Tab = (typeof tabs)[number];
function ErrorText({ text }: { text: string }) {
  return text ? (
    <p role="alert" className="fv-management-error">
      {text}
    </p>
  ) : null;
}
function OpenRows({
  rows,
  onDetail,
}: {
  rows: OpenItem[];
  onDetail: (id: number) => void;
}) {
  return (
    <div className="fv-management-scroll">
      <table>
        <thead>
          <tr>
            <th>Registro</th>
            <th>Tipo</th>
            <th>Vencimento</th>
            <th>Faixa</th>
            <th>Líquido aberto</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td>
                <button type="button" onClick={() => onDetail(r.id)}>
                  {r.descricao || `Registro ${r.id}`}
                </button>
              </td>
              <td>{r.tipo}</td>
              <td>{dateLabel(r.vencimento)}</td>
              <td>{bands[r.faixa]}</td>
              <td>{money(r.aberto)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {!rows.length && <p>Sem registros nesta seleção.</p>}
    </div>
  );
}
export default function FinancialControlCenter({
  clienteId,
  admin,
}: {
  clienteId?: number;
  admin: boolean;
}) {
  const [tab, setTab] = useState<Tab>("Saúde dos dados");
  if (!clienteId)
    return (
      <p>Selecione uma empresa para consultar a conferência financeira.</p>
    );
  return (
    <section className="fv-management">
      <header>
        <div>
          <h1>Conferência financeira</h1>
          <p>
            Entenda a origem dos valores e os registros que precisam de atenção.
          </p>
        </div>
      </header>
      <nav aria-label="Áreas de conferência">
        {tabs.map((t) => (
          <button
            type="button"
            key={t}
            aria-current={tab === t ? "page" : undefined}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </nav>
      <div key={`${clienteId}-${tab}`} className="fv-management-panel">
        {tab === "Saúde dos dados" ? (
          <HealthPanel clienteId={clienteId} />
        ) : tab === "Conciliação" ? (
          <ContaAzulReconciliationPanel
            clienteId={clienteId}
            endpoint="/api/gestao-financeira/conferencia"
            initiallyOpen
          />
        ) : tab === "Atrasos" ? (
          <AgingPanel clienteId={clienteId} />
        ) : (
          <PeriodPanel clienteId={clienteId} mode={tab} admin={admin} />
        )}
      </div>
    </section>
  );
}
function HealthPanel({ clienteId }: { clienteId: number }) {
  const [data, setData] = useState<Health | null>(null),
    [error, setError] = useState(""),
    [revision, setRevision] = useState(0);
  const [busy, setBusy] = useState(true);
  useEffect(() => {
    const c = new AbortController();
    managementGet<Health>("saude", clienteId, {}, c.signal)
      .then((d) => {
        if (!c.signal.aborted) setData(d);
      })
      .catch((e: unknown) => {
        if (!c.signal.aborted)
          setError(e instanceof Error ? e.message : "Falha ao consultar.");
      })
      .finally(() => {
        if (!c.signal.aborted) setBusy(false);
      });
    return () => c.abort();
  }, [clienteId, revision]);
  return (
    <>
      <header>
        <h2>Saúde da empresa inteira</h2>
        <button
          type="button"
          disabled={busy}
          onClick={() => {
            setError("");
            setData(null);
            setBusy(true);
            setRevision((v) => v + 1);
          }}
        >
          Atualizar
        </button>
      </header>
      <ErrorText text={error} />
      {busy && <p role="status">Consultando…</p>}
      {data && (
        <>
          <div className="fv-management-cards">
            <article>
              <h3>Última varredura concluída</h3>
              <strong>
                {data.ultimaConcluida
                  ? new Date(data.ultimaConcluida).toLocaleString("pt-BR")
                  : "Nenhuma registrada"}
              </strong>
              <p>
                {data.atualizacaoAtrasada
                  ? "Requer verificação de atualização"
                  : "Dentro do intervalo esperado"}
              </p>
              <p>Última execução: {data.ultimaSituacao.replaceAll("_", " ")}</p>
            </article>
            <article>
              <h3>Pendências ainda abertas</h3>
              <strong>{data.totalPendencias}</strong>
              <p>Continuam abertas até a parcela ser conferida novamente.</p>
            </article>
            <article>
              <h3>Valores cadastrados associados</h3>
              <strong>{money(data.liquidoVinculado)}</strong>
              <p>
                Principal sem composição líquida:{" "}
                {money(data.principalSemLiquido)}
              </p>
              <p>{data.pendenciasSemValor} pendências sem valor conhecido.</p>
            </article>
          </div>
          <p>
            Sincronização no servidor{" "}
            {data.processadorHabilitado ? "habilitado" : "desabilitado"} ·
            Automação {data.automacaoAtiva ? "ativa" : "inativa"} · Escopo{" "}
            {data.sincronizacaoAmpla ? "amplo" : "limitado pela configuração"}
          </p>
          <p>
            {data.semSaldoAberto} registros sem saldo líquido aberto ·{" "}
            {data.semVencimento} sem vencimento.
          </p>
          <h3>Cobertura observada</h3>
          <div className="fv-management-scroll">
            <table>
              <thead>
                <tr>
                  <th>Fonte</th>
                  <th>Registros</th>
                  <th>Primeira data</th>
                  <th>Última data</th>
                  <th>Atualização</th>
                </tr>
              </thead>
              <tbody>
                {data.fontes.map((f) => (
                  <tr key={f.fonte}>
                    <td>{f.fonte}</td>
                    <td>{f.registros}</td>
                    <td>{dateLabel(f.primeiraData)}</td>
                    <td>{dateLabel(f.ultimaData)}</td>
                    <td>
                      {f.atualizadoEm
                        ? new Date(f.atualizadoEm).toLocaleString("pt-BR")
                        : "Não informada"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <details>
            <summary>Pendências por parcela ({data.totalPendencias})</summary>
            <div className="fv-management-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Parcela</th>
                    <th>Descrição</th>
                    <th>Motivo</th>
                    <th>Líquido associado</th>
                    <th>Principal associado</th>
                  </tr>
                </thead>
                <tbody>
                  {data.pendencias.map((p, i) => (
                    <tr key={`${p.parcelaId}-${i}`}>
                      <td>{p.parcelaId}</td>
                      <td>{p.descricao}</td>
                      <td>{p.motivo}</td>
                      <td>{money(p.liquidoAssociado)}</td>
                      <td>{money(p.principalAssociado)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
          <p className="fv-management-notice">{data.aviso}</p>
        </>
      )}
    </>
  );
}
function AgingPanel({ clienteId }: { clienteId: number }) {
  const [data, setData] = useState<Aging | null>(null),
    [error, setError] = useState(""),
    [tipo, setTipo] = useState("DESPESA"),
    [faixa, setFaixa] = useState(""),
    [page, setPage] = useState(0),
    [selected, setSelected] = useState<number | null>(null);
  const [busy, setBusy] = useState(true);
  useEffect(() => {
    const c = new AbortController();
    managementGet<Aging>(
      "atrasos",
      clienteId,
      {
        tipo,
        ...(faixa ? { faixa } : {}),
        pagina: String(page),
        tamanho: "20",
      },
      c.signal,
    )
      .then((d) => {
        if (!c.signal.aborted) setData(d);
      })
      .catch((e: unknown) => {
        if (!c.signal.aborted)
          setError(e instanceof Error ? e.message : "Falha ao consultar.");
      })
      .finally(() => {
        if (!c.signal.aborted) setBusy(false);
      });
    return () => c.abort();
  }, [clienteId, tipo, faixa, page]);
  function change(t: string, f: string, p = 0) {
    if (t === tipo && f === faixa && p === page) return;
    setData(null);
    setError("");
    setBusy(true);
    setTipo(t);
    setFaixa(f);
    setPage(p);
  }
  return (
    <>
      <h2>Recebíveis e pagamentos por atraso</h2>
      <p>
        Posição atual · todos os vencimentos
        {data ? ` · ${dateLabel(data.referencia)}` : ""}
      </p>
      <label>
        Tipo
        <select value={tipo} onChange={(e) => change(e.target.value, faixa)}>
          <option value="DESPESA">A pagar</option>
          <option value="RECEITA">A receber</option>
        </select>
      </label>
      <ErrorText text={error} />
      {busy && <p role="status">Consultando…</p>}
      {data && (
        <>
          <div className="fv-management-bands">
            <button
              type="button"
              aria-pressed={!faixa}
              onClick={() => change(tipo, "")}
            >
              Todas as faixas
            </button>
            {data.grupos
              .filter((g) => g.tipo === tipo)
              .map((g) => (
                <button
                  type="button"
                  aria-pressed={faixa === g.faixa}
                  key={g.faixa}
                  onClick={() => change(tipo, g.faixa)}
                >
                  <span>{bands[g.faixa]}</span>
                  <strong>
                    {g.quantidade > 0 && g.semValor === g.quantidade
                      ? "Não informado"
                      : money(g.valorConhecido)}
                  </strong>
                  <small>
                    {g.quantidade} registros
                    {g.semValor ? ` · ${g.semValor} sem valor` : ""}
                  </small>
                </button>
              ))}
          </div>
          <OpenRows rows={data.itens} onDetail={setSelected} />
          <p>
            {data.total} registros · Página {page + 1}
          </p>
          <button
            type="button"
            disabled={!page}
            onClick={() => change(tipo, faixa, page - 1)}
          >
            Anterior
          </button>
          <button
            type="button"
            disabled={(page + 1) * data.tamanho >= data.total}
            onClick={() => change(tipo, faixa, page + 1)}
          >
            Próxima
          </button>
          <p className="fv-management-notice">{data.aviso}</p>
        </>
      )}
      {selected !== null && (
        <FinancialRecordDialog
          clienteId={clienteId}
          params={{ id: String(selected), posicao: "true" }}
          onClose={() => setSelected(null)}
        />
      )}
    </>
  );
}
function PeriodPanel({
  clienteId,
  mode,
  admin,
}: {
  clienteId: number;
  mode: Exclude<Tab, "Saúde dos dados" | "Conciliação" | "Atrasos">;
  admin: boolean;
}) {
  const initial = initialViewFilter();
  const [start, setStart] = useState(
      initial.inicio || initial.referencia.slice(0, 7) + "-01",
    ),
    [end, setEnd] = useState(initial.fim || initial.referencia),
    [criterion, setCriterion] = useState<ViewCriterion>("BAIXA");
  const [data, setData] = useState<FinancialView | Forecast | Dre | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [detail, setDetail] = useState<Record<string, string> | null>(null),
    [type, setType] = useState(""),
    [page, setPage] = useState(0);
  const controller = useRef<AbortController | null>(null);
  useEffect(() => () => controller.current?.abort(), []);
  function invalidate() {
    controller.current?.abort();
    setData(null);
    setBusy(false);
    setError("");
    setDetail(null);
    setPage(0);
  }
  async function load(e?: FormEvent) {
    e?.preventDefault();
    if (!start || !end || start > end) {
      setError("Informe um período válido.");
      return;
    }
    controller.current?.abort();
    const c = new AbortController();
    controller.current = c;
    setBusy(true);
    setData(null);
    setError("");
    setPage(0);
    try {
      let value: FinancialView | Forecast | Dre;
      if (mode === "Detalhamento")
        value = await getFinancialView(
          {
            ...initial,
            clienteId,
            criterio: criterion,
            periodo: "PERSONALIZADO",
            situacao: "TODAS",
            inicio: start,
            fim: end,
            categoria: "",
            centro: "",
          },
          c.signal,
        );
      else
        value =
          mode === "DRE gerencial"
            ? await managementGet<Dre>(
                "dre",
                clienteId,
                { inicio: start, fim: end },
                c.signal,
              )
            : await managementGet<Forecast>(
                "realizado-previsto",
                clienteId,
                { inicio: start, fim: end, fonte: criterion },
                c.signal,
              );
      if (value.clienteId !== clienteId)
        throw new Error("A empresa mudou. Consulte novamente.");
      if (!c.signal.aborted) setData(value);
    } catch (e) {
      if (!c.signal.aborted)
        setError(e instanceof Error ? e.message : "Falha ao consultar.");
    } finally {
      if (!c.signal.aborted) setBusy(false);
    }
  }
  const view = data && "linhas" in data ? data : null;
  const forecast = data && "compromissos" in data ? data : null;
  const dre = data && "demonstrativo" in data ? data : null;
  const groups = new Map<
    string,
    {
      id: number;
      movimentoId?: string | null;
      data: string;
      descricao: string;
      tipo: string;
      valor: number;
    }
  >();
  for (const l of view?.linhas ?? []) {
    if (type && l.tipo !== type) continue;
    const k = `${l.id}:${l.movimentoId ?? ""}:${l.data}`;
    const r = groups.get(k);
    if (r) r.valor += Math.round(l.valor * 100);
    else groups.set(k, { ...l, valor: Math.round(l.valor * 100) });
  }
  const rows = [...groups.values()];
  return (
    <>
      <h2>{mode}</h2>
      <p>
        {mode === "DRE gerencial"
          ? "Prévia por competência; depende da revisão do mapeamento."
          : "Escolha o período e a fonte. API e extrato são alternativas, não valores para somar."}
      </p>
      <form onSubmit={(e) => void load(e)}>
        <label>
          Data inicial
          <input
            type="date"
            required
            value={start}
            onChange={(e) => {
              invalidate();
              setStart(e.target.value);
            }}
          />
        </label>
        <label>
          Data final
          <input
            type="date"
            required
            min={start}
            value={end}
            onChange={(e) => {
              invalidate();
              setEnd(e.target.value);
            }}
          />
        </label>
        {mode !== "DRE gerencial" && (
          <label>
            Critério / fonte
            <select
              value={criterion}
              onChange={(e) => {
                invalidate();
                setCriterion(e.target.value as ViewCriterion);
              }}
            >
              {Object.entries(criterionNames)
                .filter(
                  ([k]) =>
                    mode === "Detalhamento" ||
                    k === "BAIXA" ||
                    k === "BAIXA_EXTRATO",
                )
                .map(([k, v]) => (
                  <option key={k} value={k}>
                    {v}
                  </option>
                ))}
            </select>
          </label>
        )}
        <button type="submit" disabled={busy}>
          {busy ? "Consultando…" : "Consultar período"}
        </button>
      </form>
      <ErrorText text={error} />
      {view && (
        <>
          <div className="fv-management-cards">
            {(["RECEITA", "DESPESA", ""] as const).map((t) => (
              <button
                type="button"
                key={t}
                aria-pressed={type === t}
                onClick={() => {
                  setType(t);
                  setPage(0);
                }}
              >
                <span>
                  {t === "RECEITA"
                    ? "Receitas"
                    : t === "DESPESA"
                      ? "Despesas"
                      : "Resultado / todos os registros"}
                </span>
                <strong>
                  {money(
                    t === "RECEITA"
                      ? view.totais.receita
                      : t === "DESPESA"
                        ? view.totais.despesa
                        : view.totais.resultado,
                  )}
                </strong>
              </button>
            ))}
          </div>
          <p>
            Clique em um indicador e depois em um registro para conferir
            parcelas, baixas e rateios. Sem registros não significa receita ou
            despesa confirmada como zero.
          </p>
          <div className="fv-management-scroll">
            <table>
              <thead>
                <tr>
                  <th>Registro</th>
                  <th>Data usada</th>
                  <th>Tipo</th>
                  <th>Valor no recorte</th>
                </tr>
              </thead>
              <tbody>
                {rows.slice(page * 20, page * 20 + 20).map((r) => (
                  <tr key={`${r.id}-${r.movimentoId}-${r.data}`}>
                    <td>
                      <button
                        type="button"
                        onClick={() =>
                          setDetail({
                            id: String(r.id),
                            ...(r.movimentoId
                              ? { movimentoId: r.movimentoId }
                              : {}),
                            criterio: criterion,
                            periodo: "PERSONALIZADO",
                            situacao: "TODAS",
                            inicio: start,
                            fim: end,
                          })
                        }
                      >
                        {r.descricao}
                      </button>
                    </td>
                    <td>{dateLabel(r.data)}</td>
                    <td>{r.tipo}</td>
                    <td>{money(r.valor / 100)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {!rows.length && <p>Sem registros no recorte.</p>}
          <p>
            {rows.length} movimentos · Página {page + 1}
          </p>
          <button
            type="button"
            disabled={!page}
            onClick={() => setPage((p) => p - 1)}
          >
            Anterior
          </button>
          <button
            type="button"
            disabled={(page + 1) * 20 >= rows.length}
            onClick={() => setPage((p) => p + 1)}
          >
            Próxima
          </button>
          <p className="fv-management-notice">{view.aviso}</p>
          <Exclusions data={view.exclusoes} />
        </>
      )}
      {forecast && (
        <>
          <p>
            Totais realizados zerados podem refletir ausência de registros na
            fonte. Confira a cobertura e o detalhamento antes de concluir que
            não houve operações.
          </p>
          <div className="fv-management-cards">
            {[
              ["Realizado", forecast.realizado],
              ["Compromissos de hoje em diante", forecast.compromissosFuturos],
              ["Já vencidos e ainda abertos", forecast.vencidosAtuais],
            ].map(([name, total]) => {
              const t = total as Forecast["realizado"];
              return (
                <article key={String(name)}>
                  <h3>{String(name)}</h3>
                  <p>Entradas: {money(t.receita)}</p>
                  <p>Saídas: {money(t.despesa)}</p>
                  <strong>Diferença: {money(t.resultado)}</strong>
                </article>
              );
            })}
          </div>
          <div className="fv-management-scroll">
            <table>
              <thead>
                <tr>
                  <th>Mês</th>
                  <th>Recebido</th>
                  <th>Pago</th>
                  <th>A receber futuro</th>
                  <th>A pagar futuro</th>
                  <th>Receber vencido</th>
                  <th>Pagar vencido</th>
                </tr>
              </thead>
              <tbody>
                {forecast.meses.map((m) => (
                  <tr key={m.mes}>
                    <th>{m.mes}</th>
                    <td>{money(m.recebido)}</td>
                    <td>{money(m.pago)}</td>
                    <td>{money(m.receberPrevisto)}</td>
                    <td>{money(m.pagarPrevisto)}</td>
                    <td>{money(m.receberVencido)}</td>
                    <td>{money(m.pagarVencido)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <details>
            <summary>
              Compromissos incluídos ({forecast.compromissos.length})
            </summary>
            <OpenRows
              rows={forecast.compromissos}
              onDetail={(id) => setDetail({ id: String(id), posicao: "true" })}
            />
          </details>
          <p className="fv-management-notice">{forecast.aviso}</p>
          <Exclusions data={forecast.exclusoes} />
          <p>
            <Link to="/projecao">Abrir simulação na Projeção</Link> · Hipóteses
            de simulação não compõem o realizado nem os compromissos
            cadastrados.
          </p>
        </>
      )}
      {dre && (
        <>
          <p>
            <strong>
              {dre.estado.replaceAll("_", " ")} · {dre.naoMapeadas} categorias
              sem revisão
            </strong>
          </p>
          <p className="fv-management-notice">{dre.aviso}</p>
          {dre.estado === "MAPEAMENTO_REVISADO" && (
            <div className="fv-management-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Demonstrativo gerencial</th>
                    <th>Valor</th>
                  </tr>
                </thead>
                <tbody>
                  {dre.demonstrativo.map((r) => (
                    <tr key={r.nome}>
                      <th>{r.nome}</th>
                      <td>{money(r.valor)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Exclusions data={dre.exclusoes ?? {}} />
          <p>
            Fora da DRE: receitas {money(dre.foraReceitas)} · despesas{" "}
            {money(dre.foraDespesas)}
          </p>
          {!admin && <p>Somente um administrador pode revisar o mapeamento.</p>}
          {dre.categorias.map((c) => (
            <DreMapping
              key={`${c.tipo}:${c.categoria}`}
              row={c}
              clienteId={clienteId}
              admin={admin}
              onSaved={() => void load()}
            />
          ))}
        </>
      )}
      {detail && (
        <FinancialRecordDialog
          clienteId={clienteId}
          params={detail}
          onClose={() => setDetail(null)}
        />
      )}
    </>
  );
}
function Exclusions({ data }: { data: Record<string, number> }) {
  return (
    <details>
      <summary>Registros fora dos totais e critérios</summary>
      {Object.entries(data).map(([k, v]) => (
        <p key={k}>
          {k}: {v}
        </p>
      ))}
    </details>
  );
}
function DreMapping({
  row,
  clienteId,
  admin,
  onSaved,
}: {
  clienteId: number;
  row: Dre["categorias"][number];
  admin: boolean;
  onSaved: () => void;
}) {
  const [group, setGroup] = useState<DreGroup | "">(row.grupo ?? ""),
    [confirmed, setConfirmed] = useState(false),
    [note, setNote] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const controller = useRef<AbortController | null>(null);
  useEffect(() => () => controller.current?.abort(), []);
  async function save(e: FormEvent) {
    e.preventDefault();
    if (!group || !confirmed) return;
    const c = new AbortController();
    controller.current = c;
    setBusy(true);
    setError("");
    try {
      await saveDre(
        {
          clienteId,
          tipo: row.tipo,
          categoria: row.categoria,
          grupo: group,
          confirmado: confirmed,
          versao: row.versao,
          observacao: note,
        },
        c.signal,
      );
      if (!c.signal.aborted) onSaved();
    } catch (e) {
      if (!c.signal.aborted)
        setError(e instanceof Error ? e.message : "Falha ao salvar.");
    } finally {
      if (!c.signal.aborted) setBusy(false);
    }
  }
  return (
    <details>
      <summary>
        {row.tipo} · {row.categoria} · {money(row.valor)} ·{" "}
        {row.grupo ?? "Revisão pendente"}
      </summary>
      {admin && (
        <form onSubmit={(e) => void save(e)}>
          <label>
            Grupo gerencial
            <select
              aria-label="Grupo gerencial"
              required
              value={group}
              onChange={(e) => {
                setGroup(e.target.value as DreGroup);
                setConfirmed(false);
              }}
            >
              <option value="">Selecione após revisar</option>
              {dreGroups
                .filter(
                  (g) =>
                    g === "FORA_DRE" ||
                    (row.tipo === "RECEITA"
                      ? g === "RECEITA_OPERACIONAL" ||
                        g === "RECEITA_FINANCEIRA"
                      : g !== "RECEITA_OPERACIONAL" &&
                        g !== "RECEITA_FINANCEIRA"),
                )
                .map((g) => (
                  <option key={g} value={g}>
                    {g.replaceAll("_", " ")}
                  </option>
                ))}
            </select>
          </label>
          <label>
            Justificativa / observação
            <input
              maxLength={1000}
              required={group === "FORA_DRE"}
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </label>
          <label>
            <input
              type="checkbox"
              checked={confirmed}
              onChange={(e) => setConfirmed(e.target.checked)}
            />{" "}
            Revisei e confirmo este mapeamento.
          </label>
          <button type="submit" disabled={busy || !group || !confirmed}>
            {busy ? "Salvando…" : "Salvar revisão"}
          </button>
          <ErrorText text={error} />
        </form>
      )}
    </details>
  );
}
