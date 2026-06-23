#!/usr/bin/env bash
# 오프라인 번들 zip — PC에서 받아 폰 「파일에서 가져오기」
# 나라 통째: bash EXE/export_walking_region.sh --country-pack croatia
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
exec python3 ingest/export_region_bundle.py "$@"
