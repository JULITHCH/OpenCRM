import { apiFetch } from './client'

/** Nutzer laut IdentityController.UserResponse. */
export interface User {
  id: string
  displayName: string
  email: string
  role: string
  active: boolean
}

export function listUsers(signal?: AbortSignal): Promise<User[]> {
  return apiFetch<User[]>('/api/v1/users?activeOnly=true', { signal })
}
