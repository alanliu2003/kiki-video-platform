import type { RouteLocationRaw } from 'vue-router'

export function userProfilePath(userId: number | string): string {
  return `/users/${userId}`
}

export function userProfileLocation(userId: number | string): RouteLocationRaw {
  return { name: 'user-profile', params: { id: String(userId) } }
}
