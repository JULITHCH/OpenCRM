# Keycloak-Realm-Konfiguration (Config-as-Code)

`realm-opencrm.json` ist die versionierte Realm-Definition (E-61). Sie wird in der
Entwicklung von `docker-compose` per `--import-realm` geladen und in Staging/Produktion
über [keycloak-config-cli](https://github.com/adorsys/keycloak-config-cli) importiert.

## Sichere Defaults (bereits gesetzt)

- **Kein Direct Access Grant / Implicit Flow** am Public-Client `opencrm-web` — nur
  Authorization Code + PKCE (docs/05 Abschnitt 3.1).
- **Client-Secret des Service-Clients** `opencrm-api` kommt aus der Umgebungsvariablen
  `OPENCRM_API_CLIENT_SECRET` (Default nur für Dev). In Produktion aus dem Secret-Store setzen.
- **Redirect-URIs / Web-Origins** über `OPENCRM_WEB_REDIRECT_URIS` / `OPENCRM_WEB_ORIGINS`
  konfigurierbar (Platzhalter-Domain E-23).
- **Audience-Mapper**: Tokens des Web-Clients tragen `aud=opencrm-api`, damit das Backend
  bei gesetztem `OPENCRM_EXPECTED_AUDIENCE` die Audience prüfen kann (docs/05 Abschnitt 3.2).

## Vor dem Produktions-Import zu erledigen

1. **Seed-Nutzer `dev` entfernen** — der Block `users[]` ist reine Dev-Bequemlichkeit.
   In Produktion werden Nutzer über Keycloak-Organizations / IdP-Föderation provisioniert.
2. `OPENCRM_API_CLIENT_SECRET` aus dem Secret-Store injizieren (nie das Dev-Default verwenden).
3. `OPENCRM_WEB_REDIRECT_URIS` / `OPENCRM_WEB_ORIGINS` auf die echten Domains setzen.
4. Optional MFA-Policies je Organization ergänzen (E-04: Pflicht für platform-admin/tenant-admin).

## Lokaler API-Test (Dev)

Da der Direct Access Grant deaktiviert ist, wird ein Token über den Browser-Login des
Frontends geholt (Login als `dev`/`dev`) und aus den DevTools kopiert. Für schnelles
Endpoint-Testing kann der Grant in der Keycloak-Admin-Konsole (http://localhost:8081,
`admin`/`admin`) am Client `opencrm-web` temporär aktiviert werden — niemals in Produktion.
