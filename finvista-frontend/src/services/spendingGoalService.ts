const API_URL =
  import.meta.env.VITE_API_URL || 'http://localhost:8080'

export type SpendingGoalStatus =
  | 'NORMAL'
  | 'ALERTA'
  | 'EXCEDIDA'

export interface SpendingGoal {
  id: number
  tipo: string
  dataInicio: string
  dataFim: string
  valorLimite: number
  gastoAtual: number
  percentualUtilizado: number
  saldoMeta: number
  percentualAlerta: number
  status: SpendingGoalStatus
}

export interface CreateSpendingGoalRequest {
  tipo: string
  dataInicio: string
  dataFim: string
  valorLimite: number
  percentualAlerta: number
}

export async function listarMetas(): Promise<SpendingGoal[]> {
  const response = await fetch(
    `${API_URL}/api/metas-gastos`,
  )

  if (!response.ok) {
    throw new Error(
      'Não foi possível carregar as metas.',
    )
  }

  return response.json()
}

export async function criarMeta(
  dados: CreateSpendingGoalRequest,
): Promise<void> {
  const response = await fetch(
    `${API_URL}/api/metas-gastos`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(dados),
    },
  )

  if (!response.ok) {
    const mensagem = await response
      .text()
      .catch(() => '')

    throw new Error(
      mensagem ||
        'Não foi possível criar a meta.',
    )
  }
}