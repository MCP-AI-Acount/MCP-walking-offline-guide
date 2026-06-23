#!/usr/bin/env python3
"""오프라인 번들 zip → 앱 「파일에서 가져오기」.

모드:
  --country-pack croatia|slovenia|italy-dolomiti  나라(또는 지역) 통째
  --city … --country …                            도시 주변 5km (기본)
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
import time
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INGEST = Path(__file__).resolve().parent
if str(INGEST) not in sys.path:
    sys.path.insert(0, str(INGEST))

from region_utils import bbox_dict, load_bbox_file  # noqa: E402

SCHEMA = "wog-region-import-v1"
TILE_URL = "https://{s}.basemaps.cartocdn.com/rastertiles/voyager_nolabels/{z}/{x}/{y}@2x.png"
SUBS = ("a", "b", "c", "d")
CITY_ZOOMS = tuple(range(10, 19))
MIN_TILE_BYTES = 8000
BLOCKED_MD5 = "c069a15b2cc2d6b6f527ad09eb93c61a"
USER_AGENT = "WalkingOfflineGuide/1.0 (region-export; build-time)"
NOMINATIM = "https://nominatim.openstreetmap.org/search"
OVERPASS = "https://overpass-api.de/api/interpreter"
DEFAULT_RADIUS_KM = 5.0
SLEEP_TILE = 0.12
SLEEP_NOMINATIM = 1.1

# 나라(지역) 통째 — ingest/bundle_budget.json country_zooms 와 동일 계열
COUNTRY_PACKS: dict[str, dict] = {
    "croatia": {
        "file": "croatia_bbox.json",
        "zooms": (9, 10, 11, 12, 13),
        "poi_max": 5000,
        "note": "크로아티아 전국",
    },
    "slovenia": {
        "file": "slovenia_bbox.json",
        "zooms": (9, 10, 11, 12, 13),
        "poi_max": 4000,
        "note": "슬로베니아 전국",
    },
    "italy-dolomiti": {
        "file": "dolomiti_venezia_bbox.json",
        "zooms": (10, 11, 12, 13, 14),
        "poi_max": 3000,
        "note": "이탈리아 북부(돌로미티·베네치아) — 이탈리아 전국 아님",
    },
}


def radius_bbox(lat: float, lon: float, radius_km: float) -> dict:
    dlat = radius_km / 111.0
    dlon = radius_km / (111.0 * math.cos(math.radians(lat)))
    return {
        "south": lat - dlat,
        "north": lat + dlat,
        "west": lon - dlon,
        "east": lon + dlon,
    }


def bbox_center(bbox: dict) -> tuple[float, float]:
    return (
        (bbox["south"] + bbox["north"]) / 2.0,
        (bbox["west"] + bbox["east"]) / 2.0,
    )


def deg2num(lat_deg: float, lon_deg: float, zoom: int) -> tuple[int, int]:
    lat_rad = math.radians(lat_deg)
    n = 2**zoom
    xtile = int((lon_deg + 180.0) / 360.0 * n)
    ytile = int((1.0 - math.asinh(math.tan(lat_rad)) / math.pi) / 2.0 * n)
    return xtile, ytile


def tile_range(bbox: dict, zoom: int) -> tuple[int, int, int, int]:
    xs: list[int] = []
    ys: list[int] = []
    for lat in (bbox["south"], bbox["north"]):
        for lon in (bbox["west"], bbox["east"]):
            x, y = deg2num(lat, lon, zoom)
            xs.append(x)
            ys.append(y)
    return min(xs), max(xs), min(ys), max(ys)


def estimate_tile_count(bbox: dict, zooms: tuple[int, ...]) -> int:
    total = 0
    for z in zooms:
        x0, x1, y0, y1 = tile_range(bbox, z)
        total += (x1 - x0 + 1) * (y1 - y0 + 1)
    return total


def nominatim_geocode(city: str, country: str) -> tuple[float, float, str]:
    q = f"{city}, {country}" if country else city
    params = urllib.parse.urlencode(
        {"q": q, "format": "json", "limit": 1, "accept-language": "ko,en"},
    )
    req = urllib.request.Request(
        f"{NOMINATIM}?{params}",
        headers={"User-Agent": USER_AGENT},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        rows = json.loads(resp.read().decode())
    if not rows:
        raise SystemExit(f"좌표를 찾지 못했습니다: {q}")
    row = rows[0]
    desc = (row.get("display_name") or "").strip()
    return float(row["lat"]), float(row["lon"]), desc


def is_blocked(data: bytes) -> bool:
    if len(data) < MIN_TILE_BYTES:
        return True
    return hashlib.md5(data).hexdigest() == BLOCKED_MD5


def fetch_tile(z: int, x: int, y: int) -> bytes | None:
    host = SUBS[(x + y) % len(SUBS)]
    url = TILE_URL.format(s=host, z=z, x=x, y=y)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            if resp.status != 200:
                return None
            data = resp.read()
            if is_blocked(data):
                return None
            return data
    except Exception:
        return None


def fetch_pois(bbox: dict, *, poi_max: int = 800, timeout_s: int = 120) -> list[dict]:
    s, w, n, e = bbox["south"], bbox["west"], bbox["north"], bbox["east"]
    query = f"""
    [out:json][timeout:180];
    (
      node["tourism"]({s},{w},{n},{e});
      way["tourism"]({s},{w},{n},{e});
      node["historic"]({s},{w},{n},{e});
      way["historic"]({s},{w},{n},{e});
      node["amenity"~"restaurant|cafe|fast_food|bar"]({s},{w},{n},{e});
      node["tourism"~"hotel|guest_house|hostel"]({s},{w},{n},{e});
    );
    out center tags;
    """
    data = urllib.parse.urlencode({"data": query}).encode()
    req = urllib.request.Request(OVERPASS, data=data, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=timeout_s) as resp:
        payload = json.loads(resp.read().decode())
    pois: list[dict] = []
    for el in payload.get("elements") or []:
        tags = el.get("tags") or {}
        name = tags.get("name:ko") or tags.get("name:en") or tags.get("name") or ""
        if not name.strip():
            continue
        lat = el.get("lat") or (el.get("center") or {}).get("lat")
        lon = el.get("lon") or (el.get("center") or {}).get("lon")
        if lat is None or lon is None:
            continue
        kind = "attraction"
        tourism = tags.get("tourism") or tags.get("historic") or "attraction"
        if tourism in ("hotel", "guest_house", "hostel", "motel"):
            kind = "hotel"
        elif tags.get("amenity") in ("restaurant", "cafe", "fast_food", "bar", "food_court"):
            kind = "restaurant"
        pois.append(
            {
                "id": f"osm/{el.get('type')}/{el.get('id')}",
                "kind": kind,
                "name_ko": name,
                "lat": float(lat),
                "lon": float(lon),
                "description_ko": (tags.get("description") or tags.get("wikipedia") or "")[:400],
                "tourism": tourism,
            },
        )
        if len(pois) >= poi_max:
            break
    return pois


def write_bundle(
    out_zip: Path,
    *,
    city_name: str,
    country_label: str,
    lat: float,
    lon: float,
    bbox: dict,
    description: str,
    pois: list[dict],
    tiles: dict[tuple[int, int, int], bytes],
) -> None:
    manifest = {
        "schema": SCHEMA,
        "city_name": city_name,
        "country_label": country_label,
        "lat": lat,
        "lon": lon,
        "bbox": bbox,
    }
    poi_bundle = {
        "region": "",
        "label_ko": city_name,
        "bbox": bbox,
        "count": len(pois),
        "pois": pois,
    }
    out_zip.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out_zip, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("import_manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
        if description.strip():
            zf.writestr("description.txt", description.strip())
        zf.writestr("poi.json", json.dumps(poi_bundle, ensure_ascii=False, indent=2))
        for (z, x, y), data in sorted(tiles.items()):
            zf.writestr(f"tiles/{z}/{x}/{y}.png", data)


def collect_tiles(bbox: dict, zooms: tuple[int, ...]) -> dict[tuple[int, int, int], bytes]:
    tiles: dict[tuple[int, int, int], bytes] = {}
    seen: set[tuple[int, int, int]] = set()
    est = estimate_tile_count(bbox, zooms)
    print(f"  예상 타일 약 {est}장 · 줌 {list(zooms)}", file=sys.stderr)
    done = 0
    for z in zooms:
        x0, x1, y0, y1 = tile_range(bbox, z)
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                key = (z, x, y)
                if key in seen:
                    continue
                seen.add(key)
                data = fetch_tile(z, x, y)
                if data:
                    tiles[key] = data
                done += 1
                if done % 100 == 0:
                    mb = sum(len(v) for v in tiles.values()) / (1024 * 1024)
                    print(f"  진행 {done}/{est} · 받음 {len(tiles)}장 · {mb:.0f}MB", file=sys.stderr)
                time.sleep(SLEEP_TILE)
    return tiles


def load_country_pack(pack_id: str) -> tuple[dict, str, str, tuple[int, ...], int]:
    spec = COUNTRY_PACKS.get(pack_id)
    if not spec:
        raise SystemExit(f"알 수 없는 --country-pack: {pack_id} (가능: {', '.join(COUNTRY_PACKS)})")
    raw = load_bbox_file(spec["file"])
    bbox = bbox_dict(raw)
    label = raw.get("label_ko") or raw.get("id") or pack_id
    country_label = label
    zooms = tuple(spec["zooms"])
    poi_max = int(spec.get("poi_max") or 3000)
    return bbox, label, country_label, zooms, poi_max


def main() -> None:
    ap = argparse.ArgumentParser(
        description="오프라인 번들 zip (앱 「파일에서 가져오기」용)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "나라 통째 예:\n"
            "  python3 ingest/export_region_bundle.py --country-pack croatia\n"
            "  python3 ingest/export_region_bundle.py --country-pack slovenia\n"
            "도시 예:\n"
            "  python3 ingest/export_region_bundle.py --city 베네치아 --country 이탈리아\n"
        ),
    )
    ap.add_argument(
        "--country-pack",
        choices=sorted(COUNTRY_PACKS.keys()),
        help="나라(지역) 통째 번들 — croatia, slovenia, italy-dolomiti",
    )
    ap.add_argument("--city", help="도시 이름 (--country-pack 없을 때 필수)")
    ap.add_argument("--country", default="", help="국가 이름 (지오코딩·표시용)")
    ap.add_argument("--lat", type=float, default=0.0)
    ap.add_argument("--lon", type=float, default=0.0)
    ap.add_argument("--radius-km", type=float, default=DEFAULT_RADIUS_KM)
    ap.add_argument("--skip-poi", action="store_true", help="명소(POI) 생략 — 지도만")
    ap.add_argument("--skip-tiles", action="store_true", help="타일 생략 (테스트)")
    ap.add_argument("-o", "--out", type=Path, help="출력 zip 경로")
    args = ap.parse_args()

    if args.country_pack:
        bbox, city_name, country_label, zooms, poi_max = load_country_pack(args.country_pack)
        lat, lon = bbox_center(bbox)
        desc = COUNTRY_PACKS[args.country_pack].get("note") or ""
        print(f"나라(지역) 번들: {city_name} ({args.country_pack})", file=sys.stderr)
        if desc:
            print(f"  ※ {desc}", file=sys.stderr)
        poi_timeout = 300
    else:
        if not args.city:
            ap.error("--city 또는 --country-pack 중 하나는 필수입니다.")
        zooms = CITY_ZOOMS
        poi_max = 800
        poi_timeout = 120
        if args.lat != 0.0 and args.lon != 0.0:
            lat, lon, desc = args.lat, args.lon, ""
        else:
            lat, lon, desc = nominatim_geocode(args.city, args.country)
            time.sleep(SLEEP_NOMINATIM)
        bbox = radius_bbox(lat, lon, args.radius_km)
        city_name = args.city
        country_label = args.country

    pois: list[dict] = []
    if not args.skip_poi:
        print("POI 수집 중…", file=sys.stderr)
        pois = fetch_pois(bbox, poi_max=poi_max, timeout_s=poi_timeout)
        print(f"  POI {len(pois)}개", file=sys.stderr)

    tiles: dict[tuple[int, int, int], bytes] = {}
    if not args.skip_tiles:
        print("지도 타일 수집 중… (나라 통째는 수 시간·수 GB 될 수 있음)", file=sys.stderr)
        tiles = collect_tiles(bbox, zooms)
        print(f"  타일 {len(tiles)}장", file=sys.stderr)

    if args.out:
        out = args.out
    elif args.country_pack:
        out = Path.cwd() / f"wog-country-{args.country_pack}.zip"
    else:
        safe = "".join(c if c.isalnum() or c in "-_" else "_" for c in city_name)[:40]
        out = Path.cwd() / f"wog-{safe}.zip"

    write_bundle(
        out,
        city_name=city_name,
        country_label=country_label,
        lat=lat,
        lon=lon,
        bbox=bbox,
        description=desc,
        pois=pois,
        tiles=tiles,
    )
    size_mb = out.stat().st_size / (1024 * 1024)
    print(f"OK: {out.resolve()} ({size_mb:.1f} MB)")
    print(
        "\n다음: zip을 폰 Download(다운로드) 폴더로 복사 → 앱 「파일에서 가져오기」",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
