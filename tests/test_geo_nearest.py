"""GPS nearest · festival filter — italy-offline-guide."""

from __future__ import annotations

import json
import sys
from datetime import date
from pathlib import Path

INGEST = Path(__file__).resolve().parents[1] / "ingest"
sys.path.insert(0, str(INGEST))

from festival import filter_festivals  # noqa: E402
from geo import festival_visible_in_travel_window, nearest_pois  # noqa: E402

SAMPLE = [
    {"id": "a", "kind": "attraction", "name": "Near", "lat": 45.434, "lon": 12.339, "description_ko": "x"},
    {"id": "b", "kind": "attraction", "name": "Mid", "lat": 45.44, "lon": 12.34, "description_ko": "x"},
    {"id": "c", "kind": "attraction", "name": "Far", "lat": 46.6, "lon": 12.3, "description_ko": "x"},
]

FEST = {
    "id": "f1",
    "kind": "festival",
    "name": "Test Fest",
    "lat": 45.434,
    "lon": 12.338,
    "description_ko": "x",
    "schedule": {"annual_start": "02-01", "annual_end": "02-15", "weekdays": [5, 6]},
}


def test_nearest_attractions_order() -> None:
    got = nearest_pois(SAMPLE, 45.434, 12.339, n=2, kind="attraction")
    assert len(got) == 2
    assert got[0]["id"] == "a"
    assert got[0]["distance_m"] <= got[1]["distance_m"]


def test_festival_weekend_in_february() -> None:
    ok = festival_visible_in_travel_window(
        FEST["schedule"],
        date(2026, 2, 6),
        date(2026, 2, 8),
        [5, 6],
    )
    assert ok is True


def test_festival_excluded_wrong_weekday() -> None:
    bad = festival_visible_in_travel_window(
        FEST["schedule"],
        date(2026, 2, 2),
        date(2026, 2, 4),
        [0, 1, 2],
    )
    assert bad is False


def test_filter_festivals_list() -> None:
    pois = SAMPLE + [FEST]
    got = filter_festivals(pois, date(2026, 2, 7), date(2026, 2, 7), [5, 6])
    assert len(got) == 1
    assert got[0]["id"] == "f1"


def test_poi_json_schema_if_present() -> None:
    poi_file = Path(__file__).resolve().parents[1] / "android" / "app" / "src" / "main" / "assets" / "poi.json"
    if not poi_file.is_file():
        return
    data = json.loads(poi_file.read_text(encoding="utf-8"))
    assert "pois" in data
    assert data.get("region") == "dolomiti-venezia"
    for p in data["pois"]:
        assert "lat" in p and "lon" in p
        assert p.get("description_ko") or p.get("name_ko") or p.get("name")


if __name__ == "__main__":
    test_nearest_attractions_order()
    test_festival_weekend_in_february()
    test_festival_excluded_wrong_weekday()
    test_filter_festivals_list()
    test_poi_json_schema_if_present()
    print("ok")
