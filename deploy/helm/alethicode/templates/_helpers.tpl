{{- define "alethicode.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "alethicode.fullname" -}}
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

{{- define "alethicode.labels" -}}
helm.sh/chart: {{ include "alethicode.name" . }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: alethicode
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}

{{- define "alethicode.selectorLabels" -}}
app.kubernetes.io/name: {{ include "alethicode.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}
