import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { listUsers } from '../api/users'

export const usersQueryKey = ['users'] as const

/**
 * Aktive Nutzer aus GET /api/v1/users — gemeinsame, gecachte Quelle für
 * Namensauflösung (ownerId → displayName) und Zuweisungs-Selects.
 */
export function useUsers() {
  return useQuery({
    queryKey: usersQueryKey,
    queryFn: ({ signal }) => listUsers(signal),
    staleTime: 5 * 60 * 1000,
  })
}

/** Map id → displayName aus dem useUsers-Cache (leer, solange Nutzer noch laden). */
export function useUserNames(): Map<string, string> {
  const { data } = useUsers()
  return useMemo(() => new Map((data ?? []).map((user) => [user.id, user.displayName])), [data])
}
