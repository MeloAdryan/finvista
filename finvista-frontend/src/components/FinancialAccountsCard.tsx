import { useEffect, useState } from "react";
import { API_URL } from "../config/api";
import { brl } from "../services/dashboardAnalysisService";

type Saldos = {
  clienteId: number;
  estado: string;
  saldo: number | null;
  consultadoEm: string | null;
  desatualizado: boolean;
  mensagem: string;
  contasExcluidas: number;
  diagnostico?: {
    codigo: string;
    etapa: string;
    httpStatus: number | null;
  } | null;
  contas: { id: string; nome: string; tipo: string; saldo: number }[];
};
const horario = (data: string) =>
  new Date(data).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo" });

export default function FinancialAccountsCard({
  clienteId,
  versao,
}: {
  clienteId: number;
  versao: number;
}) {
  const [resultado, setResultado] = useState<Saldos | null>(null);
  const [erro, setErro] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      try {
        const response = await fetch(
          `${API_URL}/api/dashboard/contas-financeiras`,
          {
            credentials: "include",
            cache: "no-store",
            signal: controller.signal,
          },
        );
        if (!response.ok)
          throw new Error(
            response.status === 401
              ? "Entre novamente no FinVista."
              : "Não foi possível consultar os saldos.",
          );
        const dados: Saldos = await response.json();
        if (dados.clienteId !== clienteId)
          throw new Error("O cliente mudou. Atualize o painel.");
        if (dados.saldo !== null && !Number.isFinite(dados.saldo))
          throw new Error("Saldo inválido na resposta.");
        if (!controller.signal.aborted) {
          setResultado(dados);
          setErro("");
        }
      } catch (e) {
        if (!controller.signal.aborted)
          setErro(e instanceof Error ? e.message : "Falha na consulta.");
      }
    })();
    return () => controller.abort();
  }, [clienteId, versao]);
  return (
    <article className="fp-card fp-accounts">
      <div className="fp-card-heading">
        <h3>Contas financeiras</h3>
        <span aria-hidden="true">▤</span>
      </div>
      <strong
        className={`fp-account-total ${resultado?.saldo == null ? "fp-account-unavailable" : ""}`}
      >
        {resultado?.saldo != null
          ? brl(resultado.saldo)
          : erro || resultado
            ? "Saldo não disponível"
            : "Consultando saldos…"}
      </strong>
      <p className="fp-account-subtitle">Bancos, caixa e aplicações</p>
      {resultado?.consultadoEm && (
        <p className="fp-account-time">
          Consultado em {horario(resultado.consultadoEm)} (Brasília).
        </p>
      )}
      {(erro || resultado?.desatualizado) && (
        <p className="fp-accounts-warning" role="status">
          {resultado?.saldo != null ? "Último saldo conhecido. " : ""}
          {erro || resultado?.mensagem}
        </p>
      )}
      {!erro &&
        resultado &&
        !resultado.desatualizado &&
        resultado.saldo === null && <p role="status">{resultado.mensagem}</p>}
      {resultado?.estado === "SEM_CONTAS" && <p>{resultado.mensagem}</p>}
      {!!resultado?.contas.length && (
        <details>
          <summary>Ver contas ({resultado.contas.length})</summary>
          <ul>
            {resultado.contas.map((c) => (
              <li key={c.id}>
                <span>{c.nome}</span>
                <strong>{brl(c.saldo)}</strong>
              </li>
            ))}
          </ul>
        </details>
      )}
      {!!resultado?.contasExcluidas && (
        <p>{resultado.contasExcluidas} contas de outros tipos fora do total.</p>
      )}
      {resultado?.diagnostico && (
        <details className="fp-diagnostic">
          <summary>Detalhes da consulta</summary>
          <p>
            Código: {resultado.diagnostico.codigo} · Etapa:{" "}
            {resultado.diagnostico.etapa}
            {resultado.diagnostico.httpStatus != null
              ? ` · HTTP ${resultado.diagnostico.httpStatus}`
              : ""}
          </p>
          <p>
            Informe estes detalhes ao revisar a integração. Nenhuma credencial é
            exibida.
          </p>
        </details>
      )}
      <details className="fp-account-source">
        <summary>Fonte e atualização</summary>
        <p>Fonte: Conta Azul. Consultas reutilizadas por até 2 minutos.</p>
      </details>
    </article>
  );
}
