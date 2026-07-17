import { apiFetch } from './client'

/**
 * Mandanteneinstellungen laut TenantSettingsController (GET und PATCH sind
 * per @PreAuthorize auf tenant-admin beschränkt — Nicht-Admins erhalten 403).
 */
export interface TenantSettings {
  name: string
  slug: string
  plan: string
  defaultCurrency: string
  settings: Record<string, unknown>
}

export function getTenantSettings(signal?: AbortSignal): Promise<TenantSettings> {
  return apiFetch<TenantSettings>('/api/v1/tenant-settings', { signal })
}

/** PATCH nimmt die Settings-Map direkt entgegen; nur bekannte Schlüssel sind erlaubt. */
export function patchTenantSettings(settings: Record<string, unknown>): Promise<TenantSettings> {
  return apiFetch<TenantSettings>('/api/v1/tenant-settings', { method: 'PATCH', body: settings })
}
