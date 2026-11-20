import { apiFetch } from "../config/http";
import { API_URL } from "../config/api";
export interface ContaAzulStatus {
  clienteId: number;
  habilitado: boolean;
  configurado: boolean;
  conectado: boolean;
  expiraEm: string | null;
  mensagem: string;
  status?: "ATIVA" | "REAUTORIZAR" | "NAO_CONECTADA";
  ultimoErro?: string | null;
}
async function request<T>(
  path: string,
  method = "GET",
  signal?: AbortSignal,
): Promise<T> {
  const response = await apiFetch(
    `${API_URL}/api/integracoes/conta-azul/${path}`,
    {
      method,
      credentials: "include",
      signal,
      headers:
        method === "POST" ? { "X-Finvista-Integration": "1" } : undefined,
    },
  );
  if (!response.ok) {
    let message = "Não foi possível consultar a conexão Conta Azul.";
    try {
      const data = await response.json();
      message = data.mensagem || data.erro || data.message || message;
    } catch {
      message = "Não foi possível consultar a conexão Conta Azul.";
    }
    throw new Error(message);
  }
  return response.json() as Promise<T>;
}
export const consultarContaAzul = (signal?: AbortSignal) =>
  request<ContaAzulStatus>("status", "GET", signal);
export const autorizarContaAzul = () =>
  request<{ authorizationUrl: string }>("autorizacao", "POST");
export const renovarContaAzul = () =>
  request<ContaAzulStatus>("renovar", "POST");

export type EmpresaContaAzul = {
  clienteId: number;
  id: string | null;
  documento: string | null;
  nome: string | null;
  verificadaEm: string | null;
  confirmadaEm: string | null;
};
export async function empresaContaAzul(
  acao: "consultar" | "verificar" | "confirmar",
  pedido?: EmpresaContaAzul,
): Promise<EmpresaContaAzul> {
  const r = await apiFetch(
    `${API_URL}/api/integracoes/conta-azul/empresa${acao === "consultar" ? "" : `/${acao}`}`,
    {
      method: acao === "consultar" ? "GET" : "POST",
      credentials: "include",
      headers:
        acao === "consultar"
          ? undefined
          : {
              "X-Finvista-Integration": "1",
              "Content-Type": "application/json",
            },
      body:
        acao === "confirmar"
          ? JSON.stringify({
              clienteId: pedido?.clienteId,
              id: pedido?.id,
              documento: pedido?.documento,
              confirmo: true,
            })
          : undefined,
    },
  );
  if (!r.ok) {
    let mensagem = "Não foi possível verificar ou confirmar a empresa.";
    try {
      const d = await r.json();
      mensagem = d.mensagem || d.erro || d.message || mensagem;
    } catch {
      /* use safe fallback */
    }
    throw new Error(mensagem);
  }
  return r.json() as Promise<EmpresaContaAzul>;
}
