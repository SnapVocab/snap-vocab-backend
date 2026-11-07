#!/bin/sh
set -eu

# Validate deployment inputs without printing credentials or making an AI request.
unquote() {
  case "$1" in
    \"*\") value=${1#\"}; printf '%s' "${value%\"}" ;;
    \'*\') value=${1#\'}; printf '%s' "${value%\'}" ;;
    *) printf '%s' "$1" ;;
  esac
}

# Docker --env-file preserves quotes, whereas Compose env_file resolves them.
AI_SERVICE_URL=$(unquote "${AI_SERVICE_URL:-}")
RUNPOD_API_KEY=$(unquote "${RUNPOD_API_KEY:-}")

case "${AI_SERVICE_URL:-}" in
  http://*|https://*) ;;
  *) echo 'AI_SERVICE_URL must be a remote HTTP(S) endpoint.' >&2; exit 1 ;;
esac

case "$AI_SERVICE_URL" in
  http://localhost*|https://localhost*|http://127.*|https://127.*|http://0.0.0.0*|https://0.0.0.0*|"http://[::1]"*|"https://[::1]"*)
    echo 'Production AI_SERVICE_URL must not point to localhost/loopback.' >&2
    exit 1
    ;;
esac

case "$AI_SERVICE_URL" in
  https://*.api.runpod.ai|https://*.api.runpod.ai/*|https://*.api.runpod.net|https://*.api.runpod.net/*)
    if [ -z "${RUNPOD_API_KEY:-}" ]; then
      echo 'RUNPOD_API_KEY is required for the Runpod Serverless endpoint.' >&2
      exit 1
    fi
    ;;
esac

echo 'Production AI endpoint configuration passed preflight.'
