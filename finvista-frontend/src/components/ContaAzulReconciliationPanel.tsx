import { uniqueReconciliationRows } from "../services/reconciliationExport";
import { exportarCSV } from "../services/dashboardAnalysisService";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { API_URL } from "../config/api";
import { apiFetch } from "../config/http";
type Move = {
  chave: string;
  parcelaId: string | null;
  baixaId: string | null;
  descricao: string;
  tipo: string;
  data: string;
  valor: number;
  contaId?: string | null;
  contaNome?: string | null;
  revisao?: {
    atual: boolean;
    motivo: string;
    usuario: string;
    horario: string;
  } | null;
};
type Match = { criterio: string; api: Move; extrato: Move };
type Difference = {
  api: Move;
  extrato: Move;
  campos: string[];
  diferencaValor: number;
};
type Details = {
  divergencias?: Difference[];
  correspondencias: Match[];
  somenteApi: Move[];
  somenteExtrato: Move[];
  ambiguidades: { extrato: Move; candidatos: Move[] }[];
};
type Total = { receita: number; despesa: number; resultado: number };
type Resultado = {
  detalhes?: Details;
  resumo?: {
    movimentosApi: number;
    movimentosExtrato: number;
    porId: number;
    sugestoes: number;
    divergencias: number;
    ambiguidades: number;
    contasNaoComparaveis: number;
    totaisReproduzidos: boolean;
    porConferencia?: number;
  };
  contas?: {
    contaId: string | null;
    nome: string;
    identificada: boolean;
    movimentosApi: number;
    movimentosExtrato: number;
    receitaApi: number;
    despesaApi: number;
    receitaExtrato: number;
    despesaExtrato: number;
  }[];
  cobertura?: {
    fonte: string;
    ultimaSituacao: string | null;
    ultimoTermino: string | null;
    pendenciasEmpresa: number | null;
    consultaAoVivo: boolean;
    coberturaConfirmada: boolean;
  };
  clienteId: number;
  inicio: string;
  fim: string;
  situacao: string;
  api: Total;
  extrato: Total;
  diferencaReceita: number;
  diferencaDespesa: number;
  aviso: string;
  exclusoesApi: Record<string, number>;
  exclusoesExtrato: Record<string, number>;
};
const moeda = (n: number) =>
  n.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const criterionLabel = (value: string) =>
  value === "ID_BAIXA_DATA_TIPO_VALOR"
    ? "ID informado no arquivo e valores correspondentes"
    : value === "VINCULO_CONFIRMADO_PELO_CONTADOR"
      ? "Vínculo confirmado pelo contador"
      : "Sugestão por data, tipo e valor — identidade não confirmada";
const uniqueMoves = (rows: Move[]) => [
  ...new Map(rows.map((r) => [r.chave, r])).values(),
];
const accountName = (m: Move) => m.contaNome || "Nome da conta não informado";
const runLabels: Record<string, string> = {
  CONCLUIDA: "Concluída",
  CONCLUIDA_COM_PENDENCIAS: "Concluída com pendências",
  EM_ANDAMENTO: "Em andamento",
  AGUARDANDO: "Aguardando nova tentativa",
  CANCELADA: "Cancelada",
  PARCIAL: "Parcial",
};
const runLabel = (value: string | null) =>
  runLabels[value ?? ""] ?? "Não informada";
const movementRow = (
  status: string,
  source: string,
  x: Move,
  fields = "",
  difference: number | string = "",
): (string | number)[] => [
  status,
  source,
  x.chave,
  x.parcelaId ?? "",
  x.baixaId ?? "",
  x.data,
  x.tipo,
  x.descricao,
  x.valor,
  x.contaId ?? "",
  fields,
  difference,
  x.contaNome ?? "",
  x.revisao
    ? x.revisao.atual
      ? "CONFERENCIA_DO_CONTADOR"
      : "REVISAO_NECESSARIA"
    : x.baixaId
      ? "ID_INFORMADO_NA_FONTE"
      : "SEM_ID_NA_FONTE",
  x.revisao?.usuario ?? "",
  x.revisao?.horario ?? "",
  x.revisao?.motivo ?? "",
];
export default function ContaAzulReconciliationPanel({
  clienteId,
  endpoint = "/api/integracoes/conta-azul/automatica/conferencia",
  initiallyOpen = false,
  canReview = false,
}: {
  clienteId: number;
  endpoint?: string;
  initiallyOpen?: boolean;
  canReview?: boolean;
}) {
  const [inicio, setInicio] = useState(""),
    [fim, setFim] = useState(""),
    [dados, setDados] = useState<Resultado | null>(null),
    [erro, setErro] = useState(""),
    [busy, setBusy] = useState(false);
  const [fileKey, setFileKey] = useState("");
  const [apiKey, setApiKey] = useState("");
  const controller = useRef<AbortController | null>(null);
  useEffect(() => () => controller.current?.abort(), [clienteId, endpoint]);
  function invalidar() {
    setFileKey("");
    setApiKey("");
    controller.current?.abort();
    setDados(null);
    setBusy(false);
    setErro("");
  }
  async function conferir(e?: FormEvent) {
    e?.preventDefault();
    setFileKey("");
    setApiKey("");
    if (!inicio || !fim || fim < inicio) {
      setErro("Informe o período em ordem válida.");
      return;
    }
    controller.current?.abort();
    const c = new AbortController();
    controller.current = c;
    setBusy(true);
    setErro("");
    setDados(null);
    try {
      const q = new URLSearchParams({
        clienteId: String(clienteId),
        inicio,
        fim,
      });
      const r = await apiFetch(`${API_URL}${endpoint}?${q}`, {
        credentials: "include",
        cache: "no-store",
        signal: c.signal,
      });
      if (!r.ok)
        throw new Error(
          "Não foi possível conferir o período. Verifique a empresa e os dados incluídos.",
        );
      const value: Resultado = await r.json();
      if (
        value.clienteId !== clienteId ||
        value.inicio !== inicio ||
        value.fim !== fim
      )
        throw new Error("A empresa ou período mudou. Consulte novamente.");
      if (!c.signal.aborted) setDados(value);
    } catch (e) {
      if (!c.signal.aborted)
        setErro(e instanceof Error ? e.message : "Falha na conferência.");
    } finally {
      if (!c.signal.aborted) setBusy(false);
    }
  }
  const details = dados?.detalhes;
  const fileRows = details
    ? uniqueMoves([
        ...details.correspondencias.map((x) => x.extrato),
        ...details.somenteExtrato,
        ...(details.divergencias ?? []).map((x) => x.extrato),
      ])
    : [];
  const apiRows = details
    ? uniqueMoves([
        ...details.correspondencias.map((x) => x.api),
        ...details.somenteApi,
        ...(details.divergencias ?? []).map((x) => x.api),
      ])
    : [];
  const selectedFile = fileRows.find((x) => x.chave === fileKey);
  const candidates = selectedFile
    ? apiRows.filter(
        (x) =>
          x.data === selectedFile.data &&
          x.tipo === selectedFile.tipo &&
          Math.round(Number(x.valor) * 100) ===
            Math.round(Number(selectedFile.valor) * 100),
      )
    : [];
  const selectedApi = candidates.find((x) => x.chave === apiKey);
  return (
    <details open={initiallyOpen}>
      <summary>Comparar API com extrato incluído</summary>
      <p>
        API significa os pagamentos auditados já armazenados e usados no
        FinVista no critério Baixa — API. Esta consulta não acessa o Conta Azul
        em tempo real. Compare pagamentos e recebimentos pela data de
        baixa/movimento. O arquivo precisa cobrir o mesmo período, empresa e
        contas. Transferências permanecem separadas.
      </p>
      <form onSubmit={(e) => void conferir(e)}>
        <label>
          Baixa inicial
          <input
            type="date"
            required
            value={inicio}
            onChange={(e) => {
              invalidar();
              setInicio(e.target.value);
            }}
          />
        </label>
        <label>
          Baixa final
          <input
            type="date"
            required
            min={inicio || undefined}
            value={fim}
            onChange={(e) => {
              invalidar();
              setFim(e.target.value);
            }}
          />
        </label>
        <button type="submit" disabled={busy}>
          {busy ? "Conferindo…" : "Conferir movimentos"}
        </button>
      </form>
      {erro && <p role="alert">{erro}</p>}
      {dados &&
        dados.clienteId === clienteId &&
        dados.inicio === inicio &&
        dados.fim === fim && (
          <div role="status">
            <strong>
              {dados.situacao === "DIVERGENCIA"
                ? "Há diferenças de valores a conferir"
                : dados.situacao === "DIVERGENCIA_DE_REGISTROS"
                  ? "Há registros ou vínculos ainda não conferidos"
                  : dados.situacao === "SEM_DADOS_COMPARAVEIS"
                    ? "Sem dados suficientes nas duas fontes"
                    : dados.situacao ===
                        "VINCULOS_CONFERIDOS_COBERTURA_NAO_CERTIFICADA"
                      ? "Movimentos vinculados por conferência registrada; cobertura ainda não certificada"
                      : dados.situacao ===
                          "VALORES_POR_ID_CONFERIDOS_COBERTURA_NAO_CERTIFICADA"
                        ? "Valores correspondentes por ID; cobertura ainda não certificada"
                        : "Totais iguais; vínculos e cobertura ainda precisam ser conferidos"}
            </strong>
            {dados.resumo && (
              <>
                <p>
                  {dados.resumo.movimentosApi} movimentos da API ·{" "}
                  {dados.resumo.movimentosExtrato} movimentos do extrato, sem
                  repetir rateios.
                </p>
                {!dados.resumo.totaisReproduzidos && (
                  <p role="alert">
                    A soma dos movimentos não reproduz os totais apresentados.
                    Esta conferência não pode ser aprovada.
                  </p>
                )}
                {dados.resumo.contasNaoComparaveis > 0 && (
                  <p role="status">
                    Em {dados.resumo.contasNaoComparaveis} movimentos do
                    extrato, a conta ainda não foi confirmada no pareamento.
                    Confira a identificação, o vínculo e eventuais diferenças.
                  </p>
                )}
              </>
            )}
            {dados.cobertura && (
              <p role="status">
                Dados armazenados no FinVista; sem consulta ao vivo ao Conta
                Azul. Execução mais recente:{" "}
                {runLabel(dados.cobertura.ultimaSituacao)} · término:{" "}
                {dados.cobertura.ultimoTermino
                  ? new Date(dados.cobertura.ultimoTermino).toLocaleString(
                      "pt-BR",
                    )
                  : "não informado"}
                . Pendências ainda não resolvidas na empresa inteira:{" "}
                {dados.cobertura.pendenciasEmpresa ?? "não apuradas"}. A
                cobertura integral ainda não foi certificada.
              </p>
            )}
            <div style={{ overflowX: "auto" }}>
              <table>
                <thead>
                  <tr>
                    <th>Indicador</th>
                    <th>API no FinVista</th>
                    <th>Extrato no FinVista</th>
                    <th>Diferença API − extrato</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <th>Recebido</th>
                    <td>{moeda(dados.api.receita)}</td>
                    <td>{moeda(dados.extrato.receita)}</td>
                    <td>{moeda(dados.diferencaReceita)}</td>
                  </tr>
                  <tr>
                    <th>Pago</th>
                    <td>{moeda(dados.api.despesa)}</td>
                    <td>{moeda(dados.extrato.despesa)}</td>
                    <td>{moeda(dados.diferencaDespesa)}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            {dados.contas && (
              <section>
                <h3>Movimentações por conta no período</h3>
                <p>
                  Entradas e saídas realizadas, não saldos bancários. Um nome
                  parecido não identifica a mesma conta. Sem ID no extrato, o
                  movimento permanece separado até a conferência.
                </p>
                <div style={{ overflowX: "auto" }}>
                  <table>
                    <thead>
                      <tr>
                        <th>Conta</th>
                        <th>Recebido API</th>
                        <th>Recebido extrato</th>
                        <th>Pago API</th>
                        <th>Pago extrato</th>
                        <th>Conferência</th>
                      </tr>
                    </thead>
                    <tbody>
                      {dados.contas.map((c, i) => (
                        <tr key={c.contaId ?? `pendente-${i}`}>
                          <th>
                            {c.nome}
                            {!c.identificada && (
                              <small>Identificação ainda não conferida</small>
                            )}
                          </th>
                          <td>
                            {c.movimentosApi
                              ? moeda(c.receitaApi)
                              : "Sem movimentos"}
                          </td>
                          <td>
                            {c.movimentosExtrato
                              ? moeda(c.receitaExtrato)
                              : "Sem movimentos vinculados"}
                          </td>
                          <td>
                            {c.movimentosApi
                              ? moeda(c.despesaApi)
                              : "Sem movimentos"}
                          </td>
                          <td>
                            {c.movimentosExtrato
                              ? moeda(c.despesaExtrato)
                              : "Sem movimentos vinculados"}
                          </td>
                          <td>
                            {!c.identificada ||
                            !c.movimentosApi ||
                            !c.movimentosExtrato
                              ? "Conferência pendente"
                              : Math.round(c.receitaApi * 100) ===
                                    Math.round(c.receitaExtrato * 100) &&
                                  Math.round(c.despesaApi * 100) ===
                                    Math.round(c.despesaExtrato * 100)
                                ? "Totais do recorte iguais"
                                : "Diferenças no recorte"}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            )}
            {canReview && dados.detalhes && (
              <section>
                <h3>Registrar conferência de vínculo</h3>
                <p>
                  Confira o comprovante e a conta no documento original. A
                  confirmação registra a decisão do contador, sem inventar um ID
                  no arquivo nem alterar valores. Se não houver prova
                  suficiente, mantenha a pendência.
                </p>
                <label>
                  Movimento do extrato
                  <select
                    value={fileKey}
                    onChange={(e) => {
                      setFileKey(e.target.value);
                      setApiKey("");
                    }}
                  >
                    <option value="">Selecione um movimento</option>
                    {fileRows.map((x) => (
                      <option key={x.chave} value={x.chave}>
                        {x.data} · {x.descricao} · {moeda(x.valor)} ·{" "}
                        {accountName(x)}
                      </option>
                    ))}
                  </select>
                </label>
                {selectedFile && !selectedFile.revisao && (
                  <label>
                    Baixa da API para conferir
                    <select
                      value={apiKey}
                      onChange={(e) => setApiKey(e.target.value)}
                    >
                      <option value="">
                        Selecione após conferir o documento
                      </option>
                      {candidates.map((x) => (
                        <option key={x.chave} value={x.chave}>
                          {x.data} · {x.descricao} · {moeda(x.valor)} ·{" "}
                          {accountName(x)} · {x.baixaId?.slice(-8)}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                {selectedFile &&
                  !selectedFile.revisao &&
                  !candidates.length && (
                    <p>
                      Nenhum candidato com a mesma data, tipo e valor. Amplie o
                      recorte ou investigue a diferença, sem ajustar os valores
                      para forçar o vínculo.
                    </p>
                  )}
                {selectedFile && (selectedApi || selectedFile.revisao) && (
                  <LinkReviewForm
                    key={`${clienteId}:${fileKey}:${apiKey}`}
                    clienteId={clienteId}
                    inicio={inicio}
                    fim={fim}
                    api={selectedApi ?? null}
                    extrato={selectedFile}
                    onSaved={() => void conferir()}
                  />
                )}
              </section>
            )}
            {dados.detalhes && (
              <section>
                <h3>Conferência individual</h3>
                <p>
                  {
                    dados.detalhes.correspondencias.filter(
                      (x) => x.criterio === "ID_BAIXA_DATA_TIPO_VALOR",
                    ).length
                  }{" "}
                  correspondências por ID ·{" "}
                  {
                    dados.detalhes.correspondencias.filter((x) =>
                      x.criterio.startsWith("SUGESTAO"),
                    ).length
                  }{" "}
                  sugestões sem vínculo certificado ·{" "}
                  {dados.resumo?.porConferencia ?? 0} vínculos confirmados pelo
                  contador · {dados.detalhes.ambiguidades.length} ambiguidades ·{" "}
                  {dados.detalhes.divergencias?.length ?? 0} divergências por
                  ID.
                </p>
                <p>
                  Sugestões não gravam vínculos nem mesclam fontes. Ausência de
                  ID exige revisão do documento original. Listas sem
                  correspondência conclusiva incluem os candidatos ambíguos; não
                  some suas contagens às ambiguidades. O CSV mantém cada
                  movimento uma única vez por fonte e chave.
                </p>
                <button
                  type="button"
                  onClick={() => {
                    const d = dados.detalhes!;
                    exportarCSV(
                      "conferencia-individual.csv",
                      uniqueReconciliationRows([
                        [
                          "Situação",
                          "Fonte",
                          "Chave",
                          "Parcela",
                          "Baixa",
                          "Data",
                          "Tipo",
                          "Descrição",
                          "Valor",
                          "ID da conta financeira",
                          "Campos divergentes",
                          "Diferença API menos extrato (uma vez por par)",
                          "Nome da conta",
                          "Origem da identificação",
                          "Conferido por",
                          "Conferido em",
                          "Referência da conferência",
                        ],
                        ...d.correspondencias.flatMap((x) => [
                          movementRow(x.criterio, "API", x.api),
                          movementRow(x.criterio, "EXTRATO", x.extrato),
                        ]),
                        ...(d.divergencias ?? []).flatMap((x) => [
                          movementRow(
                            "DIVERGENCIA_POR_ID",
                            "API",
                            x.api,
                            x.campos.join("; "),
                            x.diferencaValor,
                          ),
                          movementRow(
                            "DIVERGENCIA_POR_ID",
                            "EXTRATO",
                            x.extrato,
                            x.campos.join("; "),
                          ),
                        ]),
                        ...d.somenteApi.map((x) =>
                          movementRow("SEM_CORRESPONDENCIA", "API", x),
                        ),
                        ...d.somenteExtrato.map((x) =>
                          movementRow("SEM_CORRESPONDENCIA", "EXTRATO", x),
                        ),
                        ...d.ambiguidades.flatMap((a) => [
                          movementRow("AMBIGUIDADE", "EXTRATO", a.extrato),
                          ...a.candidatos.map((x) =>
                            movementRow("CANDIDATO_NAO_VINCULADO", "API", x),
                          ),
                        ]),
                      ]),
                    );
                  }}
                >
                  Exportar conferência individual
                </button>
                <details>
                  <summary>
                    Correspondências e sugestões (
                    {dados.detalhes.correspondencias.length})
                  </summary>
                  {dados.detalhes.correspondencias.slice(0, 100).map((x) => (
                    <p key={x.extrato.chave}>
                      {criterionLabel(x.criterio)} · {x.api.data} ·{" "}
                      {x.api.descricao} ↔ {x.extrato.descricao} ·{" "}
                      {moeda(x.api.valor)} · API: {accountName(x.api)} ·
                      Extrato: {accountName(x.extrato)}
                      {x.extrato.revisao && (
                        <small>
                          Conferido por {x.extrato.revisao.usuario} em{" "}
                          {new Date(x.extrato.revisao.horario).toLocaleString(
                            "pt-BR",
                          )}{" "}
                          · {x.extrato.revisao.motivo}
                        </small>
                      )}
                      {canReview && (
                        <button
                          type="button"
                          onClick={() => {
                            setFileKey(x.extrato.chave);
                            setApiKey(x.api.chave);
                          }}
                        >
                          Conferir este vínculo
                        </button>
                      )}
                    </p>
                  ))}
                </details>
                <details>
                  <summary>
                    Divergências identificadas pelo mesmo ID (
                    {dados.detalhes.divergencias?.length ?? 0})
                  </summary>
                  {(dados.detalhes.divergencias ?? [])
                    .slice(0, 100)
                    .map((x) => (
                      <p key={x.extrato.chave}>
                        <strong>{x.campos.join(", ")}</strong> ·{" "}
                        {x.api.descricao} · API: {x.api.data},{" "}
                        {moeda(x.api.valor)} · Extrato: {x.extrato.data},{" "}
                        {moeda(x.extrato.valor)} · Diferença:{" "}
                        {moeda(x.diferencaValor)}
                      </p>
                    ))}
                </details>
                <details>
                  <summary>
                    Sem correspondência na API (
                    {dados.detalhes.somenteExtrato.length})
                  </summary>
                  {dados.detalhes.somenteExtrato.slice(0, 100).map((x) => (
                    <p key={x.chave}>
                      {x.data} · {x.descricao} · {moeda(x.valor)}
                    </p>
                  ))}
                </details>
                <details>
                  <summary>
                    Sem correspondência no extrato (
                    {dados.detalhes.somenteApi.length})
                  </summary>
                  {dados.detalhes.somenteApi.slice(0, 100).map((x) => (
                    <p key={x.chave}>
                      {x.data} · {x.descricao} · {moeda(x.valor)}
                    </p>
                  ))}
                </details>
                <details>
                  <summary>
                    Ambiguidades e IDs divergentes (
                    {dados.detalhes.ambiguidades.length})
                  </summary>
                  {dados.detalhes.ambiguidades.slice(0, 100).map((x) => (
                    <p key={x.extrato.chave}>
                      {x.extrato.data} · {x.extrato.descricao}:{" "}
                      {x.candidatos.length} candidatos; confira valores e
                      identificadores.{" "}
                      {x.extrato.revisao &&
                        !x.extrato.revisao.atual &&
                        "A conferência anterior ficou desatualizada."}
                      {canReview && (
                        <button
                          type="button"
                          onClick={() => {
                            setFileKey(x.extrato.chave);
                            setApiKey("");
                          }}
                        >
                          Revisar candidatos
                        </button>
                      )}
                    </p>
                  ))}
                </details>
                <p>
                  Listas mostram até 100 itens; o CSV contém todas as
                  correspondências, diferenças e candidatos ambíguos do recorte.
                </p>
              </section>
            )}
            <details>
              <summary>Critérios e dados fora dos totais</summary>
              <p>{dados.aviso}</p>
              {Object.entries(dados.exclusoesApi).map(([k, n]) => (
                <p key={`api-${k}`}>
                  API · {k}: {n}
                </p>
              ))}
              {Object.entries(dados.exclusoesExtrato).map(([k, n]) => (
                <p key={`file-${k}`}>
                  Extrato · {k}: {n}
                </p>
              ))}
            </details>
          </div>
        )}
    </details>
  );
}

function LinkReviewForm({
  clienteId,
  inicio,
  fim,
  api,
  extrato,
  onSaved,
}: {
  clienteId: number;
  inicio: string;
  fim: string;
  api: Move | null;
  extrato: Move;
  onSaved: () => void;
}) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [confirmed, setConfirmed] = useState(false);
  const [plan, setPlan] = useState<{
    token: string;
    clienteId: number;
    api: Move | null;
    extrato: Move;
    acao: string;
    motivo: string;
  } | null>(null);
  const request = useRef<AbortController | null>(null);
  useEffect(() => () => request.current?.abort(), []);
  const action = extrato.revisao ? "DESVINCULAR" : "VINCULAR";
  async function submit(apply: boolean) {
    if (busy || (apply && (!plan || !confirmed))) return;
    const c = new AbortController();
    request.current?.abort();
    request.current = c;
    setBusy(true);
    setError("");
    try {
      const response = await apiFetch(
        `${API_URL}/api/integracoes/conta-azul/automatica/conferencia/${apply ? "confirmar-vinculo" : "planejar-vinculo"}`,
        {
          method: "POST",
          credentials: "include",
          signal: c.signal,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(
            apply
              ? { token: plan?.token, clienteId, confirmado: true }
              : {
                  clienteId,
                  inicio,
                  fim,
                  extratoChave: extrato.chave,
                  apiChave: api?.chave ?? null,
                  acao: action,
                  motivo: reason,
                },
          ),
        },
      );
      const data = await response.json().catch(() => null);
      if (!response.ok)
        throw new Error(
          data?.mensagem ||
            data?.detail ||
            data?.message ||
            "Falha ao registrar a conferência. Prepare outra prévia.",
        );
      if (c.signal.aborted) return;
      if (apply) {
        onSaved();
        return;
      }
      if (
        data.clienteId !== clienteId ||
        data.extrato?.chave !== extrato.chave ||
        data.acao !== action ||
        !data.token
      )
        throw new Error("A empresa ou movimento mudou. Consulte novamente.");
      setPlan(data);
      setConfirmed(false);
    } catch (e) {
      if (!c.signal.aborted) {
        setPlan(null);
        setConfirmed(false);
        setError(e instanceof Error ? e.message : "Falha na conferência.");
      }
    } finally {
      if (!c.signal.aborted) setBusy(false);
    }
  }
  const card = (name: string, m: Move) => (
    <div>
      <strong>{name}</strong>
      <p>
        {m.data} · {m.descricao} · {moeda(m.valor)}
      </p>
      <p>Conta: {accountName(m)}</p>
      <details>
        <summary>Identificação do movimento</summary>
        <p>Parcela: {m.parcelaId || "Não informada no arquivo"}</p>
        <p>Baixa: {m.baixaId || "Não informada no arquivo"}</p>
        <p>Conta: {m.contaId || "Sem ID no arquivo"}</p>
      </details>
    </div>
  );
  return (
    <div>
      <h4>
        {action === "DESVINCULAR"
          ? "Retirar conferência anterior"
          : "Conferir pagamento e conta"}
      </h4>
      {extrato.revisao && !extrato.revisao.atual && (
        <p role="alert">
          Os dados conferidos anteriormente mudaram. O vínculo não é mais
          considerado confirmado. Retire a conferência anterior e revise o
          documento novamente.
        </p>
      )}
      {card("Extrato", extrato)}
      {api && card("API", api)}
      <label>
        Referência do comprovante e o que foi conferido
        <textarea
          value={reason}
          minLength={15}
          maxLength={1000}
          disabled={busy}
          onChange={(e) => {
            setReason(e.target.value);
            setPlan(null);
            setConfirmed(false);
          }}
        />
      </label>
      <button
        type="button"
        disabled={busy || reason.trim().length < 15}
        onClick={() => void submit(false)}
      >
        Preparar conferência
      </button>
      {plan && (
        <div>
          <p>
            {plan.acao === "DESVINCULAR"
              ? "Apenas a decisão anterior será retirada; o histórico e os movimentos serão preservados."
              : "A baixa e a conta da API serão associadas a este movimento como conferência do contador. As fontes e os valores permanecem separados."}
          </p>
          {plan.api && card("Baixa e conta conferidas na prévia", plan.api)}
          <label>
            <input
              type="checkbox"
              checked={confirmed}
              disabled={busy}
              onChange={(e) => setConfirmed(e.target.checked)}
            />
            {plan.acao === "DESVINCULAR"
              ? "Conferi e autorizo retirar este vínculo."
              : "Conferi o pagamento e a conta no documento original e confirmo que representam este mesmo movimento."}
          </label>
          <button
            type="button"
            disabled={busy || !confirmed}
            onClick={() => void submit(true)}
          >
            Confirmar conferência
          </button>
        </div>
      )}
      {error && <p role="alert">{error}</p>}
    </div>
  );
}
