# Changelog

## 1.2.28 (2026-06-23)

- POI: `name:ko` 파싱 · 모국/프리뷰 소스 분리 · Overpass 재시도·홈 캐시 즉시 저장
- 허브: 도시/지도 탭 → 해당 지역 프리뷰 (GPS·토글 OFF)
- 스케일: 눈금 위치 고정 · 100m 라벨만 눈금 바로 위
- `TripNavigation.isHomeMapRegion` · `hubPreviewMode` · SafeStorage/CrashRecovery 등 누적

## 1.1.7 (2026-06-23)

- 크래시 복구: `CrashRecovery` · 로딩 **메인·옵션·복구** · 옵션 오류 복구 카드
- 메뉴 UI 단순화 (`MenuUi.kt`)
- Project Maker 문서: `BUG_LIST.md` WOG-001~003

## 1.1.6 (2026-06-23)

- `SafeStorage` — 원자적 쓰기·JSON/quarantine 전역
- `TileStore` — zip 실패 fallback · `.tmp` 감지

## 1.1.5 (2026-06-23)

- 깨진 `tiles.zip` 격리 + `tiles/` 폴더 fallback
- zip tmp→rename (다운로드 중 corruption 방지)

## 1.1.0 (2026-06-22)

GPS 헤딩 지도 1차 완성 — 도보 오프라인 가이드 핵심 UX.

### 지도 · GPS
- GPS 고정 + 헤딩업: 핀 중앙 고정, 지도만 회전 (yaw)
- 줌 범위 **200m ~ 2km** (헤딩 모드)
- 회전 클립 방지: GPS 중심 **정사각형** 오버스캔 캔버스
- POI·별점: 회전 레이어 밖 오버레이 (클립·어긋남 수정)
- 원거리 타일 차단 (한국 bbox 등) — 행정지역 경계 클립 아님
- GPS 핀: 초록 chevron(∧) 방향 표시

### UI
- 상단·우측 패널 `statusBarsPadding` — 여백 최소화
- 필터 드롭다운: **추천도** 라벨

### 빌드
- `versionCode` 110 · APK debug
