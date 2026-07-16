import { apiFetch } from './client'

/** Benachrichtigung laut NotificationController.NotificationResponse. */
export interface AppNotification {
  id: string
  type: string
  payload: Record<string, unknown>
  readAt: string | null
  createdAt: string | null
}

export function listNotifications(
  unreadOnly: boolean,
  signal?: AbortSignal,
): Promise<AppNotification[]> {
  const search = new URLSearchParams({ unreadOnly: String(unreadOnly) })
  return apiFetch<AppNotification[]>(`/api/v1/notifications?${search.toString()}`, { signal })
}

export function markNotificationRead(id: string): Promise<AppNotification> {
  return apiFetch<AppNotification>(`/api/v1/notifications/${id}/read`, { method: 'POST' })
}

export function markAllNotificationsRead(): Promise<{ marked: number }> {
  return apiFetch<{ marked: number }>('/api/v1/notifications/read-all', { method: 'POST' })
}
