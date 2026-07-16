/** Cursor-basierte Listen-Antwort des Backends (shared/web/PageEnvelope). */
export interface PageEnvelope<T> {
  items: T[]
  nextCursor: string | null
}
