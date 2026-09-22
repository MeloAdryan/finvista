import { API_URL } from '../config/api'

export type UserProfile = 'ADMIN' | 'CLIENTE'

export interface AuthUser {
  id: number
  nome: string
  email: string
  perfil: UserProfile
}

interface LoginRequest {
  email: string
  senha: string
}

async function lerErro(
  response: Response,
  mensagemPadrao: string,
): Promise<never> {
  const texto = await response
    .text()
    .catch(() => '')

  throw new Error(
    texto || mensagemPadrao,
  )
}

export async function login(
  email: string,
  senha: string,
): Promise<AuthUser> {
  const dados: LoginRequest = {
    email,
    senha,
  }

  const response = await fetch(
    `${API_URL}/api/auth/login`,
    {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(dados),
    },
  )

  if (!response.ok) {
    if (response.status === 400) {
      throw new Error(
        'E-mail ou senha inválidos.',
      )
    }

    return lerErro(
      response,
      'Não foi possível entrar no FinVista.',
    )
  }

  return response.json()
}

export async function obterUsuarioAtual():
Promise<AuthUser | null> {
  const response = await fetch(
    `${API_URL}/api/auth/me`,
    {
      method: 'GET',
      credentials: 'include',
    },
  )

  if (response.status === 401) {
    return null
  }

  if (!response.ok) {
    return lerErro(
      response,
      'Não foi possível verificar a sessão.',
    )
  }

  return response.json()
}

export async function logout(): Promise<void> {
  const response = await fetch(
    `${API_URL}/api/auth/logout`,
    {
      method: 'POST',
      credentials: 'include',
    },
  )

  if (!response.ok && response.status !== 401) {
    return lerErro(
      response,
      'Não foi possível encerrar a sessão.',
    )
  }
}