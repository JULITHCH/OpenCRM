import { apiFetch } from './client'
import type { PageEnvelope } from './types'

export type { PageEnvelope } from './types'

export type LeadStatus =
  | 'NEW'
  | 'ASSIGNED'
  | 'CONTACTED'
  | 'QUALIFIED'
  | 'DISQUALIFIED'
  | 'CONVERTED'

/** Lead laut LeadController.LeadResponse. */
export interface Lead {
  id: string
  title: string
  companyName: string | null
  firstName: string | null
  lastName: string | null
  email: string | null
  phone: string | null
  source: string
  status: LeadStatus
  score: number | null
  ownerId: string | null
  disqualifiedReason: string | null
  externalId: string | null
  createdAt: string | null
}

export interface LeadCreateRequest {
  title: string
  companyName?: string
  lastName?: string
  email?: string
}

export interface LeadListParams {
  q?: string
  status?: LeadStatus
  ownerId?: string
  cursor?: string
  limit?: number
}

export function listLeads(
  params: LeadListParams = {},
  signal?: AbortSignal,
): Promise<PageEnvelope<Lead>> {
  const search = new URLSearchParams()
  if (params.q) search.set('q', params.q)
  if (params.status) search.set('status', params.status)
  if (params.ownerId) search.set('ownerId', params.ownerId)
  if (params.cursor) search.set('cursor', params.cursor)
  if (params.limit) search.set('limit', String(params.limit))
  const query = search.toString()
  return apiFetch<PageEnvelope<Lead>>(`/api/v1/leads${query ? `?${query}` : ''}`, { signal })
}

export function createLead(request: LeadCreateRequest): Promise<Lead> {
  return apiFetch<Lead>('/api/v1/leads', { method: 'POST', body: request })
}

export function assignLead(id: string, userId: string): Promise<Lead> {
  return apiFetch<Lead>(`/api/v1/leads/${id}/assign`, { method: 'POST', body: { userId } })
}

export function markLeadContacted(id: string): Promise<Lead> {
  return apiFetch<Lead>(`/api/v1/leads/${id}/contacted`, { method: 'POST' })
}

export function qualifyLead(id: string): Promise<Lead> {
  return apiFetch<Lead>(`/api/v1/leads/${id}/qualify`, { method: 'POST' })
}

export function disqualifyLead(id: string, reason: string): Promise<Lead> {
  return apiFetch<Lead>(`/api/v1/leads/${id}/disqualify`, { method: 'POST', body: { reason } })
}
