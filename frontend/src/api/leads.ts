import { apiFetch } from './client'

export type LeadStatus =
  | 'NEW'
  | 'ASSIGNED'
  | 'CONTACTED'
  | 'QUALIFIED'
  | 'DISQUALIFIED'
  | 'CONVERTED'

export interface Lead {
  id: string
  title: string
  companyName: string | null
  lastName: string | null
  email: string | null
  status: LeadStatus
  ownerId: string | null
}

export interface PageEnvelope<T> {
  items: T[]
  nextCursor: string | null
}

export interface LeadCreateRequest {
  title: string
  companyName?: string
  lastName?: string
  email?: string
}

export function listLeads(signal?: AbortSignal): Promise<PageEnvelope<Lead>> {
  return apiFetch<PageEnvelope<Lead>>('/api/v1/leads', { signal })
}

export function createLead(request: LeadCreateRequest): Promise<Lead> {
  return apiFetch<Lead>('/api/v1/leads', { method: 'POST', body: request })
}
