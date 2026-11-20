import { useEffect, useState } from "react";
import { apiFetch } from "../config/http";
import { API_URL } from "../config/api";
import "../styles/financial-sync-health.css";
type Saude = {
  clienteId: number;
  ultimaSincronizacao: string | null;
  idadeSegundos: number | null;
  atrasada: boolean;
  pendencias: number;
  conferencia: string;
  conferidoEm: string | null;
  conferenciaMensal: string | null;
  conectada: boolean;
  identidadeConfirmada: boolean;
  empresaExternaId: string | null;
};
type Linha = {
  mes: string;
  tipo: string;
  quantidadeApi: number;
  quantidadeBanco: number;
  totalApi: number;
  totalBanco: number;
  pagoApi: number;
  pagoBanco: number;
  abertoApi: number;
  abertoBanco: number;
};
const dinheiro = (v: number) =>
  v.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
function idade(s: number | null) {
  if (s === null) return "Sem sincronizaÃ§Ã£o concluÃ­da";
  if (s < 60) return "SincronizaÃ§Ã£o concluÃ­da hÃ¡ menos de 1 minuto";
  if (s < 3600)
    return `SincronizaÃ§Ã£o concluÃ­da hÃ¡ ${Math.floor(s / 60)} min`;
  if (s < 86400)
    return `SincronizaÃ§Ã£o concluÃ­da hÃ¡ ${Math.floor(s / 3600)} h`;
  return `SincronizaÃ§Ã£o concluÃ­da hÃ¡ ${Math.floor(s / 86400)} dia(s)`;
}
const rotulos: Record<string, string> = {
  CONFERIDO: "Conferido no encerramento",
  DIVERGENTE: "DiferenÃ§as na conferÃªncia",
  NAO_VERIFICAVEL: "ConferÃªncia nÃ£o verificÃ¡vel",
  ESCOPO_INCREMENTAL: "Ãšltima coleta incremental",
  INCOMPLETA: "ConferÃªncia incompleta",
  SEM_CONFERENCIA: "Sem conferÃªncia registrada",
};
export default function FinancialSyncHealth({
  clienteId,
  consolidado = false,
  usuarioAtendido,
}: {
  clienteId?: number;
  consolidado?: boolean;
  usuarioAtendido?: number;
}) {
  const [snapshot, setSnapshot] = useState<{
    key: string;
    rows: Saude[];
    em: number;
  } | null>(null);
  const [erro, setErro] = useState("");
  const [now, setNow] = useState(() => Date.now());
  const key = `${clienteId ?? ""}:${consolidado}:${usuarioAtendido ?? ""}`;
  useEffect(() => {
    const c = new AbortController();
    let busy = false;
    async function load() {
      if (busy) return;
      busy = true;
      try {
        const q = new URLSearchParams({ consolidado: String(consolidado) });
        if (usuarioAtendido !== undefined)
          q.set("usuarioAtendido", String(usuarioAtendido));
        const r = await apiFetch(`${API_URL}/api/analises/sincronizacao?${q}`, {
          credentials: "include",
          signal: c.signal,
        });
        if (!r.ok) throw new Error();
        const rows = (await r.json()) as Saude[];
        if (!c.signal.aborted) {
          setSnapshot({ key, rows, em: Date.now() });
          setNow(Date.now());
          setErro("");
        }
      } catch {
        if (!c.signal.aborted)
          setErro("NÃ£o foi possÃ­vel atualizar o estado da sincronizaÃ§Ã£o.");
      } finally {
        busy = false;
      }
    }
    void load();
    const timer = window.setInterval(() => {
      setNow(Date.now());
      void load();
    }, 60000);
    return () => {
      c.abort();
      window.clearInterval(timer);
    };
  }, [key, consolidado, usuarioAtendido, clienteId]);
  const rows = snapshot?.key === key ? snapshot.rows : [];
  if (!rows.length && !erro) return null;
  return (
    <aside
      className="sync-health"
      aria-label="AtualizaÃ§Ã£o dos dados financeiros"
    >
      {consolidado &&
        rows.some(
          (r, i) =>
            r.empresaExternaId &&
            rows.some(
              (o, j) => i !== j && r.empresaExternaId === o.empresaExternaId,
            ),
        ) && (
          <p role="alert" className="sync-health-warning">
            HÃ¡ cadastros vinculados Ã mesma empresa do Conta Azul nesta
            seleÃ§Ã£o. A soma consolidada pode contar os mesmos lanÃ§amentos
            mais de uma vez. Revise os vÃ­nculos e mantenha os cadastros
            desativados fora da seleÃ§Ã£o.
          </p>
        )}
      {erro && (
        <p role="status">{erro} O estado exibido pode estar desatualizado.</p>
      )}
      {rows.map((r) => {
        const seconds =
          r.idadeSegundos === null
            ? null
            : r.idadeSegundos +
              Math.max(0, Math.floor((now - (snapshot?.em ?? now)) / 1000));
        const atrasada = r.conectada && (seconds === null || seconds > 86400);
        let mensal: Linha[] = [];
        try {
          mensal = JSON.parse(r.conferenciaMensal ?? "[]") as Linha[];
        } catch {
          /* Keep the state label if no report can be read. */
        }
        return (
          <div
            key={r.clienteId}
            className={atrasada ? "sync-health-warning" : ""}
          >
            <p>
              {consolidado ? `Empresa #${r.clienteId} Â· ` : ""}
              {r.conectada
                ? idade(seconds)
                : "Sem conexÃ£o Conta Azul cadastrada"}{" "}
              Â· {r.pendencias} pendÃªncia(s)
              {atrasada &&
                " Â· AtenÃ§Ã£o: atualizaÃ§Ã£o ausente ou hÃ¡ mais de 24 horas"}
            </p>
            {r.conectada && !r.identidadeConfirmada && (
              <p>
                IdentificaÃ§Ã£o da empresa no Conta Azul ainda nÃ£o confirmada
                pelo administrador.
              </p>
            )}
            <details>
              <summary>
                {rotulos[r.conferencia] ?? "Sem conferÃªncia registrada"}
              </summary>
              <p>
                A conferÃªncia compara parcelas por mÃªs de vencimento e tipo no
                encerramento da coleta. NÃ£o confirma extratos bancÃ¡rios nem
                pagamentos mensais por baixa.
              </p>
              {r.conferidoEm && (
                <p>
                  VerificaÃ§Ã£o em{" "}
                  {new Date(r.conferidoEm).toLocaleString("pt-BR")}.
                </p>
              )}
              {mensal.length > 0 && (
                <div
                  className="sync-health-scroll"
                  tabIndex={0}
                  role="region"
                  aria-label="ConferÃªncia mensal, role horizontalmente para ver todas as colunas"
                >
                  <table>
                    <thead>
                      <tr>
                        <th>MÃªs</th>
                        <th>Tipo</th>
                        <th>Qtde API / banco</th>
                        <th>Total líquido API / banco</th>
                        <th>Pago API / banco</th>
                        <th>Aberto API / banco</th>
                      </tr>
                    </thead>
                    <tbody>
                      {mensal.map((l) => (
                        <tr key={`${l.mes}:${l.tipo}`}>
                          <td>{l.mes}</td>
                          <td>{l.tipo}</td>
                          <td>
                            {l.quantidadeApi} / {l.quantidadeBanco}
                          </td>
                          <td>
                            {dinheiro(l.totalApi)} / {dinheiro(l.totalBanco)}
                          </td>
                          <td>
                            {dinheiro(l.pagoApi)} / {dinheiro(l.pagoBanco)}
                          </td>
                          <td>
                            {dinheiro(l.abertoApi)} / {dinheiro(l.abertoBanco)}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </details>
          </div>
        );
      })}
    </aside>
  );
}
