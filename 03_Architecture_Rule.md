# 03 Architecture Rule — italy-offline-guide

## 시스템 경계

```
[빌드 타임] Overpass + curated festivals → android/app/src/main/assets/poi.json
[런타임]    GPS → haversine → filter → UI (Compose map + list + TTS)
            (네트워크 없음)
```

## 컴포넌트

| 모듈 | 역할 |
|------|------|
| `ingest/fetch_poi.py` | OSM 관광 POI + curated 문화제 병합 |
| `ingest/festival.py` | 기간·요일 필터 (테스트 공유) |
| `ingest/geo.py` | haversine · nearest |
| `android/.../ItalyGuideScreen.kt` | 위치·상태·목록 UI |
| `android/.../OfflineMiniMap.kt` | bbox 상대 좌표 Canvas 핀 |
| `android/.../PoiLogic.kt` | 문화제 필터 · 거리 (ingest/geo 동형) |
| `android/.../FestivalStorage.kt` | SharedPreferences 일정 override |

## 문화제 스케줄

```json
"schedule": {
  "annual_start": "02-01",
  "annual_end": "02-15",
  "weekdays": [5, 6]
}
```

- `weekdays`: 0=월 … 6=일. 비어 있으면 기간 내 모든 요일.
- 여행 `[start, end]`와 교집합 날짜 중, 선택 요일 ∧ 연간 구간에 들어가면 표시.

## TTS

`android.speech.tts.TextToSpeech` · `Locale.KOREAN` · 앱 번들에 음성 파일 없음(기기 엔진).

## 빌드

```bash
bash EXE/run_italy_guide_apk.sh          # debug APK
VARIANT=release bash EXE/run_italy_guide_apk.sh
INSTALL=1 bash EXE/run_italy_guide_apk.sh  # adb install
```
