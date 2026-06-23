# Changelog

## 1.2.35 (2026-06-23)

- 모국: 기본 설정 저장·지도 진입 시 도보경로 그래프 선다운 (`HomeRoutingBootstrap`, `home_live_cache`)
- 모국 실시간 지도: WiFi에서도 경로 안 나오던 버그 수정 · POI 탭 시 그래프 재생성
- 경로: 직선(연한 빨강) + 도보(파랑) 동시 표시 · 우하단 `직선`/`경로` 거리 분리
- 지도 UI: 지역명 축소·중앙 정렬 · 반경 배지 글자 축소 · 스케일 눈금 위·라벨 아래

## 1.2.34 (2026-06-23)

- 스케일·반경·헤더 UI 미세 조정 · 경로 표기 UX

## 1.2.33 (2026-06-23)

- 반경 OFF → POI 숨김 복구 · 도보경로 on-demand (`OnDemandRouting`)

## 1.2.32 (2026-06-23)

- POI: fetch 한국어 enrichment · 여행 지역 `poi.json` 저장 · 미리보기 재시도

## 1.2.31 (2026-06-23)

- 여행 설정: 입국 공항·여행 기간(연도) · 구간 MM-DD · 레이어 UI · 베네치아 기본값

## 1.2.30 (2026-06-23)

- 「파일에서 가져오기」: zip MIME 확장 · URI 권한 유지 · 안내 문구 (내부 저장소만 인식)
- 허브·여행 설정 화면에 PC zip 가져오기 안내

## 1.2.29 (2026-06-23)

- POI: 도시 전환 시 `livePois` 잔존 제거 — 이전 지역 POI가 새 지도에 섞이던 문제
- 허브 「현재 지역 보기」: 여행국 GPS는 WiFi 없이도 가까운 다운로드 도시로 진입 (모국 실시간만 WiFi 필요)

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
