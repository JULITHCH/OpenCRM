# Architecture Decision Records (ADR) – Index

| Status | Stand | Verantwortlich |
|---|---|---|
| Entwurf | 2026-07-16 | Lead Architecture |

## Zweck

Dieser Index listet alle Architecture Decision Records des Projekts OpenCRM. ADRs dokumentieren getroffene Architekturentscheidungen inklusive Kontext, Konsequenzen und verworfener Alternativen. Sie sind die verbindliche Referenz, warum die Architektur so ist, wie sie in den Kapiteln [02-systemarchitektur.md](../02-systemarchitektur.md) bis [12-roadmap.md](../12-roadmap.md) beschrieben wird.

## Uebersicht

| Nummer | Titel | Status |
|---|---|---|
| [ADR-001](ADR-001-postgresql-als-datenbank.md) | PostgreSQL als Datenbank | Akzeptiert |
| [ADR-002](ADR-002-multi-tenancy-shared-schema-rls.md) | Multi-Tenancy: Shared Schema mit Row-Level Security | Akzeptiert |
| [ADR-003](ADR-003-keycloak-single-realm-organizations.md) | Keycloak: Ein Realm mit Organizations | Akzeptiert |
| [ADR-004](ADR-004-modularer-monolith-spring-boot.md) | Modularer Monolith mit Spring Boot und Java 21 | Akzeptiert |
| [ADR-005](ADR-005-rest-api-mit-openapi.md) | REST-API mit OpenAPI 3.1 | Akzeptiert |
| [ADR-006](ADR-006-import-export-mit-spring-batch.md) | Import/Export mit Spring Batch und PostgreSQL-Job-Tabellen | Akzeptiert |
| [ADR-007](ADR-007-frontend-react-spa.md) | Frontend als React-SPA | Akzeptiert |

## Konventionen

- Nummerierung fortlaufend (ADR-001, ADR-002, ...); ein einmal vergebener ADR wird nicht geloescht.
- Statuswerte: Vorgeschlagen, Akzeptiert, Abgeloest (mit Verweis auf den Nachfolger-ADR).
- Eine akzeptierte Entscheidung wird nicht editiert, sondern durch einen neuen ADR abgeloest, wenn sie revidiert wird.
- Format je ADR: Titel, Metadaten-Tabelle, Kontext, Entscheidung, Konsequenzen (positiv/negativ), Betrachtete Alternativen, Offene Punkte.

## Offene Punkte

1. Prozess fuer neue ADRs (Review-Pflicht, Freigabe durch wen) ist noch nicht formal festgelegt.
2. Ob ADRs nach Projektstart im Repo des Backends oder weiterhin in diesem Doku-Verzeichnis gepflegt werden, ist offen.
