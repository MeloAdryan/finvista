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
};
type Match = { criterio: string; api: Move; extrato: Move };
type Difference = { api: Move; extrato: Move; campos: string[]; diferencaValor: number };
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
  resumo?: { movimentosApi: number; movimentosExtrato: number; porId: number; sugestoes: number; divergencias: number; ambiguidades: number; contasNaoComparaveis: number; totaisReproduzidos: boolean };
  cobertura?: { fonte: string; ultimaSituacao: string | null; ultimoTermino: string | null; pendenciasEmpresa: number | null; consultaAoVivo: boolean; coberturaConfirmada: boolean };
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
const criterionLabel = (value: string) => value === "ID_BAIXA_DATA_TIPO_VALOR" ? "Correspondência por ID e valores" : "Sugestão por data, tipo e valor — identidade não confirmada";
const runLabels: Record<string,string> = { CONCLUIDA: "Concluída", CONCLUIDA_COM_PENDENCIAS: "Concluída com pendências", EM_ANDAMENTO: "Em andamento", AGUARDANDO: "Aguardando nova tentativa", CANCELADA: "Cancelada", PARCIAL: "Parcial" };
const runLabel = (value: string | null) => runLabels[value ?? ""] ?? "Não informada";
const movementRow = (status: string, source: string, x: Move, fields = "", difference: number | string = ""): (string | number)[] =>
  [status, source, x.chave, x.parcelaId ?? "", x.baixaId ?? "", x.data, x.tipo, x.descricao, x.valor, x.contaId ?? "", fields, difference];
export default function ContaAzulReconciliationPanel({
  clienteId,
  endpoint = "/api/integracoes/conta-azul/automatica/conferencia",
  initiallyOpen = false,
}: {
  clienteId: number;
  endpoint?: string;
  initiallyOpen?: boolean;
}) {
  const [inicio, setInicio] = useState(""),
    [fim, setFim] = useState(""),
    [dados, setDados] = useState<Resultado | null>(null),
    [erro, setErro] = useState(""),
    [busy, setBusy] = useState(false);
  const controller = useRef<AbortController | null>(null);
  useEffect(() => () => controller.current?.abort(), []);
  function invalidar() {
    controller.current?.abort();
    setDados(null);
    setBusy(false);
    setErro("");
  }
  async function conferir(e: FormEvent) {
    e.preventDefault();
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
      {dados && (
        <div role="status">
          <strong>
            {dados.situacao === "DIVERGENCIA" || dados.situacao === "DIVERGENCIA_DE_REGISTROS"
              ? "Há diferenças de valores ou registros a conferir"
              : dados.situacao === "SEM_DADOS_COMPARAVEIS"
                ? "Sem dados suficientes nas duas fontes"
                : dados.situacao === "VALORES_POR_ID_CONFERIDOS_COBERTURA_NAO_CERTIFICADA"
                  ? "Valores correspondentes por ID; cobertura ainda não certificada"
                  : "Totais iguais; vínculos e cobertura ainda precisam ser conferidos"}
          </strong>
          {dados.resumo && <>
            <p>{dados.resumo.movimentosApi} movimentos da API · {dados.resumo.movimentosExtrato} movimentos do extrato, sem repetir rateios.</p>
            {!dados.resumo.totaisReproduzidos && <p role="alert">A soma dos movimentos não reproduz os totais apresentados. Esta conferência não pode ser aprovada.</p>}
            {dados.resumo.contasNaoComparaveis > 0 && <p role="status">Em {dados.resumo.contasNaoComparaveis} correspondências ou sugestões, falta identificação da conta em pelo menos uma fonte. A conta financeira ainda não foi conferida.</p>}
          </>}
          {dados.cobertura && <p role="status">Dados armazenados no FinVista; sem consulta ao vivo ao Conta Azul. Execução mais recente: {runLabel(dados.cobertura.ultimaSituacao)} · término: {dados.cobertura.ultimoTermino ? new Date(dados.cobertura.ultimoTermino).toLocaleString("pt-BR") : "não informado"}. Pendências ainda não resolvidas na empresa inteira: {dados.cobertura.pendenciasEmpresa ?? "não apuradas"}. A cobertura integral ainda não foi certificada.</p>}
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
                  dados.detalhes.correspondencias.filter(
                    (x) => x.criterio !== "ID_BAIXA_DATA_TIPO_VALOR",
                  ).length
                }{" "}
                sugestões sem vínculo certificado ·{" "}
                {dados.detalhes.ambiguidades.length} ambiguidades · {dados.detalhes.divergencias?.length ?? 0} divergências por ID.
              </p>
              <p>
                Sugestões não gravam vínculos nem mesclam fontes. Ausência de ID exige revisão do documento original.
                Listas sem correspondência conclusiva incluem os candidatos ambíguos; não some suas contagens às ambiguidades.
                O CSV mantém cada movimento uma única vez por fonte e chave.
              </p>
              <button
                type="button"
                onClick={() => {
                  const d = dados.detalhes!;
                  exportarCSV("conferencia-individual.csv", uniqueReconciliationRows([
                    ["Situação", "Fonte", "Chave", "Parcela", "Baixa", "Data", "Tipo", "Descrição", "Valor", "ID da conta financeira", "Campos divergentes", "Diferença API menos extrato (uma vez por par)"],
                    ...d.correspondencias.flatMap(x => [movementRow(x.criterio,"API",x.api),movementRow(x.criterio,"EXTRATO",x.extrato)]),
                    ...(d.divergencias ?? []).flatMap(x => [movementRow("DIVERGENCIA_POR_ID","API",x.api,x.campos.join("; "),x.diferencaValor),movementRow("DIVERGENCIA_POR_ID","EXTRATO",x.extrato,x.campos.join("; "))]),
                    ...d.somenteApi.map(x => movementRow("SEM_CORRESPONDENCIA","API",x)),
                    ...d.somenteExtrato.map(x => movementRow("SEM_CORRESPONDENCIA","EXTRATO",x)),
                    ...d.ambiguidades.flatMap(a => [movementRow("AMBIGUIDADE","EXTRATO",a.extrato),...a.candidatos.map(x => movementRow("CANDIDATO_NAO_VINCULADO","API",x))]),
                  ]));
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
                    {criterionLabel(x.criterio)} · {x.api.data} · {x.api.descricao} ↔{" "}
                    {x.extrato.descricao} · {moeda(x.api.valor)}
                  </p>
                ))}
              </details>
              <details>
                <summary>Divergências identificadas pelo mesmo ID ({dados.detalhes.divergencias?.length ?? 0})</summary>
                {(dados.detalhes.divergencias ?? []).slice(0,100).map(x => <p key={x.extrato.chave}><strong>{x.campos.join(", ")}</strong> · {x.api.descricao} · API: {x.api.data}, {moeda(x.api.valor)} · Extrato: {x.extrato.data}, {moeda(x.extrato.valor)} · Diferença: {moeda(x.diferencaValor)}</p>)}
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
                    identificadores.
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
