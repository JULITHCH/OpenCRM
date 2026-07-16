import { createContext } from 'react'
import type Keycloak from 'keycloak-js'

export interface AuthContextValue {
  keycloak: Keycloak
  /** Anzeigename aus dem ID-/Access-Token (name bzw. preferred_username). */
  displayName: string
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
