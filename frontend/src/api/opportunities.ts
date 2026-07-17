import { apiFetch } from './client'
import type { PageEnvelope } from './types'

export type OpportunityStatus = 'OPEN' | 'WON' | 'LOST'

/** Opportunity laut OpportunityController.OpportunityResponse. */
export interface Opportunity {
  id: string
  accountId: string
  pipelineId: string
  stageId: string
  name: string
  amount: number | null
  isEstimated: boolean
  currency: string
  expectedCloseDate: string | null
  ownerId: string | null
  leadId: string | null
  status: OpportunityStatus
  wonAt: string | null
  lostAt: string | null
  lostReason: string | null
  createdAt: string | null
}

export interface OpportunityListParams {
  status?: OpportunityStatus
  accountId?: string
  cursor?: string
  limit?: number
}

export function listOpportunities(
  params: OpportunityListParams = {},
  signal?: AbortSignal,
): Promise<PageEnvelope<Opportunity>> {
  const search = new URLSearchParams()
  if (params.status) search.set('status', params.status)
  if (params.accountId) search.set('accountId', params.accountId)
  if (params.cursor) search.set('cursor', params.cursor)
  if (params.limit) search.set('limit', String(params.limit))
  const query = search.toString()
  return apiFetch<PageEnvelope<Opportunity>>(`/api/v1/opportunities${query ? `?${query}` : ''}`, {
    signal,
  })
}

/** Pipeline-Stage laut PipelineController.StageResponse. */
export interface PipelineStage {
  id: string
  name: string
  sortOrder: number
  probability: number
  isWon: boolean
  isLost: boolean
}

/** Pipeline laut PipelineController.PipelineResponse (inkl. Stages). */
export interface Pipeline {
  id: string
  name: string
  isDefault: boolean
  stages: PipelineStage[]
}

export function listPipelines(signal?: AbortSignal): Promise<Pipeline[]> {
  return apiFetch<Pipeline[]>('/api/v1/pipelines', { signal })
}
