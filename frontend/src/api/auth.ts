import { http, type ApiError } from './http'

export interface User {
  id: number
  username: string
  email: string
  displayName: string
  role: string
  createdAt?: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: User
}

export interface RegisterPayload {
  username: string
  email: string
  password: string
}

export interface LoginPayload {
  identifier: string
  password: string
}

export function register(payload: RegisterPayload) {
  return http.post<User>('/auth/register', payload)
}

export function login(payload: LoginPayload) {
  return http.post<LoginResponse>('/auth/login', payload)
}

export function getCurrentUser() {
  return http.get<User>('/users/me', {
    skipAuthRedirect: true,
  })
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof Error && error.name === 'ApiError'
}
