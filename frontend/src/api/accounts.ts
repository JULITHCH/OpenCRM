import { apiFetch } from './client'
import type { PageEnvelope } from './types'

/** Account laut AccountController.AccountResponse. */
export interface Account {
  id: string
  name: string
  industry: string | null
  website: string | null
  street: string | null
  postalCode: string | null
  city: string | null
  country: string | null
  ownerId: string | null
  externalId: string | null
  createdAt: string | null
}

export interface AccountUpsertRequest {
  name: string
  industry?: string
  website?: string
  street?: string
  postalCode?: string
  city?: string
  country?: string
  ownerId?: string
  externalId?: string
}

export interface AccountListParams {
  q?: string
  cursor?: string
  limit?: number
}

export function listAccounts(
  params: AccountListParams = {},
  signal?: AbortSignal,
): Promise<PageEnvelope<Account>> {
  const search = new URLSearchParams()
  if (params.q) search.set('q', params.q)
  if (params.cursor) search.set('cursor', params.cursor)
  if (params.limit) search.set('limit', String(params.limit))
  const query = search.toString()
  return apiFetch<PageEnvelope<Account>>(`/api/v1/accounts${query ? `?${query}` : ''}`, { signal })
}

export function getAccount(id: string, signal?: AbortSignal): Promise<Account> {
  return apiFetch<Account>(`/api/v1/accounts/${id}`, { signal })
}

export function createAccount(request: AccountUpsertRequest): Promise<Account> {
  return apiFetch<Account>('/api/v1/accounts', { method: 'POST', body: request })
}

export function updateAccount(id: string, request: Partial<AccountUpsertRequest>): Promise<Account> {
  return apiFetch<Account>(`/api/v1/accounts/${id}`, { method: 'PATCH', body: request })
}

export function deleteAccount(id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/accounts/${id}`, { method: 'DELETE' })
}
