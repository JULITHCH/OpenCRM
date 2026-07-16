import Keycloak from 'keycloak-js'

// Public Client "opencrm-web" im Realm "opencrm" (infra/keycloak/realm-opencrm.json).
// Tokens bleiben ausschliesslich in-memory (E-71) — keine Ablage in local/sessionStorage.
export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081',
  realm: 'opencrm',
  clientId: 'opencrm-web',
})
