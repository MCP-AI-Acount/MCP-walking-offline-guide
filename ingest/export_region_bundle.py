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
CACHE_DIR = ROOT / "offline-bundles" / ".cache"
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


def split_bbox(bbox: dict, nx: int, ny: int) -> list[dict]:
    s, w, n, e = bbox["south"], bbox["west"], bbox["north"], bbox["east"]
    dlat = (n - s) / ny
    dlon = (e - w) / nx
    out: list[dict] = []
    for iy in range(ny):
        for ix in range(nx):
            out.append(
                {
                    "south": s + iy * dlat,
                    "north": s + (iy + 1) * dlat,
                    "west": w + ix * dlon,
                    "east": w + (ix + 1) * dlon,
                },
            )
    return out


def _parse_overpass_elements(payload: dict, *, poi_max: int) -> list[dict]:
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


def fetch_pois_single(bbox: dict, *, poi_max: int = 800, timeout_s: int = 120) -> list[dict]:
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
    last_err: Exception | None = None
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=timeout_s) as resp:
                payload = json.loads(resp.read().decode())
            return _parse_overpass_elements(payload, poi_max=poi_max)
        except Exception as err:
            last_err = err
            time.sleep(3 + attempt * 4)
    print(f"  ⚠ POI 구역 실패(건너뜀): {last_err}", file=sys.stderr, flush=True)
    return []


def poi_progress_path(cache_key: str) -> Path:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    return CACHE_DIR / f"{cache_key}-poi-progress.json"


def load_poi_progress(path: Path) -> tuple[int, dict[str, dict]]:
    if not path.is_file():
        return 0, {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        done = int(data.get("completed_chunks") or 0)
        merged: dict[str, dict] = {}
        for p in data.get("pois") or []:
            if isinstance(p, dict) and p.get("id"):
                merged[str(p["id"])] = p
        return done, merged
    except Exception:
        return 0, {}


def save_poi_progress(path: Path, *, completed_chunks: int, merged: dict[str, dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "completed_chunks": completed_chunks,
        "pois": list(merged.values()),
        "updated_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def fetch_pois(
    bbox: dict,
    *,
    poi_max: int = 800,
    timeout_s: int = 120,
    progress_path: Path | None = None,
) -> list[dict]:
    lat_span = bbox["north"] - bbox["south"]
    lon_span = bbox["east"] - bbox["west"]
    if lat_span <= 1.5 and lon_span <= 2.0:
        pois = fetch_pois_single(bbox, poi_max=poi_max, timeout_s=timeout_s)
        if progress_path and pois:
            save_poi_progress(progress_path, completed_chunks=1, merged={p["id"]: p for p in pois})
        return pois

    nx = max(2, min(4, int(lon_span / 1.5) + 1))
    ny = max(2, min(4, int(lat_span / 1.0) + 1))
    chunks = split_bbox(bbox, nx, ny)
    merged: dict[str, dict] = {}
    start_chunk = 0
    if progress_path:
        start_chunk, merged = load_poi_progress(progress_path)
        if start_chunk > 0 and merged:
            print(
                f"  POI 이어받기 — {start_chunk}/{len(chunks)}구역 완료 · 누적 {len(merged)}개",
                file=sys.stderr,
                flush=True,
            )
    print(f"  POI {len(chunks)}구역 분할 (Overpass 타임아웃 방지)", file=sys.stderr, flush=True)
    for i, sub in enumerate(chunks, 1):
        if i <= start_chunk:
            continue
        print(f"  POI 구역 {i}/{len(chunks)}…", file=sys.stderr, flush=True)
        batch = fetch_pois_single(sub, poi_max=poi_max, timeout_s=timeout_s)
        for p in batch:
            merged[p["id"]] = p
        if progress_path:
            save_poi_progress(progress_path, completed_chunks=i, merged=merged)
            ck = progress_path.stem.removesuffix("-poi-progress")
            if ck:
                save_checkpoint(
                    ck,
                    {
                        "cache_key": ck,
                        "phase": "poi",
                        "poi_chunks_done": i,
                        "poi_chunks_total": len(chunks),
                        "poi_n": len(merged),
                    },
                )
        if len(merged) >= poi_max:
            break
        time.sleep(2)
    return list(merged.values())[:poi_max]


def tile_cache_root(cache_key: str) -> Path:
    return CACHE_DIR / cache_key / "tiles"


def tile_cache_file(cache_key: str, z: int, x: int, y: int) -> Path:
    return tile_cache_root(cache_key) / str(z) / str(x) / f"{y}.png"


def checkpoint_path(cache_key: str) -> Path:
    return CACHE_DIR / cache_key / "checkpoint.json"


def bbox_fingerprint(bbox: dict, zooms: tuple[int, ...]) -> str:
    raw = json.dumps({"bbox": bbox, "zooms": list(zooms)}, sort_keys=True)
    return hashlib.sha256(raw.encode()).hexdigest()[:16]


def load_checkpoint(cache_key: str) -> dict | None:
    path = checkpoint_path(cache_key)
    if not path.is_file():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return None


def save_checkpoint(cache_key: str, payload: dict) -> None:
    path = checkpoint_path(cache_key)
    path.parent.mkdir(parents=True, exist_ok=True)
    payload["updated_at"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")


def clear_pack_cache(cache_key: str) -> None:
    import shutil

    for rel in (CACHE_DIR / cache_key, CACHE_DIR / f"{cache_key}-poi.json", CACHE_DIR / f"{cache_key}-poi-progress.json"):
        if rel.is_dir():
            shutil.rmtree(rel)
        elif rel.is_file():
            rel.unlink()


def count_cached_tiles(cache_key: str) -> tuple[int, int]:
    root = tile_cache_root(cache_key)
    if not root.is_dir():
        return 0, 0
    n = 0
    nbytes = 0
    for path in root.rglob("*.png"):
        if path.stat().st_size >= MIN_TILE_BYTES:
            n += 1
            nbytes += path.stat().st_size
    return n, nbytes


def read_cached_tile(cache_key: str, z: int, x: int, y: int) -> bytes | None:
    path = tile_cache_file(cache_key, z, x, y)
    if not path.is_file():
        return None
    data = path.read_bytes()
    if is_blocked(data):
        path.unlink(missing_ok=True)
        return None
    return data


def write_cached_tile(cache_key: str, z: int, x: int, y: int, data: bytes) -> None:
    path = tile_cache_file(cache_key, z, x, y)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def iter_tile_keys(bbox: dict, zooms: tuple[int, ...]):
    seen: set[tuple[int, int, int]] = set()
    for z in zooms:
        x0, x1, y0, y1 = tile_range(bbox, z)
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                key = (z, x, y)
                if key not in seen:
                    seen.add(key)
                    yield key


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
    tiles: dict[tuple[int, int, int], bytes] | None = None,
    tile_cache_key: str | None = None,
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
        if tile_cache_key:
            root = tile_cache_root(tile_cache_key)
            for path in sorted(root.rglob("*.png")):
                parts = path.relative_to(root).parts
                if len(parts) != 3:
                    continue
                z, x, y_name = parts
                y = y_name.removesuffix(".png")
                zf.write(path, arcname=f"tiles/{z}/{x}/{y}.png")
        elif tiles:
            for (z, x, y), data in sorted(tiles.items()):
                zf.writestr(f"tiles/{z}/{x}/{y}.png", data)


def collect_tiles(
    bbox: dict,
    zooms: tuple[int, ...],
    *,
    cache_key: str,
    fingerprint: str,
) -> int:
    est = estimate_tile_count(bbox, zooms)
    cached_n, cached_bytes = count_cached_tiles(cache_key)
    if cached_n:
        print(
            f"  타일 이어받기 — 디스크 {cached_n:,}장 · {cached_bytes / 1_048_576:.0f}MB",
            file=sys.stderr,
            flush=True,
        )
    print(f"  예상 타일 약 {est}장 · 줌 {list(zooms)}", file=sys.stderr, flush=True)
    done = 0
    received = cached_n
    for z, x, y in iter_tile_keys(bbox, zooms):
        done += 1
        if read_cached_tile(cache_key, z, x, y) is None:
            data = fetch_tile(z, x, y)
            if data:
                write_cached_tile(cache_key, z, x, y, data)
                received += 1
        if done % 25 == 0:
            received, tile_bytes = count_cached_tiles(cache_key)
            mb = tile_bytes / (1024 * 1024)
            print(
                f"  진행 {done}/{est} · 받음 {received}장 · {mb:.0f}MB",
                file=sys.stderr,
                flush=True,
            )
            save_checkpoint(
                cache_key,
                {
                    "cache_key": cache_key,
                    "fingerprint": fingerprint,
                    "phase": "tiles",
                    "tiles_done": done,
                    "tiles_total": est,
                    "tiles_received": received,
                    "tile_bytes": tile_bytes,
                },
            )
        time.sleep(SLEEP_TILE)
    received, _ = count_cached_tiles(cache_key)
    save_checkpoint(
        cache_key,
        {
            "cache_key": cache_key,
            "fingerprint": fingerprint,
            "phase": "tiles_complete",
            "tiles_done": est,
            "tiles_total": est,
            "tiles_received": received,
        },
    )
    return received


def poi_cache_path(pack_key: str) -> Path:
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    return CACHE_DIR / f"{pack_key}-poi.json"


def load_poi_cache(path: Path) -> list[dict] | None:
    if not path.is_file():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, list) else None
    except Exception:
        return None


def save_poi_cache(path: Path, pois: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(pois, ensure_ascii=False, indent=2), encoding="utf-8")


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
    ap.add_argument("--refresh-poi", action="store_true", help="POI 캐시 무시하고 다시 수집")
    ap.add_argument("--fresh", action="store_true", help="이 pack 캐시·체크포인트 삭제 후 처음부터")
    ap.add_argument(
        "--poi-first",
        action="store_true",
        help="POI 먼저(기본: 지도 타일 먼저 — 진행률이 바로 오름)",
    )
    ap.add_argument("-o", "--out", type=Path, help="출력 zip 경로")
    args = ap.parse_args()

    cache_key = args.country_pack or ""
    if not cache_key and args.city:
        safe = "".join(c if c.isalnum() or c in "-_" else "_" for c in args.city)[:40]
        cache_key = f"city-{safe}"

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

    fingerprint = bbox_fingerprint(bbox, zooms)
    if cache_key and args.fresh:
        clear_pack_cache(cache_key)
        print(f"캐시 삭제: {cache_key}", file=sys.stderr, flush=True)
    elif cache_key:
        old = load_checkpoint(cache_key)
        if old and old.get("fingerprint") not in (None, fingerprint):
            print(
                f"⚠ bbox/zoom 변경 — 캐시 무효화 ({cache_key})",
                file=sys.stderr,
                flush=True,
            )
            clear_pack_cache(cache_key)

    def load_pois() -> list[dict]:
        poi_cache: Path | None = poi_cache_path(cache_key) if cache_key else None
        poi_progress: Path | None = poi_progress_path(cache_key) if cache_key else None
        if args.refresh_poi and poi_progress and poi_progress.is_file():
            poi_progress.unlink(missing_ok=True)
        cached = None if args.refresh_poi else (load_poi_cache(poi_cache) if poi_cache else None)
        if cached:
            print(f"POI 캐시 사용 ({len(cached)}개) — {poi_cache}", file=sys.stderr, flush=True)
            return cached
        print("POI 수집 중…", file=sys.stderr, flush=True)
        fetched = fetch_pois(
            bbox,
            poi_max=poi_max,
            timeout_s=poi_timeout,
            progress_path=poi_progress,
        )
        print(f"  POI {len(fetched)}개", file=sys.stderr, flush=True)
        if poi_cache and fetched:
            save_poi_cache(poi_cache, fetched)
            print(f"  POI 캐시 저장 → {poi_cache}", file=sys.stderr, flush=True)
        if poi_progress and poi_progress.is_file():
            poi_progress.unlink(missing_ok=True)
        if cache_key:
            save_checkpoint(
                cache_key,
                {
                    "cache_key": cache_key,
                    "fingerprint": fingerprint,
                    "phase": "poi_complete",
                    "poi_n": len(fetched),
                },
            )
        return fetched

    pois: list[dict] = []
    tile_count = 0
    tiles_first = bool(args.country_pack and not args.poi_first)

    if tiles_first and not args.skip_tiles:
        print("지도 타일 수집 중… (나라 통째는 수 시간·수 GB 될 수 있음)", file=sys.stderr, flush=True)
        if not cache_key:
            ap.error("타일 이어받기는 --country-pack 또는 --city 필요")
        tile_count = collect_tiles(bbox, zooms, cache_key=cache_key, fingerprint=fingerprint)
        print(f"  타일 {tile_count}장", file=sys.stderr, flush=True)

    if not args.skip_poi:
        pois = load_pois()

    if not tiles_first and not args.skip_tiles:
        print("지도 타일 수집 중… (나라 통째는 수 시간·수 GB 될 수 있음)", file=sys.stderr, flush=True)
        if not cache_key:
            ap.error("타일 이어받기는 --country-pack 또는 --city 필요")
        tile_count = collect_tiles(bbox, zooms, cache_key=cache_key, fingerprint=fingerprint)
        print(f"  타일 {tile_count}장", file=sys.stderr, flush=True)

    if not tile_count and cache_key and not args.skip_tiles:
        tile_count = count_cached_tiles(cache_key)[0]

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
        tile_cache_key=cache_key if tile_count else None,
    )
    if cache_key and tile_count:
        save_checkpoint(
            cache_key,
            {
                "cache_key": cache_key,
                "fingerprint": fingerprint,
                "phase": "done",
                "poi_n": len(pois),
                "tiles_received": tile_count,
                "zip": str(out.resolve()),
            },
        )
    size_mb = out.stat().st_size / (1024 * 1024)
    print(f"OK: {out.resolve()} ({size_mb:.1f} MB)")
    print(
        "\n다음: zip을 폰 Download(다운로드) 폴더로 복사 → 앱 「파일에서 가져오기」",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
