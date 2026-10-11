import { apiFetch } from "../config/http";
import { API_URL } from "../config/api";
import type { Cliente } from "./clientService";
export interface AdminUser {
  id: number;
  nome: string;
  email: string;
  perfil: "ADMIN" | "CLIENTE";
  clienteId: number | null;
  clienteNome: string | null;
  ativo: boolean;
}
export interface NewUser {
  nome: string;
  email: string;
  senha: string;
  perfil: "ADMIN" | "CLIENTE";
  clienteId: number | null;
}
async function request<T>(
  path: string,
  body?: unknown,
  method = body === undefined ? "GET" : "POST",
): Promise<T> {
  const r = await apiFetch(`${API_URL}/api/admin/${path}`, {
    method,
    credentials: "include",
    cache: "no-store",
    headers: { "Content-Type": "application/json", "X-Finvista-Admin": "1" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!r.ok) {
    let mensagem =
      r.status === 403
        ? "Acesso permitido somente a ADMIN."
        : "Não foi possível concluir a operação.";
    try {
      const e = await r.json();
      if (typeof e.mensagem === "string") mensagem = e.mensagem;
      else if (typeof e.message === "string") mensagem = e.message;
    } catch {
      /* resposta sem JSON */
    }
    throw new Error(mensagem);
  }
  return r.json();
}
export const criarEmpresa = (nome: string) =>
  request<Cliente>("clientes", { nome });
export const criarUsuarioAdmin = (dados: NewUser) =>
  request<AdminUser>("usuarios", dados);
export const listarUsuariosAdmin = () => request<AdminUser[]>("usuarios");

export const listarEmpresasAdmin = () => request<Cliente[]>("clientes");
export const atualizarEmpresaAdmin = (
  original: Cliente,
  nome: string,
  ativo: boolean,
) =>
  request<Cliente>(
    `clientes/${original.id}`,
    {
      nome,
      ativo,
      nomeAnterior: original.nome,
      ativoAnterior: original.ativo,
    },
    "PUT",
  );
