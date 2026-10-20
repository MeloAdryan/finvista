import FinancialAccountsCard from "./FinancialAccountsCard";
import { useEffect, useState } from "react";
import { API_URL } from "../config/api";
import { brl, dataBR } from "../services/dashboardAnalysisService";
import "../styles/financial-position.css";

type Grupo = { vencidos: number; aVencer: number };
type Posicao = {
  clienteId: number;
  referencia: string;
  receber: Grupo;
  pagar: Grupo;
  semValorAberto: number;
  semVencimento: number;
  cobertura?: {
    origem: string;
    semValorAberto: number;
    semVencimento: number;
    primeiraDataAnalise: string | null;
    ultimaDataAnalise: string | null;
  }[];
};

export default function FinancialPositionPanel({
  clienteId,
}: {
  clienteId: number;
}) {
  const [dados, setDados] = useState<Posicao | null>(null);
  const [erro, setErro] = useState("");
  const [versao, setVersao] = useState(0);
  const [carregando, setCarregando] = useState(true);
  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      try {
        const resposta = await fetch(
          `${API_URL}/api/dashboard/posicao-financeira`,
          {
            credentials: "include",
            cache: "no-store",
            signal: controller.signal,
          },
        );
        if (!resposta.ok)
          throw new Error(
            resposta.status === 401
              ? "Entre novamente no FinVista."
              : "Não foi possível consultar os valores em aberto.",
          );
        const resultado: Posicao = await resposta.json();
        if (resultado.clienteId !== clienteId)
          throw new Error("O cliente selecionado mudou. Atualize o painel.");
        if (!controller.signal.aborted) setDados(resultado);
      } catch (e) {
        if (!controller.signal.aborted)
          setErro(e instanceof Error ? e.message : "Falha na consulta.");
      } finally {
        if (!controller.signal.aborted) setCarregando(false);
      }
    })();
    return () => controller.abort();
  }, [clienteId, versao]);
  return (
    <section
      className="fp-panel"
      aria-label="Posição financeira"
      aria-busy={carregando}
    >
      <div className="fp-heading">
        <div>
          <h2>Posição financeira</h2>
          <p>
            {dados ? `Referência: ${dataBR(dados.referencia)}. ` : ""}Todos os
            vencimentos e categorias do cliente.
          </p>
        </div>
        <button
          type="button"
          disabled={carregando}
          onClick={() => {
            setCarregando(true);
            setErro("");
            setDados(null);
            setVersao((v) => v + 1);
          }}
        >
          {carregando ? "Consultando…" : "Atualizar"}
        </button>
      </div>
      {erro && <p role="alert">{erro}</p>}
      {dados && (dados.semValorAberto > 0 || dados.semVencimento > 0) && (
        <p className="fp-coverage-warning" role="status">
          <strong>Posição parcial de contas a receber e a pagar.</strong> Há
          lançamentos sem saldo em aberto ou vencimento informado. Os valores
          abaixo incluem somente os registros com dados suficientes.
        </p>
      )}

      <div className="fp-grid">
        <FinancialAccountsCard
          key={`${clienteId}:${versao}`}
          clienteId={clienteId}
          versao={versao}
        />
        {(
          [
            ["A receber", dados?.receber],
            ["A pagar", dados?.pagar],
          ] as const
        ).map(([titulo, grupo]) => (
          <article className="fp-card" key={titulo}>
            <h3>{titulo}</h3>
            <dl>
              <div>
                <dt>Vencidos</dt>
                <dd
                  className={
                    grupo && grupo.vencidos > 0 ? "fp-overdue" : undefined
                  }
                >
                  {grupo ? brl(grupo.vencidos) : "—"}
                </dd>
              </div>
              <div>
                <dt>A vencer</dt>
                <dd>{grupo ? brl(grupo.aVencer) : "—"}</dd>
              </div>
            </dl>
          </article>
        ))}
      </div>
      <p className="fp-note">
        Valores em aberto registrados no FinVista. A vencer inclui vencimentos
        na data de referência. Os filtros dos gráficos não alteram esta posição.
      </p>
      {dados && (dados.semValorAberto > 0 || dados.semVencimento > 0) && (
        <details className="fp-coverage">
          <summary>
            Conferir dados fora dos totais (
            {dados.semValorAberto + dados.semVencimento})
          </summary>
          <p className="fp-note">
            Sem saldo em aberto informado: {dados.semValorAberto}. Com saldo
            positivo, mas sem vencimento: {dados.semVencimento}. O levantamento
            considera todo o histórico do cliente; não significa que esses
            registros estejam em aberto ou pagos.
          </p>
          {!!dados.cobertura?.length && (
            <ul>
              {dados.cobertura.map((c) => (
                <li key={c.origem}>
                  <strong>
                    {c.origem === "NAO_INFORMADA"
                      ? "Origem não informada"
                      : c.origem}
                  </strong>
                  <span>
                    Sem saldo: {c.semValorAberto} · Sem vencimento:{" "}
                    {c.semVencimento}
                  </span>
                  {c.primeiraDataAnalise && c.ultimaDataAnalise && (
                    <span>
                      Datas de análise: {dataBR(c.primeiraDataAnalise)} a{" "}
                      {dataBR(c.ultimaDataAnalise)}
                    </span>
                  )}
                </li>
              ))}
            </ul>
          )}
          <p className="fp-note">
            Importações genéricas de CSV/Excel podem conter apenas valores
            contábeis. Para completar a posição, confira a origem e use uma
            exportação com valores em aberto e vencimentos, passando pelo plano
            de conciliação para evitar duplicidades.
          </p>
        </details>
      )}
    </section>
  );
}
