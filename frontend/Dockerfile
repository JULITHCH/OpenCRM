# OpenCRM Frontend — Multi-Stage-Build (docs/11-deployment-und-betrieb.md, Abschnitt 4)
# Build:   node:22-alpine (Vite-Build)
# Runtime: nginx:alpine, non-root auf Port 8080, eigene nginx.conf

# --- Build-Stage ---------------------------------------------------------
FROM node:22-alpine AS build
WORKDIR /app

# Hinweis: VITE_*-Variablen werden zur BUILD-Zeit in das Bundle eingebacken.
# Fuer abweichende Umgebungen als --build-arg setzen (Platzhalter-Domain E-23).
# Vom Frontend-Code tatsaechlich gelesene Variablen (src/api/client.ts, src/auth/keycloak.ts):
#   VITE_API_URL      leer = relative /api-Pfade (gleiche Origin ueber den Reverse-Proxy)
#   VITE_KEYCLOAK_URL Basis-URL des Keycloak-Servers (ohne /realms/...)
ARG VITE_API_URL=
ARG VITE_KEYCLOAK_URL=https://auth.opencrm.example
# Commit-Stand fuer den Footer (im Image gibt es kein .git): --build-arg VITE_GIT_SHA=$(git rev-parse --short=10 HEAD)
ARG VITE_GIT_SHA=unknown
ENV VITE_API_URL=${VITE_API_URL} \
    VITE_KEYCLOAK_URL=${VITE_KEYCLOAK_URL} \
    VITE_GIT_SHA=${VITE_GIT_SHA}

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- Runtime-Stage -------------------------------------------------------
FROM nginx:alpine AS runtime

# Eigene Konfiguration: non-root, Port 8080, SPA-Fallback, Security-Header
COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist /usr/share/nginx/html

# Non-root: User "nginx" existiert im Basisimage (UID 101);
# nginx.conf verlegt PID-/Temp-Pfade nach /tmp, listen 8080 (unprivilegiert)
USER nginx
EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
