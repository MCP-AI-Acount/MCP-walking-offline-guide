#!/usr/bin/env python3
"""돌로미티·베네치아 bbox OSM 관광 POI + curated (한국어) ingest."""

from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INGEST = Path(__file__).resolve().parent
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
if str(INGEST) not in sys.path:
    sys.path.insert(0, str(INGEST))

from geo import in_bbox  # noqa: E402
from budget import poi_limits  # noqa: E402
from korean_poi import expand_description_ko, is_garbage_name, parse_osm_rating, to_korean_name  # noqa: E402
from region_utils import bbox_dict, load_bbox_file, load_catalog  # noqa: E402

_POI_LIM = poi_limits()
OVERPASS = "https://overpass-api.de/api/interpreter"
BBOX_FILE = INGEST / "dolomiti_venezia_bbox.json"
CURATED_ATTR = INGEST / "attractions_curated.json"
CURATED_FEST = INGEST / "festivals_curated.json"
DEFAULT_OUT = ASSETS / "poi.json"
MAX_POI_JSON_BYTES = int(_POI_LIM["max_json_bytes"])
DEFAULT_MAX_POIS = int(_POI_LIM["max_pois"])
DESC_MAX_CHARS = int(_POI_LIM["description_max_chars"])
HOTEL_MAX = int(_POI_LIM.get("hotel_max") or 0)
INCLUDE_HOTELS = bool(_POI_LIM.get("include_hotels"))
SKIP_TOURISM = frozenset({"information", "artwork"})
HOTEL_TOURISM = frozenset({"hotel", "guest_house", "hostel", "motel", "chalet"})
RESTAURANT_AMENITY = frozenset({"restaurant", "cafe", "fast_food", "bar", "food_court"})
RESTAURANT_MAX = int(_POI_LIM.get("restaurant_max") or 300)


def load_bbox() -> dict:
    return json.loads(BBOX_FILE.read_text(encoding="utf-8"))


def load_curated_file(path: Path) -> list[dict]:
    if not path.is_file():
        return []
    raw = json.loads(path.read_text(encoding="utf-8"))
    out: list[dict] = []
    for item in raw:
        kind = item.get("kind") or "attraction"
        row = {
            "id": item["id"],
            "kind": kind,
            "name_ko": item["name_ko"],
            "lat": item["lat"],
            "lon": item["lon"],
            "description_ko": item["description_ko"][:DESC_MAX_CHARS],
        }
        if kind == "festival":
            row["schedule"] = item["schedule"]
        else:
            row["tourism"] = item.get("tourism") or "attraction"
            row["rating"] = float(item.get("rating") or 4.8)
        out.append(row)
    return out


def load_curated_attractions() -> list[dict]:
    return load_curated_file(CURATED_ATTR)


def load_curated_festivals() -> list[dict]:
    return load_curated_file(CURATED_FEST)


def overpass_query(bbox: dict, *, hotel_max: int, restaurant_max: int) -> str:
    s, w, n, e = bbox["south"], bbox["west"], bbox["north"], bbox["east"]
    hotel_block = ""
    if INCLUDE_HOTELS and hotel_max > 0:
        hotel_block = f"""
  node["tourism"~"hotel|guest_house|hostel|motel"]({s},{w},{n},{e});
  node["building"="hotel"]({s},{w},{n},{e});
"""
    restaurant_block = ""
    if restaurant_max > 0:
        restaurant_block = f"""
  node["amenity"~"restaurant|cafe|fast_food|bar|food_court"]({s},{w},{n},{e});
"""
    return f"""
[out:json][timeout:180];
(
  node["tourism"~"attraction|museum|viewpoint"]({s},{w},{n},{e});
  node["historic"~"castle|ruins|monument|archaeological_site|fort"]({s},{w},{n},{e});
  way["tourism"~"attraction|museum"]({s},{w},{n},{e});
{restaurant_block}{hotel_block});
out center tags;
"""


def fetch_osm(
    bbox: dict,
    *,
    skip_ids: set[str],
    hotel_max: int = HOTEL_MAX,
    restaurant_max: int = RESTAURANT_MAX,
) -> list[dict]:
    q = overpass_query(bbox, hotel_max=hotel_max, restaurant_max=restaurant_max)
    req = urllib.request.Request(
        OVERPASS,
        data=urllib.parse.urlencode({"data": q}).encode("utf-8"),
        method="POST",
        headers={
            "User-Agent": "MCP-Auto/italy-offline-guide ingest/0.2",
            "Accept": "*/*",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.loads(resp.read().decode("utf-8"))
    pois: list[dict] = []
    seen: set[str] = set()
    hotel_count = 0
    restaurant_count = 0
    for el in data.get("elements") or []:
        tags = el.get("tags") or {}
        amenity = tags.get("amenity") or ""
        is_restaurant = amenity in RESTAURANT_AMENITY
        tourism = tags.get("tourism") or tags.get("historic") or ("hotel" if tags.get("building") == "hotel" else "poi")
        is_hotel = tourism in HOTEL_TOURISM or tags.get("building") == "hotel"
        if is_hotel:
            if hotel_count >= hotel_max:
                continue
            if not tags.get("name") and not tags.get("name:ko"):
                continue
        if is_restaurant:
            if restaurant_count >= restaurant_max:
                continue
            if not tags.get("name") and not tags.get("name:ko"):
                continue
        name_raw = tags.get("name:ko") or tags.get("name") or tags.get("name:it") or tags.get("name:en") or tags.get("name:hr") or tags.get("name:sl")
        if not name_raw or is_garbage_name(str(name_raw)):
            continue
        if tourism in SKIP_TOURISM and not tags.get("name:ko"):
            continue
        if el.get("type") == "node":
            lat, lon = el.get("lat"), el.get("lon")
        else:
            center = el.get("center") or {}
            lat, lon = center.get("lat"), center.get("lon")
        if lat is None or lon is None:
            continue
        if not in_bbox(float(lat), float(lon), bbox):
            continue
        oid = f"osm/{el.get('type')}/{el.get('id')}"
        if oid in seen or oid in skip_ids:
            continue
        seen.add(oid)
        if is_hotel:
            hotel_count += 1
        if is_restaurant:
            restaurant_count += 1
        name_ko = to_korean_name(str(name_raw), tags)
        desc = expand_description_ko(name_ko, tags, float(lat), float(lon))
        if is_hotel:
            kind = "hotel"
        elif is_restaurant:
            kind = "restaurant"
        else:
            kind = "attraction"
        rating = parse_osm_rating(tags, kind)
        row = {
            "id": oid,
            "kind": kind,
            "name_ko": name_ko,
            "lat": float(lat),
            "lon": float(lon),
            "description_ko": desc[:DESC_MAX_CHARS],
            "tourism": "restaurant" if is_restaurant else (tourism if not is_hotel else "hotel"),
        }
        if rating is not None:
            row["rating"] = round(rating, 1)
        pois.append(row)
    pois.sort(key=lambda p: p["name_ko"])
    return pois


def _merge_curated_osm(curated: list[dict], osm: list[dict], max_pois: int) -> list[dict]:
    festivals = [p for p in curated if p.get("kind") == "festival"]
    curated_attr = [p for p in curated if p.get("kind") != "festival"]
    curated_ids = {p["id"] for p in curated_attr}
    merged = list(curated_attr)
    for p in osm:
        if p["id"] not in curated_ids:
            merged.append(p)
    cap = max(max_pois - len(festivals), len(festivals))
    return merged[: max(0, cap - len(festivals))] + festivals


def preserve_photos(all_pois: dict[str, list[dict]], out_path: Path) -> dict[str, list[dict]]:
    if not out_path.is_file():
        return all_pois
    try:
        prev = json.loads(out_path.read_text(encoding="utf-8"))
    except Exception:
        return all_pois
    by_id: dict[str, str] = {}
    for key in ("pois", "croatia_pois", "slovenia_pois", "korea_pois"):
        for p in prev.get(key) or []:
            if p.get("photo"):
                by_id[p["id"]] = p["photo"]
    out: dict[str, list[dict]] = {}
    for key, pois in all_pois.items():
        patched: list[dict] = []
        for p in pois:
            q = dict(p)
            if not q.get("photo") and p["id"] in by_id:
                q["photo"] = by_id[p["id"]]
            patched.append(q)
        out[key] = patched
    return out


def preserve_sidecar(out_path: Path) -> dict:
    if not out_path.is_file():
        return {}
    try:
        prev = json.loads(out_path.read_text(encoding="utf-8"))
    except Exception:
        return {}
    side: dict = {}
    for key in ("korea_pois", "korea_bbox"):
        if prev.get(key):
            side[key] = prev[key]
    return side


def build_region_pois(
    bbox: dict,
    *,
    max_pois: int,
    curated_attr: Path | None,
    curated_fest: Path | None,
    dry_run: bool,
    hotel_max: int = HOTEL_MAX,
    restaurant_max: int = RESTAURANT_MAX,
) -> list[dict]:
    curated: list[dict] = []
    if curated_attr:
        curated.extend(load_curated_file(curated_attr))
    if curated_fest:
        curated.extend(load_curated_file(curated_fest))
    if dry_run:
        return curated[:max_pois]
    skip = {p["id"] for p in curated if p.get("kind") != "festival"}
    osm = fetch_osm(
        bbox,
        skip_ids=skip,
        hotel_max=hotel_max,
        restaurant_max=restaurant_max,
    )
    return _merge_curated_osm(curated, osm, max_pois)


def main() -> int:
    ap = argparse.ArgumentParser(description="Adriatic POI ingest (Italy + Croatia + Slovenia)")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--max-pois", type=int, default=DEFAULT_MAX_POIS)
    ap.add_argument("--italy-only", action="store_true")
    args = ap.parse_args()
    catalog = load_catalog()
    primary = catalog["primary"]
    primary_bbox = load_bbox_file(primary["file"])
    primary_pois = build_region_pois(
        primary_bbox,
        max_pois=args.max_pois,
        curated_attr=INGEST / (primary.get("curated_attr") or "attractions_curated.json"),
        curated_fest=INGEST / (primary.get("curated_fest") or "festivals_curated.json"),
        dry_run=args.dry_run,
    )
    poi_groups = {"pois": primary_pois}
    out: dict = {
        "region": primary_bbox["id"],
        "label_ko": primary_bbox.get("label_ko"),
        "bbox": bbox_dict(primary_bbox),
        "count": len(primary_pois),
        "pois": primary_pois,
    }
    if not args.italy_only:
        for spec in catalog.get("extra") or []:
            bbox = load_bbox_file(spec["file"])
            curated_attr = INGEST / f"attractions_curated_{bbox['id']}.json"
            pois = build_region_pois(
                bbox,
                max_pois=int(spec.get("max_pois") or 800),
                curated_attr=curated_attr,
                curated_fest=None,
                dry_run=args.dry_run,
                hotel_max=int(spec.get("hotel_max") or 120),
                restaurant_max=int(spec.get("restaurant_max") or 150),
            )
            poi_groups[spec["poi_key"]] = pois
            out[spec["bbox_key"]] = bbox_dict(bbox)
            out[spec["label_key"]] = bbox.get("label_ko")
            out[spec.get("count_key") or f"{bbox['id']}_count"] = len(pois)
            out[spec["poi_key"]] = pois
    poi_groups = preserve_photos(poi_groups, args.out)
    for key, pois in poi_groups.items():
        out[key] = pois
        if key == "pois":
            out["count"] = len(pois)
    out.update(preserve_sidecar(args.out))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(out, ensure_ascii=False, indent=2) + "\n"
    if len(payload.encode("utf-8")) > MAX_POI_JSON_BYTES:
        print(json.dumps({"ok": False, "error": "poi_json_exceeds_budget"}, ensure_ascii=False), file=sys.stderr)
        return 1
    args.out.write_text(payload, encoding="utf-8")
    print(json.dumps({"ok": True, "out": str(args.out), "counts": {k: len(v) for k, v in poi_groups.items()}}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
