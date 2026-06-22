"""오프라인 관광지 거리·geofence — italy-offline-guide."""

from __future__ import annotations

import math
from datetime import date, timedelta
from typing import Any

EARTH_RADIUS_M = 6_371_000


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    rlat1, rlon1, rlat2, rlon2 = map(math.radians, (lat1, lon1, lat2, lon2))
    dlat = rlat2 - rlat1
    dlon = rlon2 - rlon1
    a = math.sin(dlat / 2) ** 2 + math.cos(rlat1) * math.cos(rlat2) * math.sin(dlon / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(a))


def in_bbox(lat: float, lon: float, bbox: dict[str, float]) -> bool:
    return bbox["south"] <= lat <= bbox["north"] and bbox["west"] <= lon <= bbox["east"]


def nearest_pois(
    pois: list[dict[str, Any]],
    lat: float,
    lon: float,
    *,
    n: int = 2,
    kind: str | None = None,
    bbox: dict[str, float] | None = None,
) -> list[dict[str, Any]]:
    ranked: list[tuple[float, dict[str, Any]]] = []
    for p in pois:
        if kind and p.get("kind", "attraction") != kind:
            continue
        plat = float(p["lat"])
        plon = float(p["lon"])
        if bbox and not in_bbox(plat, plon, bbox):
            continue
        d = haversine_m(lat, lon, plat, plon)
        ranked.append((d, {**p, "distance_m": round(d)}))
    ranked.sort(key=lambda x: x[0])
    return [p for _, p in ranked[:n]]


def _parse_md(s: str) -> tuple[int, int]:
    m, d = s.split("-", 1)
    return int(m), int(d)


def _day_in_annual_range(day: date, start_md: str, end_md: str) -> bool:
    sm, sd = _parse_md(start_md)
    em, ed = _parse_md(end_md)
    cur = (day.month, day.day)
    start = (sm, sd)
    end = (em, ed)
    if start <= end:
        return start <= cur <= end
    return cur >= start or cur <= end


def festival_visible_in_travel_window(
    schedule: dict[str, Any],
    travel_start: date,
    travel_end: date,
    selected_weekdays: list[int] | None,
) -> bool:
    """여행 기간 ∩ 선택 요일 ∩ 축제 연간 구간이 비어 있지 않으면 True."""
    if travel_end < travel_start:
        travel_start, travel_end = travel_end, travel_start
    annual_start = str(schedule.get("annual_start") or "")
    annual_end = str(schedule.get("annual_end") or "")
    if not annual_start or not annual_end:
        return False
    fest_weekdays = schedule.get("weekdays")
    if fest_weekdays is not None and len(fest_weekdays) == 0:
        fest_weekdays = list(range(7))
    day = travel_start
    while day <= travel_end:
        wd = day.weekday()
        if selected_weekdays is not None and len(selected_weekdays) > 0 and wd not in selected_weekdays:
            day += timedelta(days=1)
            continue
        if fest_weekdays is not None and wd not in fest_weekdays:
            day += timedelta(days=1)
            continue
        if _day_in_annual_range(day, annual_start, annual_end):
            return True
        day += timedelta(days=1)
    return False
