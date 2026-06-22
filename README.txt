이탈리아 돌로미티·베네치아 오프라인 가이드 (italy-offline-guide)
====================================================================

무엇을 하나요?
  여행 중 GPS만으로 내 위치를 잡고, 앱에 미리 넣어 둔
  관광지·문화제 중 가까운 곳을 한국어로 안내합니다.
  인터넷·구글맵 없이 동작합니다.

  · 관광지: 항상 거리순으로 표시
  · 문화제: 「여행 시작~종료 날짜」와 「보고 싶은 요일」을 고른 뒤,
    그 기간·요일에 해당하는 축제만 표시

화면
  · 위: **오프라인 미니 지도**(핀만) + GPS 위치
  · 아래: 카드 목록 + 「읽어주기」(Android TTS ko-KR)

설치·실행 (Kotlin 네이티브 APK)
  # MCP-Auto 레포에서
  bash EXE/run_italy_guide_apk.sh
  # APK: ~/MCP-italy-offline-guide/android/app/build/outputs/apk/debug/app-debug.apk
  # 기기에 바로 설치:
  INSTALL=1 bash EXE/run_italy_guide_apk.sh

POI 데이터 넣기 (빌드 시 1회, 네트워크 필요)
  bash EXE/run_italy_guide_ingest.sh --dry-run   # 샘플 (게이트 없음)
  bash EXE/run_italy_guide_ingest.sh             # OSM 실수집
  ※ 출력: android/app/src/main/assets/poi.json

문서
  01_Planning_Specification.md — 기획서
  03_Architecture_Rule.md      — 설계
  PROJECT.json                 — Stele

참고 Stele: swiss-offline-guide (패턴 fork)
