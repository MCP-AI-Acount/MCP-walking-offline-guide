#!/usr/bin/env bash
# 사용자 터미널에서 장시간 다운로드 시작 (에이전트 세션과 무관하게 유지)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/offline-bundles"
LOG="${OUT}/download.log"
MODE="${1:-tiles}"  # tiles | full

cd "${ROOT}"
mkdir -p "${OUT}"

if pgrep -f 'export_region_bundle.py' >/dev/null 2>&1; then
  echo "이미 export_region_bundle.py 실행 중입니다."
  pgrep -fl export_region_bundle
  exit 1
fi

case "${MODE}" in
  tiles)
    SCRIPT="${OUT}/download_tiles_only.sh"
    echo "타일만 다운로드 (POI 제외) — 로그: ${LOG}"
    ;;
  full)
    SCRIPT="${OUT}/download_all.sh"
    echo "POI+타일 전체 다운로드 — 로그: ${LOG}"
    ;;
  *)
    echo "사용: $0 [tiles|full]" >&2
    exit 1
    ;;
esac

nohup bash "${SCRIPT}" >>"${LOG}" 2>&1 &
echo "PID=$!"
echo "실시간 진행: bash ${ROOT}/EXE/show_offline_download_progress.sh"
