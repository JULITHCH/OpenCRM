{{/*
Chart-Name (ueberschreibbar via nameOverride).
*/}}
{{- define "opencrm.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Voll qualifizierter Name (Release + Chart, ueberschreibbar via fullnameOverride).
*/}}
{{- define "opencrm.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Chart-Label (Name-Version).
*/}}
{{- define "opencrm.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Gemeinsame Standard-Labels fuer alle Ressourcen.
*/}}
{{- define "opencrm.labels" -}}
helm.sh/chart: {{ include "opencrm.chart" . }}
app.kubernetes.io/name: {{ include "opencrm.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: opencrm
{{- end }}

{{/*
Selector-Labels je Komponente. Aufruf:
  {{ include "opencrm.selectorLabels" (dict "root" $ "component" "backend") }}
*/}}
{{- define "opencrm.selectorLabels" -}}
app.kubernetes.io/name: {{ include "opencrm.name" .root }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .component }}
{{- end }}

{{/*
Image-Referenz: Digest (CI-Deploys, docs/11 Abschnitt 3.2) hat Vorrang vor Tag.
Aufruf mit dem image-Block aus den Values, z. B.:
  {{ include "opencrm.image" .Values.backend.image }}
*/}}
{{- define "opencrm.image" -}}
{{- if .digest }}
{{- printf "%s@%s" .repository .digest }}
{{- else }}
{{- printf "%s:%s" .repository .tag }}
{{- end }}
{{- end }}
