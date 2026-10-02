import { API_URL } from '../config/api'

export interface Cliente {
  id: number
  nome: string
  ativo: boolean
  dataCriacao: string
}

interface ApiErrorResponse {
  erro?: string
  message?: string
}

async function lerErro(
  response: Response,
  mensagemPadrao: string,
): Promise<never> {
  let mensagem = mensagemPadrao

  try {
    const data = (await response.json()) as ApiErrorResponse

    if (data.erro) {
      mensagem = data.erro
    } else if (data.message) {
      mensagem = data.message
    }
  } catch {
    // Mantém a mensagem padrão caso a resposta não seja JSON.
  }

  throw new Error(mensagem)
}

export async function listarClientes(): Promise<Cliente[]> {
  const response = await fetch(`${API_URL}/api/clientes`, {
    method: 'GET',
    credentials: 'include',
  })

  if (!response.ok) {
    return lerErro(
      response,
      'Não foi possível carregar os clientes.',
    )
  }

  return (await response.json()) as Cliente[]
}

export async function obterClienteAtual(): Promise<Cliente | null> {
  const response = await fetch(
    `${API_URL}/api/clientes/contexto`,
    {
      method: 'GET',
      credentials: 'include',
    },
  )

  if (response.ok) {
    return (await response.json()) as Cliente
  }

  if (
    response.status === 400 ||
    response.status === 403 ||
    response.status === 404 ||
    response.status === 409
  ) {
    return null
  }

  return lerErro(
    response,
    'Não foi possível obter o cliente atual.',
  )
}

export async function selecionarCliente(
  clienteId: number,
): Promise<Cliente> {
  const response = await fetch(
    `${API_URL}/api/clientes/contexto/${clienteId}`,
    {
      method: 'POST',
      credentials: 'include',
    },
  )

  if (!response.ok) {
    return lerErro(
      response,
      'Não foi possível selecionar o cliente.',
    )
  }

  return (await response.json()) as Cliente
}

export async function limparClienteAtual(): Promise<void> {
  const response = await fetch(
    `${API_URL}/api/clientes/contexto`,
    {
      method: 'DELETE',
      credentials: 'include',
    },
  )

  if (!response.ok && response.status !== 204) {
    return lerErro(
      response,
      'Não foi possível limpar o cliente selecionado.',
    )
  }
}