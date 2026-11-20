import {
  empresaContaAzul,
  type EmpresaContaAzul,
} from "../services/contaAzulConnectionService";
import "../styles/financial-sync-health.css";
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
  const [revision, setRevision] = useState(0);
  return (
    <ContaAzulConnectionDetails
      key={`${cliente?.id ?? "sem-empresa"}:${revision}`}
      cliente={cliente}
      onRefresh={() => setRevision((value) => value + 1)}
    />
  );
}

function ContaAzulConnectionDetails({
  cliente,
  onRefresh,
}: {
  cliente: Cliente | null;
  onRefresh: () => void;
}) {
  const [status, setStatus] = useState<ContaAzulStatus | null>(null);
  const [erro, setErro] = useState("");
  const [empresa, setEmpresa] = useState<EmpresaContaAzul | null>(null);
  const [confirmoEmpresa, setConfirmoEmpresa] = useState(false);
  useEffect(() => {
    let ativa = true;
    if (status?.conectado)
      void empresaContaAzul("consultar")
        .then((e) => {
          if (ativa && e.clienteId === cliente?.id) {
            setEmpresa(e);
            setConfirmoEmpresa(false);
          }
        })
        .catch(() => {});
    return () => {
      ativa = false;
    };
  }, [cliente?.id, status?.conectado]);
  async function identificar(acao: "verificar" | "confirmar") {
    setBusy(true);
    setErro("");
    try {
      const e = await empresaContaAzul(acao, empresa ?? undefined);
      if (e.clienteId === cliente?.id) {
        setEmpresa(e);
        setConfirmoEmpresa(false);
      }
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Falha na identificação.");
    } finally {
      setBusy(false);
    }
  }
  const [busy, setBusy] = useState(false);
  const [consultadoEm, setConsultadoEm] = useState(() => Date.now());
  useEffect(() => {
    const controller = new AbortController();
    if (cliente)
      consultarContaAzul(controller.signal)
        .then((value) => {
          if (!controller.signal.aborted && value.clienteId === cliente?.id) {
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
  }, [cliente]);
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
  const reautorizar = status?.status === "REAUTORIZAR";
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
              : reautorizar
                ? "Autorize novamente"
                : status.conectado
                  ? expirado
                    ? "Renovação necessária"
                    : "Autorização armazenada"
                  : "Aguardando autorização"}
        </span>
      </div>
      {reautorizar && (
        <p role="alert" className="ca-connection-error">
          {status?.ultimoErro || "A conexão precisa de nova autorização."} A
          automação foi pausada. Após autorizar, revise e ative novamente a
          configuração da automação.
        </p>
      )}
      <p aria-live="polite">
        {pronta && status?.conectado
          ? "Conexão autorizada. Acompanhe os resultados e as pendências na aba Automação."
          : status?.mensagem ||
            (cliente && !erro ? "Consultando conexão…" : "")}
      </p>
      {pronta && !status?.conectado && (
        <button
          type="button"
          className="ca-connect-primary"
          disabled={busy}
          onClick={() => void agir("autorizar")}
        >
          {reautorizar ? "Autorizar novamente" : "Conectar Conta Azul"}
        </button>
      )}
      {erro && (
        <p role="alert" className="ca-connection-error">
          {erro}
        </p>
      )}
      {status?.conectado && (
        <div className="ca-company-identity">
          <h3>Empresa autorizada no Conta Azul</h3>
          {empresa?.clienteId === cliente?.id && empresa?.id ? (
            <>
              <p>
                <strong>{empresa.nome}</strong> · CNPJ {empresa.documento} ·
                Identificador {empresa.id}
              </p>
              <p>
                {empresa.confirmadaEm
                  ? "Vínculo confirmado pelo administrador."
                  : "Confira se esta é a empresa correspondente ao cadastro selecionado no FinVista."}
              </p>
              {!empresa.confirmadaEm && (
                <>
                  <label>
                    <input
                      type="checkbox"
                      checked={confirmoEmpresa}
                      onChange={(e) => setConfirmoEmpresa(e.target.checked)}
                    />{" "}
                    Confirmo que esta empresa do Conta Azul corresponde ao
                    cadastro {cliente?.nome}.
                  </label>
                  <button
                    type="button"
                    disabled={busy || !confirmoEmpresa}
                    onClick={() => void identificar("confirmar")}
                  >
                    Confirmar vínculo
                  </button>
                </>
              )}
            </>
          ) : (
            <p>
              Identificação pendente. Verifique e confirme o vínculo antes de
              ativar a automação.
            </p>
          )}
          <button
            type="button"
            disabled={busy}
            onClick={() => void identificar("verificar")}
          >
            Verificar empresa no Conta Azul
          </button>
          <p>
            Após confirmar, ative a automação na aba correspondente. Cadastros
            do mesmo grupo devem ser conferidos separadamente.
          </p>
        </div>
      )}
      <details className="ca-connection-manage">
        <summary>Gerenciar conexão</summary>
        <p>
          A autorização fica armazenada por empresa. A automação renova o acesso
          quando necessário; use as ações abaixo para conferir ou recuperar a
          conexão.
        </p>
        {status?.expiraEm && (
          <p>
            Validade do acesso atual:{" "}
            {new Date(status.expiraEm).toLocaleString("pt-BR")}.
          </p>
        )}
        <div className="ca-connection-actions">
          <button
            type="button"
            disabled={!cliente || busy}
            onClick={() => {
              onRefresh();
            }}
          >
            Atualizar status
          </button>
          {pronta && status?.conectado && (
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
      </details>
    </section>
  );
}
