# OpenCRM Frontend

React-SPA für OpenCRM (siehe [ADR-007](../docs/adr/ADR-007-frontend-react-spa.md)).
Stack: Vite, React 18, TypeScript, MUI (E-06), TanStack Query v5, react-router v6,
react-i18next (de/en), keycloak-js (Authorization Code + PKCE, Tokens nur in-memory, E-71).

## Voraussetzungen

- Node.js >= 20.19 (bzw. >= 22.12) und npm
- Laufende Dev-Infrastruktur: `docker compose up -d` in [`infra/`](../infra/docker-compose.yml)
  (PostgreSQL, Keycloak auf Port 8081, MinIO)
- Laufendes Backend auf Port 8080: `mvn spring-boot:run` in [`backend/`](../backend/)

## Entwicklung

```bash
npm install
npm run dev        # http://localhost:5173
```

Login mit dem Dev-Nutzer **dev / dev** (Realm `opencrm`, wird beim Keycloak-Start
aus `infra/keycloak/realm-opencrm.json` importiert).

Das Backend erlaubt kein CORS: der Vite-Dev-Server proxied alle `/api`-Aufrufe
nach `http://localhost:8080` (siehe `vite.config.ts`), der API-Client nutzt dafür
relative Pfade.

Weitere Befehle: `npm run build` (Typecheck + Produktions-Build nach `dist/`),
`npm run lint`, `npm run preview`.

## Umgebungsvariablen

Vorlage: [`.env.example`](.env.example) (lokal als `.env.local` kopieren).

| Variable            | Default                 | Bedeutung |
|---------------------|-------------------------|-----------|
| `VITE_API_URL`      | *(leer)*                | Basis-URL der REST-API. Leer = relative Pfade (Dev-Proxy); nur für Produktions-Builds setzen, wenn die API auf einem anderen Origin liegt. |
| `VITE_KEYCLOAK_URL` | `http://localhost:8081` | Basis-URL des Keycloak-Servers (Realm `opencrm`, Client `opencrm-web`). |
