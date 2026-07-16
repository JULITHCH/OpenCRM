/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Basis-URL der REST-API. Leer = relative Pfade (Dev-Proxy, siehe vite.config.ts). */
  readonly VITE_API_URL?: string
  /** Basis-URL des Keycloak-Servers (Default: http://localhost:8081). */
  readonly VITE_KEYCLOAK_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
