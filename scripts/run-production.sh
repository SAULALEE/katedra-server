#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
exec doppler run --project katedra-server --config production -- ./mvnw spring-boot:run
