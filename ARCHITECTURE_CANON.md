# Architecture Canon — walking-offline-guide

> **Q축 정본** — `delivery_intent_scan.py scan --project .`  
> 유저 말 그대로만 구현 **금지** · 아래 MUST vs 코드 대조 후 delivery.

## Q0: literal ≠ delivery · 구현 3단 (D-23 / IRE)

- **MUST:** 기능 체크리스트만으로 완료 선언하지 않는다. 의도·아키텍처 축별 코드 증거 1줄.
- **MUST (구현 순서 — MCP-Auto Core D-23):**
  1. **1차·설계** — 유저 문장 그대로보다 **도메인 표준·기존 패턴** 우선 (예: 헤딩업=GPS 축·핀 화면 고정).
  2. **2차·검토** — 웹/SDK · 본 캐논 · 코드 grep/Read로 교차 검증.
  3. **3차·집행** — 검토와 유저 지시 **충돌·모호** → 질문 후 구현 · **일치** → 구현.
- **≠:** 유저 지시 수신 즉시 코드 Write.

## Q1: 모국 — GPS 중심 점진 LOD

- **MUST:** 모국(`homeCountry`)에서는 **현재 GPS 주변** 타일·POI를 **점진적**(낮은 줌→고줌·이동 시 ring 확장)으로 받는다. 여행국 **일괄 5km** 다운로드와 **다른 코드 경로**.
- **≠:** `RegionDownloadManager.downloadLeg` 단일 경로만 존재.
- **상태:** *(1차 구현 — `HomeProgressiveDownloader` GPS 근→원 타일·POI; 완전 ring LOD는 P1)*

## Q1-B: 모국 일반 지도 (HOME_LIVE)

- **MUST:** **모국 GPS + WiFi/인터넷** 둘 다일 때만 일반 지도앱(온라인 타일·GPS follow). 하나라도 빠지면 **TRAVEL** 오프라인 또는 **NEED_TRAVEL_SETUP**.
- **증거:** `MapPolicy.decide` · `TripNavigation.isHomeLiveMode(..., hasInternet)` · `WalkingApp.routeByPolicy`
- **≠:** 비행기 모드 강제 — 네트워크 락(온라인 타일 fetch는 HOME_LIVE만).

## Q2: 여행국 — 미리 선다운로드

- **MUST:** 일정 도시별 bbox · 줌 10–14 · POI·경로·번역 **일괄** FGS 다운로드.
- **증거:** `RegionDownloadManager` · `STOP_DOWNLOAD_RADIUS_KM=5`

## Q3: 현장 vs 미리보기

- **MUST:** GPS가 다운로드 지역 밖 → region 중심·미리보기 배너·GPS핀·도보경로 OFF. 현장 → GPS·follow·경로 ON.
- **증거:** `TripNavigation.isOnSite` · `MapGuideScreen` preview/on-site

## Q4: 모국어·TTS·설명

- **MUST:** 지도 패널 UI·POI 설명·TTS = 1단계 모국어.
- **증거:** `HomeLanguage` · `PoiLocalization`

## Q5: U축 편의 (교차)

- **MUST:** U2 이어받기 · U3 autocomplete · U1 FGS 다운로드 — `UX_CONVENIENCE_CANON.md` 해당 절.

## Q6: 헤딩업 — GPS 고정 · 주변 지도 회전 (landscape)

- **MUST:** sensorLandscape · bearing = `landscapeScreenTopBearingDeg` (±90° 보정 **금지**).
- **MUST:** 지도 회전 = `mapRotationDeg`(−heading) · pivot = Canvas px 중앙 = GPS anchor.
- **MUST:** 타일·경로·POI = **단일** `withTransform` 통째 회전 · 타일 개별 orbit **금지**.
- **MUST:** 줌 = `visibleSpanM`+실제 map `width` px · 회전 시 `visibleTileRangeHeadingUp`.
- **MUST:** 나침반만 · `NavigationAnchor` · bearing ≠ MapCamera.

---

### 슬라이더 (하네스)

MCP-Auto `0_Config/delivery_audit_state.json` — AI 자율(0) ↔ 유저 체크리스트(100).

```bash
python ~/MCP-Auto/automation/delivery_intent_scan.py set-slider 40
python ~/MCP-Auto/automation/delivery_intent_scan.py scan --project . --record
```
