#!/usr/bin/env bash
# 실시간 다운로드 % (기본 2초마다 갱신)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec python3 "${ROOT}/offline-bundles/progress.py" --live "$@"
