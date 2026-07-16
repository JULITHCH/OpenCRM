import { apiFetch } from './client'
import type { PageEnvelope } from './types'

/** Kontakt laut ContactController.ContactResponse. */
export interface Contact {
  id: string
  lastName: string
  firstName: string | null
  accountId: string | null
  email: string | null
  phone: string | null
  position: string | null
  externalId: string | null
  createdAt: string | null
}

export interface ContactUpsertRequest {
  lastName: string
  firstName?: string
  accountId?: string
  email?: string
  phone?: string
  position?: string
  externalId?: string
}

export interface ContactListParams {
  q?: string
  accountId?: string
  cursor?: string
  limit?: number
}

export function listContacts(
  params: ContactListParams = {},
  signal?: AbortSignal,
): Promise<PageEnvelope<Contact>> {
  const search = new URLSearchParams()
  if (params.q) search.set('q', params.q)
  if (params.accountId) search.set('accountId', params.accountId)
  if (params.cursor) search.set('cursor', params.cursor)
  if (params.limit) search.set('limit', String(params.limit))
  const query = search.toString()
  return apiFetch<PageEnvelope<Contact>>(`/api/v1/contacts${query ? `?${query}` : ''}`, { signal })
}

export function createContact(request: ContactUpsertRequest): Promise<Contact> {
  return apiFetch<Contact>('/api/v1/contacts', { method: 'POST', body: request })
}

export function updateContact(id: string, request: Partial<ContactUpsertRequest>): Promise<Contact> {
  return apiFetch<Contact>(`/api/v1/contacts/${id}`, { method: 'PATCH', body: request })
}

export function deleteContact(id: string): Promise<void> {
  return apiFetch<void>(`/api/v1/contacts/${id}`, { method: 'DELETE' })
}
