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
};
type Match = { criterio: string; api: Move; extrato: Move };
type Details = {
  correspondencias: Match[];
  somenteApi: Move[];
  somenteExtrato: Move[];
  ambiguidades: { extrato: Move; candidatos: Move[] }[];
};
type Total = { receita: number; despesa: number; resultado: number };
type Resultado = {
  detalhes?: Details;
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
            {dados.situacao === "DIVERGENCIA"
              ? "Há diferenças a conferir"
              : dados.situacao === "SEM_DADOS_COMPARAVEIS"
                ? "Sem dados suficientes nas duas fontes"
                : "Totais iguais; vínculos e cobertura ainda precisam ser conferidos"}
          </strong>
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
                {dados.detalhes.ambiguidades.length} ambiguidades.
              </p>
              <p>
                Sugestões não gravam vínculos nem mesclam fontes. Ausência de ID
                exige revisão do documento original.
              </p>
              <button
                type="button"
                onClick={() => {
                  const d = dados.detalhes!;
                  exportarCSV("conferencia-individual.csv", [
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
                    ],
                    ...d.correspondencias.flatMap((x) => [
                      [
                        x.criterio,
                        "API",
                        x.api.chave,
                        x.api.parcelaId ?? "",
                        x.api.baixaId ?? "",
                        x.api.data,
                        x.api.tipo,
                        x.api.descricao,
                        x.api.valor,
                      ],
                      [
                        x.criterio,
                        "EXTRATO",
                        x.extrato.chave,
                        x.extrato.parcelaId ?? "",
                        x.extrato.baixaId ?? "",
                        x.extrato.data,
                        x.extrato.tipo,
                        x.extrato.descricao,
                        x.extrato.valor,
                      ],
                    ]),
                    ...d.somenteApi.map((x) => [
                      "SEM_CORRESPONDENCIA",
                      "API",
                      x.chave,
                      x.parcelaId ?? "",
                      x.baixaId ?? "",
                      x.data,
                      x.tipo,
                      x.descricao,
                      x.valor,
                    ]),
                    ...d.somenteExtrato.map((x) => [
                      "SEM_CORRESPONDENCIA",
                      "EXTRATO",
                      x.chave,
                      x.parcelaId ?? "",
                      x.baixaId ?? "",
                      x.data,
                      x.tipo,
                      x.descricao,
                      x.valor,
                    ]),
                    ...d.ambiguidades.flatMap((a) => [
                      [
                        "AMBIGUIDADE",
                        "EXTRATO",
                        a.extrato.chave,
                        a.extrato.parcelaId ?? "",
                        a.extrato.baixaId ?? "",
                        a.extrato.data,
                        a.extrato.tipo,
                        a.extrato.descricao,
                        a.extrato.valor,
                      ],
                      ...a.candidatos.map((x) => [
                        "CANDIDATO_NAO_VINCULADO",
                        "API",
                        x.chave,
                        x.parcelaId ?? "",
                        x.baixaId ?? "",
                        x.data,
                        x.tipo,
                        x.descricao,
                        x.valor,
                      ]),
                    ]),
                  ]);
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
                    {x.criterio} · {x.api.data} · {x.api.descricao} ↔{" "}
                    {x.extrato.descricao} · {moeda(x.api.valor)}
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
