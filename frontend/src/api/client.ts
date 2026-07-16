import { keycloak } from '../auth/keycloak'

// Leer im Dev-Modus: relative Pfade laufen über den Vite-Dev-Proxy (vite.config.ts),
// weil das Backend kein CORS erlaubt. VITE_API_URL greift nur für Produktions-Builds.
const baseUrl = import.meta.env.VITE_API_URL ?? ''

/** Problem Details nach RFC 9457 (application/problem+json). */
export interface ProblemDetails {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  [extension: string]: unknown
}

export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemDetails

  constructor(status: number, message: string, problem?: ProblemDetails) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

/** Nutzerfreundliche Meldung aus einem unbekannten Fehler (z. B. für Snackbars). */
export function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }
  return String(error)
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
}

/**
 * Kleiner fetch-Wrapper: erneuert vor jedem Aufruf das Access-Token (mindestens
 * 30 s Restlaufzeit), setzt den Authorization-Header und wirft bei !ok einen
 * ApiError mit RFC-9457-Problem-Details, sofern das Backend welche liefert.
 */
export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  try {
    await keycloak.updateToken(30)
  } catch {
    // Refresh nicht mehr möglich (Session abgelaufen) → erneuter Login.
    await keycloak.login()
    throw new ApiError(401, 'Session abgelaufen')
  }

  const headers: Record<string, string> = {
    Authorization: `Bearer ${keycloak.token}`,
  }
  // FormData wird unverändert durchgereicht — den Content-Type (multipart/form-data
  // inkl. Boundary) setzt der Browser selbst.
  const isFormData = options.body instanceof FormData
  if (options.body !== undefined && !isFormData) {
    headers['Content-Type'] = 'application/json'
  }

  const response = await fetch(`${baseUrl}${path}`, {
    method: options.method ?? 'GET',
    headers,
    body: isFormData
      ? (options.body as FormData)
      : options.body !== undefined
        ? JSON.stringify(options.body)
        : undefined,
    signal: options.signal,
  })

  if (!response.ok) {
    let problem: ProblemDetails | undefined
    const contentType = response.headers.get('Content-Type') ?? ''
    if (contentType.includes('json')) {
      try {
        problem = (await response.json()) as ProblemDetails
      } catch {
        // Kein parsebarer Body — Fallback auf den HTTP-Status.
      }
    }
    const message = problem?.detail ?? problem?.title ?? `HTTP ${response.status}`
    throw new ApiError(response.status, message, problem)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
