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

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

function toApiError(error: AxiosError<ApiErrorBody>): ApiError {
  const status = error.response?.status ?? 0
  const code = error.response?.data?.code ?? 'REQUEST_FAILED'
  const message = error.response?.data?.message ?? 'Request failed'
  return new ApiError(status, code, message)
}
