#!/usr/bin/env python3
"""Build-time OSM raster tiles → zip (런타임 네트워크 없음 · 1GB 번들 예산)."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INGEST = Path(__file__).resolve().parent
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
if str(INGEST) not in sys.path:
    sys.path.insert(0, str(INGEST))

from budget import tile_limits  # noqa: E402
from region_utils import load_bbox_file, load_catalog  # noqa: E402

_TILE = tile_limits()
_SOURCE = _TILE.get("source") or {}
TILE_URL_TEMPLATE = str(
    _SOURCE.get("url_template") or "https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png"
)
SUBDOMAINS = list(_SOURCE.get("subdomains") or ["a", "b", "c", "d"])
USER_AGENT = str(_SOURCE.get("user_agent") or "MCP-italy-offline-guide/1.0 (offline; build-time only)")
ATTRIBUTION = str(_SOURCE.get("attribution") or "© OpenStreetMap contributors © CARTO")
MIN_TILE_BYTES = int(_SOURCE.get("min_tile_bytes") or 8000)
BLOCKED_MD5 = str(_SOURCE.get("blocked_md5") or "c069a15b2cc2d6b6f527ad09eb93c61a")
DEFAULT_ZOOMS = tuple(int(z) for z in _TILE["default_zooms"])
DOLOMITI_ZOOMS = tuple(int(z) for z in (_TILE.get("dolomiti_zooms") or _TILE["default_zooms"]))
COUNTRY_ZOOMS = tuple(int(z) for z in (_TILE.get("country_zooms") or [9, 10, 11, 12]))
SLOVENIA_ZOOMS = tuple(int(z) for z in (_TILE.get("slovenia_zooms") or COUNTRY_ZOOMS))
MAX_TILE_BYTES_BUDGET = int(_TILE["max_total_bytes"])
MAX_TILE_COUNT = int(_TILE["max_count"])
UNLIMITED_TILES = MAX_TILE_BYTES_BUDGET <= 0 and MAX_TILE_COUNT <= 0
BBOX_FILE = INGEST / "dolomiti_venezia_bbox.json"
_TEST_GPS = _TILE.get("test_gps") or {}
TEST_GPS_CENTER = (float(_TEST_GPS.get("lat", 37.5122)), float(_TEST_GPS.get("lon", 127.1078)))
DOLOMITI_CENTER = (46.5, 12.0)
SLEEP_S = 0.25


def load_bbox() -> dict:
    return json.loads(BBOX_FILE.read_text(encoding="utf-8"))


def radius_bbox(lat: float, lon: float, radius_km: float) -> dict:
    dlat = radius_km / 111.0
    dlon = radius_km / (111.0 * math.cos(math.radians(lat)))
    return {
        "south": lat - dlat,
        "north": lat + dlat,
        "west": lon - dlon,
        "east": lon + dlon,
    }


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


def iter_tiles(bbox: dict, zooms: tuple[int, ...]):
    seen: set[tuple[int, int, int]] = set()
    for z in zooms:
        x0, x1, y0, y1 = tile_range(bbox, z)
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                key = (z, x, y)
                if key not in seen:
                    seen.add(key)
                    yield key


def is_blocked_tile(data: bytes) -> bool:
    if len(data) < MIN_TILE_BYTES:
        return True
    return hashlib.md5(data).hexdigest() == BLOCKED_MD5


def fetch_tile(z: int, x: int, y: int) -> bytes | None:
    host = SUBDOMAINS[(x + y + z) % len(SUBDOMAINS)]
    url = TILE_URL_TEMPLATE.format(s=host, z=z, x=x, y=y)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            if resp.status != 200:
                return None
            data = resp.read()
            if is_blocked_tile(data):
                return None
            return data
    except Exception:
        return None


def write_zip(tiles: dict[tuple[int, int, int], bytes], out_zip: Path) -> int:
    out_zip.parent.mkdir(parents=True, exist_ok=True)
    total = 0
    with zipfile.ZipFile(out_zip, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for (z, x, y), data in sorted(tiles.items()):
            arc = f"tiles/{z}/{x}/{y}.png"
            zf.writestr(arc, data)
            total += len(data)
    return total


def sample_tiles(bbox: dict) -> dict[tuple[int, int, int], bytes]:
    z = 10
    x, y = deg2num((bbox["south"] + bbox["north"]) / 2, (bbox["west"] + bbox["east"]) / 2, z)
    png_1x1 = (
        b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01"
        b"\x08\x02\x00\x00\x00\x90wS\xde\x00\x00\x00\x0cIDATx\x9cc\xf8\x0f\x00"
        b"\x00\x01\x01\x00\x05\x18\xd6N\x00\x00\x00\x00IEND\xaeB`\x82"
    )
    return {(z, x, y): png_1x1}


def tile_plan(full_bbox: dict) -> list[tuple[dict, tuple[int, ...], str]]:
    test_bbox = radius_bbox(*TEST_GPS_CENTER, float(_TEST_GPS.get("radius_km", 1)))
    test_zooms = tuple(int(z) for z in _TEST_GPS.get("zooms") or [15, 16])
    dolomiti = radius_bbox(*DOLOMITI_CENTER, float(_TILE["dolomiti_radius_km"]))
    plan: list[tuple[dict, tuple[int, ...], str]] = [
        (test_bbox, test_zooms, "test_gps_bangi"),
        (dolomiti, DOLOMITI_ZOOMS, "dolomiti"),
        (full_bbox, DEFAULT_ZOOMS, "italy_region"),
    ]
    catalog = load_catalog()
    for spec in catalog.get("extra") or []:
        bbox = load_bbox_file(spec["file"])
        zooms = SLOVENIA_ZOOMS if bbox["id"] == "slovenia" else COUNTRY_ZOOMS
        plan.append((bbox, zooms, bbox["id"]))
    return plan


def collect_tiles(plan: list[tuple[dict, tuple[int, ...], str]], *, dry_run: bool) -> dict[tuple[int, int, int], bytes]:
    if dry_run:
        return sample_tiles(plan[0][0])

    tiles: dict[tuple[int, int, int], bytes] = {}
    total_bytes = 0
    fetched = 0
    for bbox, zooms, label in plan:
        for i, (z, x, y) in enumerate(iter_tiles(bbox, zooms)):
            if not UNLIMITED_TILES:
                if MAX_TILE_COUNT > 0 and fetched >= MAX_TILE_COUNT:
                    return tiles
                if MAX_TILE_BYTES_BUDGET > 0 and total_bytes >= MAX_TILE_BYTES_BUDGET:
                    return tiles
            key = (z, x, y)
            if key in tiles:
                continue
            data = fetch_tile(z, x, y)
            if not data:
                continue
            if not UNLIMITED_TILES and MAX_TILE_BYTES_BUDGET > 0 and total_bytes + len(data) > MAX_TILE_BYTES_BUDGET:
                return tiles
            tiles[key] = data
            total_bytes += len(data)
            fetched += 1
            if fetched % 50 == 0:
                print(
                    json.dumps(
                        {
                            "progress": fetched,
                            "bytes": total_bytes,
                            "region": label,
                            "last": f"{z}/{x}/{y}",
                            "unlimited": UNLIMITED_TILES,
                        },
                        ensure_ascii=False,
                    ),
                    flush=True,
                )
            time.sleep(SLEEP_S)
    return tiles


def main() -> int:
    ap = argparse.ArgumentParser(description="Offline map tiles ingest")
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--out-zip", type=Path, default=ASSETS / "tiles.zip")
    ap.add_argument("--out-manifest", type=Path, default=ASSETS / "tiles-manifest.json")
    ap.add_argument("--zooms", default=",".join(str(z) for z in DEFAULT_ZOOMS), help="comma zoom levels (override)")
    args = ap.parse_args()
    full_bbox = load_bbox()
    plan = tile_plan(full_bbox)
    if not args.dry_run and args.zooms:
        z_override = tuple(int(z.strip()) for z in args.zooms.split(",") if z.strip())
        plan = [(b, z_override, n) for b, _, n in plan]

    tiles = collect_tiles(plan, dry_run=args.dry_run)
    nbytes = write_zip(tiles, args.out_zip)
    manifest = {
        "region": full_bbox["id"],
        "bbox": {k: full_bbox[k] for k in ("south", "west", "north", "east")},
        "zooms": list(DEFAULT_ZOOMS),
        "test_gps": {
            "label_ko": _TEST_GPS.get("label_ko"),
            "center": list(TEST_GPS_CENTER),
            "radius_km": _TEST_GPS.get("radius_km"),
            "zooms": _TEST_GPS.get("zooms"),
            "note": _TEST_GPS.get("note"),
        },
        "dolomiti": {"center": list(DOLOMITI_CENTER), "radius_km": _TILE["dolomiti_radius_km"]},
        "tileCount": len(tiles),
        "zipBytes": args.out_zip.stat().st_size,
        "budgetBytes": MAX_TILE_BYTES_BUDGET,
        "pathTemplate": "{dir}/tiles/{z}/{x}/{y}.png",
        "source": _SOURCE.get("id") or "carto-voyager",
        "attribution": ATTRIBUTION,
    }
    args.out_manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(
        json.dumps(
            {"ok": True, "tiles": len(tiles), "zip": str(args.out_zip), "bytes": nbytes, "budgetBytes": MAX_TILE_BYTES_BUDGET},
            ensure_ascii=False,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
