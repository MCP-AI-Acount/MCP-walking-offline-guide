"""문화제 기간·요일 필터 — ingest·tests 공유."""

from __future__ import annotations

from datetime import date
from typing import Any

from geo import festival_visible_in_travel_window  # noqa: I001


def filter_festivals(
    pois: list[dict[str, Any]],
    travel_start: date,
    travel_end: date,
    selected_weekdays: list[int] | None,
) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    for p in pois:
        if p.get("kind") != "festival":
            continue
        sched = p.get("schedule") or {}
        if festival_visible_in_travel_window(sched, travel_start, travel_end, selected_weekdays):
            out.append(p)
    return out
