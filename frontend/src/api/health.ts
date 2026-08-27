import { http } from './http'

export interface HealthResponse {
  status: string
}

export function getHealth() {
  return http.get<HealthResponse>('/health')
}
