# UX Convenience Canon — 암묵적 편의 (기능 나열 금지)

> **목적**: 편의 후보는 **무한**하다. 전부 나열·MCQ **하지 않는다**.  
> **4층 필터**로 걸러낸 **U1~U7**만 1차에 넣는다.

## 4층 필터 (후보 걸러내기)

| 층 | 질문 | No면 |
|----|------|------|
| **L1 여정** | J1~J5 중 이번 제품 여정에 해당? | 그 단계 편의 **스킵** |
| **L2 U축** | topic·goal·topology 키워드가 U1~U7 트리거? | 해당 축 **스킵** |
| **L3 종속** | U1(장시간) 등 선행 축 → U2·U4 **자동 포함** | — |
| **L4 배제** | constraints에 명시 거부? CLI-only? scope 밖? | **넣지 않음** |

**플랫폼 상식 (L2 통과 후)**: 에이전트가 OS·프레임워크 **기본 패턴**으로 구체화 (기능 checklist 아님).  
예: **U1+Android** → ForegroundService + **PARTIAL_WAKE_LOCK** + **WifiLock** + (다운로드 시작 시) **REQUEST_IGNORE_BATTERY_OPTIMIZATIONS** · **U2** → **§ U2-Resume**(앱 이어받기 + USB `adb install -r`) · **U3** → **§ U3-Autocomplete**(입력·드롭다운·스크롤 한 세트·터치 후 수정).

Oracle: `automation/ux_convenience_scan.py` 가 L1~L4 **자동** → `write-spec` §7 주입 · **유저 MCQ 없음**.

## 적용 시점 (MUST)

| 단계 | 동작 |
|------|------|
| **Oracle·write-spec** | `scan_implicit_ux(state)` → 기획서 **§7 암묵적 편의** 자동 |
| **Feature write-brief** | `implicitUx` JSON 블록 자동 |
| **Manifest·1차 구현** | U1~U7 재스캔 → 해당 축 **1차 포함** |

## 7축 (U1~U7)

| # | 축 | 해당하면 MUST (개념) |
|---|-----|----------------------|
| **U1** | 장시간·백그라운드 | **FGS**·알림·**WakeLock·WifiLock**(Doze)·다운로드 시 **배터리 최적화 제외 요청**·진행률·완료 후 다음 화면 |
| **U2** | 끊김·재개 | 부분 저장·이어받기·실패 사유 — **§ U2-Resume** |
| **U3** | 입력·확정 | 자동완성·**터치 후 수정**·키보드 — **§ U3-Autocomplete** |
| **U4** | 네트워크·용량 | WiFi 경고·받음/전체·예상 시간 |
| **U5** | 오프라인·GPS | 오프라인·위치·막힘 메시지 |
| **U6** | 화면·몰입 | 방향·내비 숨김·완료 후 이동 |
| **U7** | 설정·되돌리기 | 1회/매회 분리·옵션 초기화 |

## U2-Resume — 이어받기 (앱 + 제작·USB 업데이트)

> U1(장시간·다운로드)이면 **L3로 U2 자동 포함**. 끊김·재개는 **런타임**과 **개발 중 USB 설치** 모두 해당.

### 앱 (런타임 MUST)

| MUST | 내용 |
|------|------|
| **부분 저장** | 진행률·완료 도시·타일·작업 상태를 **내부 저장소**에 지속 (`filesDir` 등). 앱 종료·크래시·백그라운드 킬 후에도 복원. |
| **이어받기** | 재실행 시 **미완료 구간만** 이어서 처리(완료 도시·파일 스킵). UI에 **「이어받기」** 또는 자동 재개. |
| **업데이트 후** | **같은 앱 서명·덮어쓰기 설치**면 저장 데이터 유지 → 다운로드 **이어받기 가능**이 기본. |
| **실패 사유** | 중단·실패 시 **왜** 멈췄는지 한 줄 안내. |

### 제작·USB 업데이트 (에이전트 MUST)

| MUST | 내용 |
|------|------|
| **기본 설치** | USB 연결 중 APK 반영 → **`adb install -r`** (replace·**데이터 유지**). 다운로드·설정 **이어받기 전제**. |
| **새 설치만 예외** | 유저가 **「새로 설치」「초기화」** 등 **명시**할 때만 `adb uninstall` 후 clean install. |
| **금지** | 개발 중 매번 uninstall·데이터 삭제를 **기본**으로 두는 것. 유저에게 「데이터 날아갑니다」 확인을 **매 업데이트** 요구. |

```bash
# 기본 (이어받기 유지)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 유저 명시 초기화 시만
adb uninstall <applicationId> && adb install app/build/outputs/apk/debug/app-debug.apk
```

**플랫폼 예 (Android)**: `DownloadJobState` · `TripStore.saveDownloadJob` · `RegionDownloadManager` 스킵 로직 · `TravelSetupScreen` 이어받기.

## U3-Autocomplete — 입력·드롭다운·스크롤 **한 세트**

> 나라·도시·주소 등 **검색형 입력**에 U3가 해당하면 아래를 **분리 구현하지 않는다** — 하나의 필드 UX로 묶는다.

| MUST | 내용 |
|------|------|
| **터치 후 수정** | 확정·readOnly 상태여도 **입력창 탭 → 즉시 편집**·키보드. 「터치하여 수정」 같은 별도 안내 **금지**(필드 자체가 수정 가능해야 함). |
| **한 세트** | **입력창 + 자동완성 드롭다운 + 목록 스크롤**을 한 컴포넌트·한 흐름으로. 드롭다운만 따로·스크롤 없는 2줄 잘림 **금지**. |
| **드롭다운** | 테두리·**항목 구분선(칸막이)** · 고정 가시 높이(**≥5~6행**) · 후보는 **전부** 표시(임의 2~5개 cap 금지). |
| **스크롤** | 후보 > 가시 행 → **드롭다운 내부** 스크롤(LazyColumn 등) + 「N개 — 아래로 스크롤」 등 **스크롤 가능** 안내. |
| **확정** | 목록 선택 또는 **확인** 버튼으로 좌표·값 확정. 확정 후에도 위 **터치 후 수정** 유지. |

**플랫폼 예 (Android Compose)**: `CountryAutocompleteField` · `CityConfirmField` · `GeoSuggestionDropdownList` — `SetupFormFields.kt`.

## 금지

- 편의 기능 **전수조사** 후 유저에게 확인
- U축 **무관**한 「있으면 좋음」 과잉 (L4 위반)
- Oracle MCQ에 「백그라운드도요?」

## 제품 repo

```bash
cp templates/cursor-rules/ux-convenience-defaults.mdc .cursor/rules/
```

## 관련

- `AGENT.md` · MCP-Auto `UX_CONVENIENCE_CATALOG` · `genesis-session` skill
