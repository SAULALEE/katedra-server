#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
action="${1:-up}"
doppler_config="${DOPPLER_CONFIG:-dev}"

if docker compose version >/dev/null 2>&1; then
  compose=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  compose=(docker-compose)
else
  printf 'Docker Compose is required (docker compose or docker-compose).\n' >&2
  exit 1
fi

case "$action" in
  up)
    exec doppler run --project katedra-server --config "$doppler_config" -- "${compose[@]}" up -d --build
    ;;
  down)
    exec doppler run --project katedra-server --config "$doppler_config" -- "${compose[@]}" down
    ;;
  logs)
    exec doppler run --project katedra-server --config "$doppler_config" -- "${compose[@]}" logs -f server
    ;;
  *)
    printf 'Usage: %s {up|down|logs}\n' "$0" >&2
    exit 2
    ;;
esac
