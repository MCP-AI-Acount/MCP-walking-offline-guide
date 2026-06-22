#!/usr/bin/env python3
"""방이동 GPS 테스트 구역 — 식당만 Overpass ingest."""

from __future__ import annotations

import json
import math
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INGEST = Path(__file__).resolve().parent
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
POI_JSON = ASSETS / "poi.json"
if str(INGEST) not in sys.path:
    sys.path.insert(0, str(INGEST))

from budget import load_budget, poi_limits  # noqa: E402
from korean_poi import expand_description_ko, is_garbage_name, parse_osm_rating, to_korean_name  # noqa: E402

OVERPASS = "https://overpass-api.de/api/interpreter"
RESTAURANT_AMENITY = frozenset({"restaurant", "cafe", "fast_food", "bar", "food_court"})
DESC_MAX = int(poi_limits()["description_max_chars"])
MAX_POIS = 80


def bangi_bbox() -> dict:
    tg = load_budget()["tiles"]["test_gps"]
    lat, lon = float(tg["lat"]), float(tg["lon"])
    r = float(tg.get("radius_km") or 1.0)
    dlat = r / 111.0
    dlon = r / (111.0 * math.cos(math.radians(lat)))
    return {
        "south": lat - dlat,
        "north": lat + dlat,
        "west": lon - dlon,
        "east": lon + dlon,
    }


def fetch_restaurants(bbox: dict) -> list[dict]:
    s, w, n, e = bbox["south"], bbox["west"], bbox["north"], bbox["east"]
    q = f"""
[out:json][timeout:90];
(
  node["amenity"~"restaurant|cafe|fast_food|bar|food_court"]({s},{w},{n},{e});
);
out body;
"""
    req = urllib.request.Request(
        OVERPASS,
        data=urllib.parse.urlencode({"data": q}).encode("utf-8"),
        method="POST",
        headers={
            "User-Agent": "MCP-Auto/italy-offline-guide bangi-poi/1.0",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    last_err: Exception | None = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            break
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
            last_err = exc
            if attempt < 3:
                time.sleep(5 * (attempt + 1))
                continue
            raise
    else:
        raise last_err or RuntimeError("overpass failed")
    out: list[dict] = []
    for el in data.get("elements") or []:
        if el.get("type") != "node":
            continue
        tags = el.get("tags") or {}
        amenity = tags.get("amenity") or ""
        if amenity not in RESTAURANT_AMENITY:
            continue
        name_raw = tags.get("name:ko") or tags.get("name") or tags.get("name:en")
        if not name_raw or is_garbage_name(str(name_raw)):
            continue
        lat, lon = el.get("lat"), el.get("lon")
        if lat is None or lon is None:
            continue
        oid = f"osm/node/{el.get('id')}"
        name_ko = to_korean_name(str(name_raw), tags)
        desc = expand_description_ko(name_ko, tags, float(lat), float(lon))
        rating = parse_osm_rating(tags, "restaurant")
        row = {
            "id": oid,
            "kind": "restaurant",
            "name_ko": name_ko,
            "lat": float(lat),
            "lon": float(lon),
            "description_ko": desc[:DESC_MAX],
            "tourism": "restaurant",
        }
        if rating is not None:
            row["rating"] = round(rating, 1)
        out.append(row)
    out.sort(key=lambda p: p["name_ko"])
    return out[:MAX_POIS]


def main() -> int:
    bbox = bangi_bbox()
    pois = fetch_restaurants(bbox)
    bundle = {}
    if POI_JSON.is_file():
        bundle = json.loads(POI_JSON.read_text(encoding="utf-8"))
    bundle["korea_pois"] = pois
    bundle["korea_bbox"] = bbox
    POI_JSON.write_text(json.dumps(bundle, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"ok": True, "korea_pois": len(pois)}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
