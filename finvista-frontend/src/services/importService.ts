import { API_URL } from '../config/api'

export interface ImportPreviewStructure {
  sucesso: boolean
  arquivo: string
  tipoArquivo: string
  colunas: string[]
  linhas: unknown[][]
  quantidadeLinhas: number
}

export interface FinancialTransaction {
  id?: number
  data: string
  descricao: string
  tipo: string
  valor: number
  categoria?: string | null
  centroCusto?: string | null
  origem?: string | null
  documentoReferencia?: string | null
}

export interface ImportPreviewResult {
  sucesso: boolean
  arquivo: string
  quantidade: number
  lancamentos: FinancialTransaction[]
}

export interface ImportResult {
  sucesso: boolean
  arquivo: string
  processados: number
  importados: number
  ignoradosDuplicidade: number
  lancamentos: FinancialTransaction[]
}

export interface ImportedTransactionsResult {
  sucesso: boolean
  quantidade: number
  lancamentos: FinancialTransaction[]
}

interface ApiErrorResponse {
  sucesso?: boolean
  erro?: string
  message?: string
}

async function lerResposta<T>(
  response: Response,
): Promise<T> {
  const data = (await response.json()) as
    | T
    | ApiErrorResponse

  if (!response.ok) {
    const erro = data as ApiErrorResponse

    throw new Error(
      erro.erro ??
        erro.message ??
        'Não foi possível concluir a operação.',
    )
  }

  return data as T
}

function criarFormData(
  arquivo: File,
): FormData {
  const formData = new FormData()

  formData.append('arquivo', arquivo)

  return formData
}

export async function previewEstrutura(
  arquivo: File,
): Promise<ImportPreviewStructure> {
  const response = await fetch(
    `${API_URL}/api/importacoes/preview-estrutura`,
    {
      method: 'POST',
      body: criarFormData(arquivo),
    },
  )

  return lerResposta<ImportPreviewStructure>(
    response,
  )
}

export async function previewCsv(
  arquivo: File,
): Promise<ImportPreviewResult> {
  const response = await fetch(
    `${API_URL}/api/importacoes/csv/preview`,
    {
      method: 'POST',
      body: criarFormData(arquivo),
    },
  )

  return lerResposta<ImportPreviewResult>(
    response,
  )
}

export async function importarCsv(
  arquivo: File,
): Promise<ImportResult> {
  const response = await fetch(
    `${API_URL}/api/importacoes/csv/importar`,
    {
      method: 'POST',
      body: criarFormData(arquivo),
    },
  )

  return lerResposta<ImportResult>(
    response,
  )
}

export async function previewExcel(
  arquivo: File,
): Promise<ImportPreviewResult> {
  const response = await fetch(
    `${API_URL}/api/importacoes/excel/preview`,
    {
      method: 'POST',
      body: criarFormData(arquivo),
    },
  )

  return lerResposta<ImportPreviewResult>(
    response,
  )
}

export async function importarExcel(
  arquivo: File,
): Promise<ImportResult> {
  const response = await fetch(
    `${API_URL}/api/importacoes/excel/importar`,
    {
      method: 'POST',
      body: criarFormData(arquivo),
    },
  )

  return lerResposta<ImportResult>(
    response,
  )
}

export async function listarLancamentosImportados():
Promise<ImportedTransactionsResult> {
  const response = await fetch(
    `${API_URL}/api/importacoes/lancamentos`,
  )

  return lerResposta<ImportedTransactionsResult>(
    response,
  )
}