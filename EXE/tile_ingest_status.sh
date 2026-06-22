#!/usr/bin/env bash
# Italy Guide — 타일 ingest 진행 상태 (한 줄 요약)
set -euo pipefail
LOG="${1:-/tmp/tiles_unlimited.log}"
ZIP="${2:-$(cd "$(dirname "$0")/.." && pwd)/android/app/src/main/assets/tiles.zip}"

echo "=== tiles.zip (APK 번들) ==="
ls -lh "$ZIP" 2>/dev/null || echo "(없음)"

echo ""
echo "=== ingest 프로세스 ==="
pgrep -fl 'fetch_tiles.py' || echo "(실행 중 아님)"

echo ""
echo "=== 최근 로그 (마지막 5줄) ==="
if [[ -f "$LOG" ]]; then
  tail -5 "$LOG"
  echo ""
  LAST=$(tail -1 "$LOG")
  if echo "$LAST" | grep -q progress; then
    PROG=$(echo "$LAST" | python3 -c "import sys,json; d=json.load(sys.stdin); print(f\"region={d.get('region')} tiles={d.get('progress')} bytes={d.get('bytes',0)/1e6:.1f}MB\")" 2>/dev/null || echo "$LAST")
    echo "현재: $PROG"
  fi
else
  echo "로그 없음 — ingest 시작:"
  echo "  cd ~/MCP-italy-offline-guide && python3 -u ingest/fetch_tiles.py 2>&1 | tee /tmp/tiles_unlimited.log"
fi
