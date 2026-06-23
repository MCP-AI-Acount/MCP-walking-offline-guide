#!/usr/bin/env bash
# 오프라인 나라 번들 — 백그라운드 다운로드 (끊겨도 캐시 이어받기)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/offline-bundles"
CACHE="${OUT}/.cache"
LOG="${OUT}/download.log"
PIDFILE="${CACHE}/download.pid"
mkdir -p "${CACHE}"

if pgrep -f "offline-bundles/download_all.sh" >/dev/null 2>&1; then
  pid="$(pgrep -f "offline-bundles/download_all.sh" | head -1)"
  echo "이미 실행 중 (pid ${pid})"
  echo "진행: bash ${ROOT}/EXE/show_offline_download_progress.sh"
  exit 0
fi

if [[ -f "${PIDFILE}" ]]; then
  oldpid="$(cat "${PIDFILE}" 2>/dev/null || true)"
  if [[ -n "${oldpid}" ]] && kill -0 "${oldpid}" 2>/dev/null; then
    echo "이미 실행 중 (pid ${oldpid})"
    exit 0
  fi
fi

echo "=== BACKGROUND START $(date -Iseconds) ===" >> "${LOG}"
nohup bash "${OUT}/download_all.sh" </dev/null >>"${LOG}" 2>&1 &
pid=$!
disown "${pid}" 2>/dev/null || true
sleep 1
if ! kill -0 "${pid}" 2>/dev/null; then
  echo "시작 실패 — tail ${LOG}"
  exit 1
fi

echo "백그라운드 시작 pid=${pid}"
echo "로그: tail -f ${LOG}"
echo "진행: bash ${ROOT}/EXE/show_offline_download_progress.sh"
