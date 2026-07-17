# OpenCRM auf einem einzelnen Host (LXC/VM) installieren

Dieser Ordner enthält ein schlüsselfertiges Single-Host-Deployment: der gesamte
Stack (PostgreSQL, Keycloak, Backend, Frontend) läuft als Docker-Compose-Projekt in
**einem** LXC-Container hinter einem vorgelagerten Nginx-Reverse-Proxy, der TLS terminiert.

```
Browser ──HTTPS──> Nginx-Proxy ──┬── crm.<domain>  ──HTTP──> LXC:8080  (Frontend: SPA + /api-Proxy ──> Backend)
                                 └── auth.<domain> ──HTTP──> LXC:8081  (Keycloak)
                          LXC-intern: Backend, PostgreSQL (nicht nach außen exponiert)
```

> Für Mehr-Replica-/Cluster-Betrieb gibt es stattdessen das Helm-Chart unter
> `infra/helm/opencrm`. Die Dev-Umgebung (`infra/docker-compose.yml`) startet nur die
> Abhängigkeiten, nicht die App — dieses Verzeichnis hier startet **alles**.

## 0. Voraussetzungen im LXC

- Container mit **Docker**-Fähigkeit. Auf Proxmox: LXC mit `features: nesting=1` (und ggf.
  `keyctl=1`); ein **unprivilegierter** Container mit Nesting reicht für Docker in der Regel.
- Richtwert Ressourcen: **2 vCPU, 4 GB RAM, 20 GB Disk** (Keycloak + JVM brauchen Speicher).
- Zwei DNS-Namen, die auf deinen Nginx-Proxy zeigen: `crm.<domain>` und `auth.<domain>`.

Docker + Compose-Plugin installieren (Debian/Ubuntu im LXC):

```bash
apt-get update && apt-get install -y ca-certificates curl git
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/debian $(. /etc/os-release; echo $VERSION_CODENAME) stable" \
  > /etc/apt/sources.list.d/docker.list
apt-get update && apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

Wer nicht als `root` arbeitet, braucht Zugriff auf den Docker-Socket (sonst:
`permission denied while trying to connect to the docker API`):

```bash
sudo usermod -aG docker $USER   # danach neu einloggen (oder: newgrp docker)
```

## 1. Repository holen

```bash
git clone https://github.com/JULITHCH/OpenCRM.git
cd OpenCRM/infra/deploy
```

## 2. Konfiguration

```bash
cp .env.example .env
nano .env   # URLs auf crm.<domain>/auth.<domain> setzen, ALLE Passwörter ändern
```

Wichtig in `.env`:
- `CRM_PUBLIC_URL` / `AUTH_PUBLIC_URL` — die beiden öffentlichen HTTPS-URLs (ohne Slash am Ende).
- alle `*_PASSWORD` und `OPENCRM_API_CLIENT_SECRET` — starke Werte.

## 3. Stack bauen und starten

```bash
docker compose up -d --build
```

Beim ersten Start werden die DB-Rollen angelegt (`postgres-init/01-init.sh`), Flyway spielt die
Migrationen ein und Keycloak importiert den Realm `opencrm`. Fortschritt:

```bash
docker compose ps
docker compose logs -f backend
```

## 4. Vorgelagerten Nginx-Proxy konfigurieren

`nginx-proxy.example.conf` als Vorlage nutzen: `crm.<domain>` → `LXC-IP:8080`,
`auth.<domain>` → `LXC-IP:8081`, `LXC_IP` und Zertifikatspfade anpassen, dann Nginx neu laden.
Zertifikate z. B. per certbot (`certbot --nginx -d crm.<domain> -d auth.<domain>`).

## 5. Ersten Mandanten anlegen

Der Realm bringt keinen produktiven Nutzer mit. Zwei Schritte:

**a) Mandant in OpenCRM anlegen** (durch einen `platform-admin`). Für den allerersten Start
kann der Mandant direkt in der DB gesetzt werden:

```bash
docker compose exec postgres psql -U postgres -d opencrm -c \
  "INSERT INTO tenants (id, name, slug) VALUES (gen_random_uuid(), 'Meine Firma', 'meine-firma') RETURNING id;"
```

Notiere die zurückgegebene `id` (Tenant-UUID).

**b) Keycloak-Nutzer anlegen** in der Admin-Konsole (`https://auth.<domain>`, Login mit
`KEYCLOAK_ADMIN_*` aus der `.env`), Realm `opencrm`:
1. Nutzer anlegen, Passwort setzen.
2. Unter **Attributes** das Attribut `tenant_id` = die UUID aus Schritt (a) setzen.
3. Unter **Role mapping** eine Realm-Rolle zuweisen (z. B. `tenant-admin`).

Danach `https://crm.<domain>` öffnen und mit diesem Nutzer anmelden.

## Betrieb

- **Update**: `git pull && docker compose up -d --build` (Flyway migriert automatisch).
- **Backup**: das Volume `opencrm_postgres-data` (DB) und `opencrm_storage-data`
  (Import-/Exportdateien) sichern; zusätzlich die Keycloak-DB steckt in `postgres-data`.
- **Logs**: strukturierte JSON-Logs (`SPRING_PROFILES_ACTIVE=json`); `docker compose logs`.

## Häufige Stolpersteine

- **Login schlägt fehl / Redirect-Fehler**: Die Redirect-URIs des Clients `opencrm-web` im
  Realm müssen `https://crm.<domain>/*` enthalten. Werden die `OPENCRM_WEB_*`-Platzhalter beim
  Import nicht ersetzt, in der Keycloak-Admin-Konsole (Client `opencrm-web`) manuell setzen.
- **Backend meldet `invalid_token` / erreicht Keycloak nicht (Hairpin)**: Der `issuer` im Token
  ist `https://auth.<domain>/realms/opencrm` — das Backend muss **dieselbe** öffentliche URL
  erreichen können. Zeigt dieser Name per DNS auf den vorgelagerten Proxy und kommt der Container
  dort nicht raus (kein NAT-Hairpin), im `backend`-Service in `docker-compose.yml` `extra_hosts`
  einkommentieren:
  - Proxy auf **anderem** Host im LAN: `- "auth.<domain>:<LAN-IP-des-Proxys>"`
  - Proxy auf **demselben** Host wie Docker: `- "auth.<domain>:host-gateway"`

  Danach `docker compose up -d backend`. Test von innen:
  `docker compose exec backend sh -c "wget -qO- https://auth.<domain>/realms/opencrm/.well-known/openid-configuration | head -c 80"`.
  Kommt JSON zurück, ist der Hairpin gesetzt. Voraussetzung: der Proxy liefert für `auth.<domain>`
  ein **gültiges** Zertifikat (kein selbstsigniertes — die JVM prüft es).
- **`nesting`**: Startet Docker im LXC nicht, fehlt meist `features: nesting=1` am Container.
