#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
exec doppler run --project katedra-server --config local -- ./mvnw test
