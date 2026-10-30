import {
  Bar,
  ComposedChart,
  CartesianGrid,
  Line,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { useEffect, useState } from "react";
import { API_URL } from "../config/api";
import {
  getFinancialView,
  type ViewFilter,
} from "../services/financialViewService";
import { brl, exportarCSV } from "../services/dashboardAnalysisService";
type Account = {
  id: string;
  nome: string;
  saldoInicial: number;
  recebimentos: number;
  pagamentos: number;
  transferenciasEntrada: number;
  transferenciasSaida: number;
  ajustesAbertura: number;
  saldoFinal: number;
  semSaldoInicial: boolean;
};
type Move = {
  id: string;
  origem: string;
  tipo: string;
  data: string;
  contaId: string;
  conta: string;
  valor: number;
};
type Ledger = {
  meses: {
    mes: string;
    saldoInicial: number;
    entradas: number;
    saidas: number;
    saldoFinal: number;
  }[];
  clienteId: number;
  inicio: string;
  fim: string;
  execucaoId: string | null;
  capturadoEm: string | null;
  contas: Account[];
  movimentos: Move[];
  parcial: boolean;
  verificacao?: {detalhesConferidos:boolean;saldoContas:number;saldoMensal:number;coberturaConfirmada:boolean};
  avisos: string[];
};
export default function FinancialLedgerPanel({
  filter,
  revision = 0,
}: {
  filter: ViewFilter;
  revision?: number;
}) {
  const [data, setData] = useState<Ledger | null>(null),
    [error, setError] = useState("");
  useEffect(() => {
    const c = new AbortController();
    void (async () => {
      try {
        const range = await getFinancialView(
          { ...filter, categoria: "", centro: "" },
          c.signal,
        );
        const q = new URLSearchParams(range.intervalo);
        const r = await fetch(`${API_URL}/api/analises/financeira/caixa?${q}`, {
          credentials: "include",
          cache: "no-store",
          signal: c.signal,
        });
        if (!r.ok)
          throw new Error("Não foi possível calcular os movimentos por conta.");
        const next: Ledger = await r.json();
        if (
          next.clienteId !== filter.clienteId ||
          next.inicio !== range.intervalo.inicio ||
          next.fim !== range.intervalo.fim
        )
          throw new Error("A empresa ou período mudou. Consulte novamente.");
        if (!c.signal.aborted) {
          setData(next);
          setError("");
        }
      } catch (e) {
        if (!c.signal.aborted) {
          setData(null);
          setError(e instanceof Error ? e.message : "Falha no caixa.");
        }
      }
    })();
    return () => c.abort();
  }, [filter, revision]);
  return (
    <section className="fe-card">
      <h2>Movimentos e saldos calculados por conta · API</h2>
      <p>
        Recebimentos e pagamentos, transferências e aberturas têm naturezas
        distintas. Este bloco não usa filtros de categoria ou centro, pois
        transferências e saldos iniciais não são despesas operacionais.
      </p>
      {error && <p role="alert">{error}</p>}
      {!data && !error && <p role="status">Calculando movimentos por conta…</p>}
      {data && (
        <>
          <p>
            {data.inicio} a {data.fim} ·{" "}
            {data.parcial
              ? "Cobertura parcial — consulte os avisos"
              : "Fontes locais processadas; confira o saldo com o banco"}
          </p>
          <p>
            Última captura ampla concluída:{" "}
            {data.capturadoEm
              ? new Date(data.capturadoEm).toLocaleString("pt-BR")
              : "não disponível"}
            . O saldo calculado não certifica o saldo bancário atual.
          </p>
          <p role={data.verificacao?.detalhesConferidos ? "status" : "alert"}>
            {data.verificacao?.detalhesConferidos
              ? "Soma dos detalhes e saldos calculados conferida. Isso não certifica cobertura nem igualdade com o banco."
              : "Conferência dos detalhes não aprovada ou indisponível. Revise antes de usar o saldo."}
          </p>
          <button type="button" onClick={() => exportarCSV("saldos-por-conta.csv", [
            ["Empresa","Início","Fim","ID da conta","Conta","Saldo anterior ao recorte","Recebido","Pago","Transferências de entrada","Transferências de saída","Aberturas no recorte","Saldo calculado","Abertura anterior ao recorte","Detalhes conferidos","Cobertura certificada"],
            ...data.contas.map(x=>[data.clienteId,data.inicio,data.fim,x.id,x.nome,x.saldoInicial,x.recebimentos,x.pagamentos,x.transferenciasEntrada,x.transferenciasSaida,x.ajustesAbertura,x.saldoFinal,x.semSaldoInicial?"Não":"Sim",data.verificacao?.detalhesConferidos?"Sim":"Não","Não"])
          ])}>Exportar saldos por conta em CSV</button>
          {data.avisos.map((x) => (
            <p key={x} role="status">
              {x}
            </p>
          ))}
          <h3>Evolução do caixa calculado</h3>
          <p>
            Entradas e saídas incluem transferências e aberturas; não são os
            indicadores de receita e despesa.
          </p>
          <div style={{ height: 260, minWidth: 0 }}>
            <ResponsiveContainer width="100%" height="100%">
              <ComposedChart data={data.meses}>
                <CartesianGrid stroke="#dbe4ef" vertical={false} />
                <XAxis dataKey="mes" />
                <YAxis
                  width={70}
                  tickFormatter={(v) =>
                    new Intl.NumberFormat("pt-BR", {
                      notation: "compact",
                    }).format(v)
                  }
                />
                <ReferenceLine y={0} stroke="#64748b" />
                <Tooltip formatter={(v) => brl(Number(v))} />
                <Bar
                  dataKey="entradas"
                  name="Entradas de caixa"
                  fill="#2b8a72"
                />
                <Bar dataKey="saidas" name="Saídas de caixa" fill="#bd7735" />
                <Line
                  dataKey="saldoFinal"
                  name="Saldo calculado"
                  stroke="#2864ae"
                  strokeWidth={2}
                  isAnimationActive={false}
                />
              </ComposedChart>
            </ResponsiveContainer>
          </div>
          <div style={{ overflowX: "auto" }}>
            <table>
              <thead>
                <tr>
                  <th>Conta</th>
                  <th>Anterior ao recorte</th>
                  <th>Recebido</th>
                  <th>Pago</th>
                  <th>Transferências +</th>
                  <th>Transferências −</th>
                  <th>Aberturas no recorte</th>
                  <th>Saldo calculado</th>
                </tr>
              </thead>
              <tbody>
                {data.contas.map((x) => (
                  <tr key={x.id}>
                    <th>
                      {x.nome === x.id ? "Nome da conta não informado" : x.nome}
                      {x.semSaldoInicial ? " · sem abertura anterior ao recorte" : ""}
                    </th>
                    {[
                      x.saldoInicial,
                      x.recebimentos,
                      x.pagamentos,
                      x.transferenciasEntrada,
                      x.transferenciasSaida,
                      x.ajustesAbertura,
                      x.saldoFinal,
                    ].map((n, i) => (
                      <td key={i}>{brl(n)}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <details>
            <summary>
              Movimentações do recorte ({data.movimentos.length})
            </summary>
            <button
              type="button"
              onClick={() =>
                exportarCSV("movimentos-por-conta.csv", [
                  ["ID", "Origem", "Tipo", "Data", "ID da conta", "Conta", "Valor"],
                  ...data.movimentos.map((x) => [
                    x.id,
                    x.origem,
                    x.tipo,
                    x.data,
                    x.contaId,
                    x.conta,
                    x.valor,
                  ]),
                ])
              }
            >
              Exportar todos em CSV
            </button>
            <p>
              Prévia de até 100 movimentos; exportação contém o recorte
              completo.
            </p>
            <div style={{ overflowX: "auto" }}>
              <table>
                <thead>
                  <tr>
                    <th>Data</th>
                    <th>Tipo</th>
                    <th>Conta</th>
                    <th>Valor</th>
                  </tr>
                </thead>
                <tbody>
                  {data.movimentos.slice(0, 100).map((x) => (
                    <tr key={x.id}>
                      <td>{x.data}</td>
                      <td>{x.tipo}</td>
                      <td>
                        {x.conta === x.contaId
                          ? "Nome da conta não informado"
                          : x.conta}
                      </td>
                      <td>{brl(x.valor)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </details>
        </>
      )}
    </section>
  );
}
