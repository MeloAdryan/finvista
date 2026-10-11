import { useEffect, useState } from "react";
import type { Cliente } from "../services/clientService";
import {
  consultarContaAzul,
  autorizarContaAzul,
  renovarContaAzul,
  type ContaAzulStatus,
} from "../services/contaAzulConnectionService";
import "../styles/conta-azul-connection.css";

export default function ContaAzulConnectionPanel({
  cliente,
}: {
  cliente: Cliente | null;
}) {
  const [status, setStatus] = useState<ContaAzulStatus | null>(null);
  const [erro, setErro] = useState("");
  const [busy, setBusy] = useState(false);
  const [revision, setRevision] = useState(0);
  const [consultadoEm, setConsultadoEm] = useState(() => Date.now());
  useEffect(() => {
    const controller = new AbortController();
    if (cliente)
      consultarContaAzul(controller.signal)
        .then((value) => {
          if (!controller.signal.aborted) {
            setStatus(value);
            setErro("");
            setConsultadoEm(Date.now());
          }
        })
        .catch((e: unknown) => {
          if (!controller.signal.aborted)
            setErro(
              e instanceof Error ? e.message : "Falha ao consultar conexão.",
            );
        });
    return () => controller.abort();
  }, [cliente, revision]);
  async function agir(action: "autorizar" | "renovar") {
    setBusy(true);
    setErro("");
    try {
      if (action === "renovar") {
        setStatus(await renovarContaAzul());
        setConsultadoEm(Date.now());
      } else {
        const { authorizationUrl } = await autorizarContaAzul();
        const url = new URL(authorizationUrl);
        if (url.origin !== "https://login.contaazul.com")
          throw new Error("Endereço de autorização inválido.");
        window.location.assign(url.href);
      }
    } catch (e) {
      setErro(
        e instanceof Error
          ? e.message
          : "Não foi possível concluir a operação.",
      );
    } finally {
      setBusy(false);
    }
  }
  const pronta = status?.habilitado && status.configurado;
  const expirado = status?.expiraEm
    ? new Date(status.expiraEm).getTime() <= consultadoEm
    : false;
  return (
    <section
      className="ca-connection"
      aria-label="Conexão Conta Azul"
      aria-busy={busy}
    >
      <div className="ca-connection-top">
        <div>
          <h2>Conexão Conta Azul</h2>
          <p>
            {cliente
              ? cliente.nome
              : "Selecione uma empresa para consultar a conexão."}
          </p>
        </div>
        <span className="ca-connection-badge">
          {!status
            ? "Aguardando consulta"
            : !pronta
              ? "Configuração pendente"
              : status.conectado
                ? expirado
                  ? "Renovação necessária"
                  : "Autorização armazenada"
                : "Aguardando autorização"}
        </span>
      </div>
      <p aria-live="polite">
        {status?.mensagem || (cliente && !erro ? "Consultando conexão…" : "")}
      </p>
      {status?.expiraEm && (
        <p>
          Validade do acesso:{" "}
          {new Date(status.expiraEm).toLocaleString("pt-BR")}.
        </p>
      )}
      <p>
        A autorização não importa lançamentos automaticamente. A importação por
        arquivo segue disponível abaixo.
      </p>
      {erro && (
        <p role="alert" className="ca-connection-error">
          {erro}
        </p>
      )}
      <div className="ca-connection-actions">
        <button
          type="button"
          disabled={!cliente || busy}
          onClick={() => {
            setStatus(null);
            setErro("");
            setRevision((v) => v + 1);
          }}
        >
          Atualizar status
        </button>
        {pronta && (
          <button
            type="button"
            disabled={busy}
            onClick={() => void agir("autorizar")}
          >
            {status?.conectado ? "Autorizar novamente" : "Autorizar empresa"}
          </button>
        )}
        {pronta && status?.conectado && (
          <button
            type="button"
            disabled={busy}
            onClick={() => void agir("renovar")}
          >
            Renovar acesso
          </button>
        )}
      </div>
    </section>
  );
}
