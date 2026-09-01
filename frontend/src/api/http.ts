import axios, { type AxiosError } from 'axios'

declare module 'axios' {
  interface AxiosRequestConfig {
    skipAuthRedirect?: boolean
  }
}

const TOKEN_KEY = 'kiki.accessToken'

export function getStoredAccessToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setStoredAccessToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearStoredAccessToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

let onUnauthorized: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 8000,
})

http.interceptors.request.use((config) => {
  const token = getStoredAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    const status = error.response?.status
    const url = error.config?.url ?? ''
    const isCredentialRequest = url.includes('/auth/login') || url.includes('/auth/register')
    const skipRedirect = Boolean(error.config?.skipAuthRedirect)

    if (status === 401 && !isCredentialRequest && !skipRedirect) {
      onUnauthorized?.()
    }

    return Promise.reject(toApiError(error))
  },
)

export interface ApiErrorBody {
  code?: string
  message?: string
  timestamp?: string
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly requestId?: string

  constructor(status: number, code: string, message: string, requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.requestId = requestId
  }
}

export function requestIdFromHeaders(headers: unknown): string | undefined {
  if (!headers || typeof headers !== 'object') {
    return undefined
  }
  const record = headers as Record<string, unknown>
  const raw = record['x-request-id'] ?? record['X-Request-ID']
  if (typeof raw === 'string' && raw.trim()) {
    return raw.trim()
  }
  if (Array.isArray(raw) && typeof raw[0] === 'string' && raw[0].trim()) {
    return raw[0].trim()
  }
  return undefined
}

function toApiError(error: AxiosError<ApiErrorBody>): ApiError {
  const status = error.response?.status ?? 0
  const code = error.response?.data?.code ?? 'REQUEST_FAILED'
  const message = error.response?.data?.message ?? 'Request failed'
  const requestId = requestIdFromHeaders(error.response?.headers)
  if (import.meta.env.DEV && requestId) {
    console.warn(`[kiki] API error requestId=${requestId} status=${status} code=${code}`)
  }
  return new ApiError(status, code, message, requestId)
}
