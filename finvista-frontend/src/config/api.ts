const apiUrl = import.meta.env.VITE_API_URL

if (!apiUrl) {
  throw new Error(
    'A variavel de ambiente VITE_API_URL nao foi configurada.',
  )
}

export const API_URL = apiUrl.replace(/\/+$/, '')
