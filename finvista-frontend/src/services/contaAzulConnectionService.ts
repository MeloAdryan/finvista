import { API_URL } from "../config/api";
export interface ContaAzulStatus {
  clienteId: number;
  habilitado: boolean;
  configurado: boolean;
  conectado: boolean;
  expiraEm: string | null;
  mensagem: string;
}
async function request<T>(
  path: string,
  method = "GET",
  signal?: AbortSignal,
): Promise<T> {
  const response = await fetch(
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
      /* resposta sem JSON */
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
