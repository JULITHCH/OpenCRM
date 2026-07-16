import { apiFetch } from './client'

export type ImportJobStatus =
  | 'PENDING'
  | 'VALIDATING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'COMPLETED_WITH_ERRORS'
  | 'FAILED'
  | 'CANCELLED'

export type ImportJobMode = 'DRY_RUN' | 'EXECUTE'

export type DuplicateStrategy = 'SKIP' | 'UPDATE' | 'CREATE'

/** Zielfelder für den LEAD-Import (LeadRowImporter.targetFields). */
export const LEAD_TARGET_FIELDS = [
  'title',
  'companyName',
  'firstName',
  'lastName',
  'email',
  'phone',
  'source',
  'score',
  'externalId',
] as const

export interface ImportJobOptions {
  headers?: string[]
  duplicateStrategy?: DuplicateStrategy
  skippedRows?: number
  [extension: string]: unknown
}

/** Import-Job laut ImportController.JobResponse. */
export interface ImportJob {
  id: string
  entityType: string
  fileName: string
  format: string
  mode: ImportJobMode
  status: ImportJobStatus
  totalRows: number | null
  processedRows: number
  errorRows: number
  mapping: Record<string, string>
  options: ImportJobOptions
  startedAt: string | null
  finishedAt: string | null
  createdAt: string | null
}

/** Zeilenfehler laut ImportController.ErrorResponse. */
export interface ImportJobError {
  rowNumber: number
  columnName: string | null
  errorCode: string
  message: string
  rawRow: Record<string, string>
}

export interface ImportRunRequest {
  mapping: Record<string, string>
  options: {
    duplicateStrategy: DuplicateStrategy
  }
}

export function uploadImportFile(file: File): Promise<ImportJob> {
  const form = new FormData()
  form.append('file', file)
  form.append('entityType', 'LEAD')
  return apiFetch<ImportJob>('/api/v1/import-jobs', { method: 'POST', body: form })
}

export function getImportJob(id: string, signal?: AbortSignal): Promise<ImportJob> {
  return apiFetch<ImportJob>(`/api/v1/import-jobs/${id}`, { signal })
}

export function getMappingSuggestion(
  id: string,
  signal?: AbortSignal,
): Promise<Record<string, string>> {
  return apiFetch<Record<string, string>>(`/api/v1/import-jobs/${id}/mapping-suggestion`, { signal })
}

export function validateImportJob(id: string, request: ImportRunRequest): Promise<ImportJob> {
  return apiFetch<ImportJob>(`/api/v1/import-jobs/${id}/validate`, { method: 'POST', body: request })
}

export function executeImportJob(id: string, request: ImportRunRequest): Promise<ImportJob> {
  return apiFetch<ImportJob>(`/api/v1/import-jobs/${id}/execute`, { method: 'POST', body: request })
}

export function listImportErrors(id: string, signal?: AbortSignal): Promise<ImportJobError[]> {
  return apiFetch<ImportJobError[]>(`/api/v1/import-jobs/${id}/errors`, { signal })
}
