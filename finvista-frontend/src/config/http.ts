import { API_URL } from './api'
import { createApiFetch } from './apiFetchFactory'

export const apiFetch = createApiFetch(API_URL)
