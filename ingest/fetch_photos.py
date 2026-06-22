#!/usr/bin/env python3
"""Wikimedia Commons — 관광지 사진 1장씩 (빌드 타임 · CC · 저해상도 · 장식 제외)."""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INGEST = Path(__file__).resolve().parent
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
if str(INGEST) not in sys.path:
    sys.path.insert(0, str(INGEST))

from budget import photo_limits  # noqa: E402

_PHOTO = photo_limits()
CURATED = INGEST / "attractions_curated.json"
POI_JSON = ASSETS / "poi.json"
PHOTO_DIR = ASSETS / "poi-photos"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"
MAX_PHOTO_BYTES = int(_PHOTO["max_total_bytes"])
MAX_PHOTO_COUNT = int(_PHOTO["max_count"])
DEFAULT_MAX_EDGE = int(_PHOTO["max_edge_px"])
MAX_FILE_BYTES = int(_PHOTO["max_file_bytes"])
SKIP_KINDS = frozenset(_PHOTO.get("skip_kinds") or [])
SKIP_TOURISM = frozenset(_PHOTO.get("skip_tourism") or [])
SLEEP = 1.0

# 아이콘·로고·지도·배너 등 비관광 이미지 제외
SKIP_TITLE_RE = re.compile(
    r"(icon|logo|logotype|flag|banner|sprite|button|emblem|symbol|coat.of.arms|"
    r"map|diagram|chart|locator|svg|seal|crest|pictogram|silhouette|"
    r"background|wallpaper|texture|pattern|qr.code|screenshot)",
    re.I,
)


def safe_filename(poi_id: str) -> str:
    return re.sub(r"[^a-zA-Z0-9._-]", "_", poi_id) + ".jpg"


def is_decorative_title(title: str) -> bool:
    t = title.strip()
    if not t or SKIP_TITLE_RE.search(t):
        return True
    if t.lower().endswith(".svg") or t.lower().endswith(".gif"):
        return True
    return False


def poi_wants_photo(p: dict) -> bool:
    if p.get("kind") in SKIP_KINDS:
        return False
    if p.get("tourism") in SKIP_TOURISM:
        return False
    if p.get("kind") == "hotel":
        return bool(p.get("photo_commons"))
    return p.get("kind") in ("attraction", None) or p.get("tourism") in (
        "attraction",
        "museum",
        "viewpoint",
        "castle",
        "archaeological_site",
        "theme_park",
        "zoo",
        "monument",
        "ruins",
        "gallery",
    )


def commons_search_image(query: str, *, max_edge: int | None = None) -> str | None:
    edge = max_edge or DEFAULT_MAX_EDGE
    params = urllib.parse.urlencode(
        {
            "action": "query",
            "format": "json",
            "generator": "search",
            "gsrsearch": f"file:{query}",
            "gsrlimit": "5",
            "prop": "imageinfo",
            "iiprop": "url",
            "iiurlwidth": str(edge),
        }
    )
    req = urllib.request.Request(f"{COMMONS_API}?{params}", headers={"User-Agent": "MCP-Auto/italy-guide/0.3"})
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        pages = data.get("query", {}).get("pages", {})
        for page in sorted(pages.values(), key=lambda x: x.get("index", 0)):
            title = page.get("title") or ""
            if is_decorative_title(title.replace("File:", "")):
                continue
            info = (page.get("imageinfo") or [{}])[0]
            url = info.get("thumburl") or info.get("url")
            if url:
                return url
    except Exception:
        return None
    return None


def commons_image_url(title: str, *, max_edge: int | None = None) -> str | None:
    if is_decorative_title(title):
        return None
    edge = max_edge or DEFAULT_MAX_EDGE
    params = urllib.parse.urlencode(
        {
            "action": "query",
            "format": "json",
            "titles": f"File:{title}" if not title.startswith("File:") else title,
            "prop": "imageinfo",
            "iiprop": "url",
            "iiurlwidth": str(edge),
        }
    )
    req = urllib.request.Request(f"{COMMONS_API}?{params}", headers={"User-Agent": "MCP-Auto/italy-guide/0.3"})
    try:
        with urllib.request.urlopen(req, timeout=45) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        pages = data.get("query", {}).get("pages", {})
        for page in pages.values():
            info = (page.get("imageinfo") or [{}])[0]
            return info.get("thumburl") or info.get("url")
    except Exception:
        return None
    return None


def resolve_image_url(title: str) -> str | None:
    url = commons_image_url(title)
    if url:
        return url
    return commons_search_image(title.replace(".jpg", "").replace("_", " "))


def download(url: str, dest: Path) -> int:
    req = urllib.request.Request(url, headers={"User-Agent": "MCP-Auto/italy-guide/0.3"})
    with urllib.request.urlopen(req, timeout=90) as resp:
        body = resp.read()
    if len(body) > MAX_FILE_BYTES:
        raise ValueError(f"photo_too_large:{len(body)}")
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(body)
    return len(body)


def download_with_retry(title_or_query: str, dest: Path, *, curated_title: str | None = None) -> int:
    title = curated_title or title_or_query
    for edge in (DEFAULT_MAX_EDGE, max(240, DEFAULT_MAX_EDGE // 2)):
        url = commons_image_url(title, max_edge=edge) if curated_title else commons_search_image(title_or_query, max_edge=edge)
        if not url:
            continue
        try:
            return download(url, dest)
        except ValueError:
            continue
    raise ValueError("no_acceptable_size")


def attach_photos_to_poi(pois: list[dict], photo_map: dict[str, str]) -> list[dict]:
    out: list[dict] = []
    for p in pois:
        q = dict(p)
        fn = photo_map.get(p["id"])
        if fn:
            q["photo"] = f"poi-photos/{fn}"
        else:
            q.pop("photo", None)
        out.append(q)
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="Fetch POI photos from Wikimedia")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    curated = json.loads(CURATED.read_text(encoding="utf-8"))
    photo_map: dict[str, str] = {}
    total_bytes = 0

    for item in curated:
        if total_bytes >= MAX_PHOTO_BYTES or len(photo_map) >= MAX_PHOTO_COUNT:
            break
        pid = item["id"]
        title = item.get("photo_commons")
        if not title:
            continue
        fn = safe_filename(pid)
        dest = PHOTO_DIR / fn
        if args.dry_run:
            if not dest.is_file():
                dest.write_bytes(b"\xff\xd8\xff")
            photo_map[pid] = fn
            continue
        if dest.is_file() and dest.stat().st_size > 1000:
            if dest.stat().st_size <= MAX_FILE_BYTES:
                photo_map[pid] = fn
                total_bytes += dest.stat().st_size
            continue
        try:
            nbytes = download_with_retry(title, dest, curated_title=title)
        except Exception as exc:
            print(json.dumps({"skip": pid, "reason": str(exc)[:80]}, ensure_ascii=False))
            continue
        total_bytes += nbytes
        photo_map[pid] = fn
        print(json.dumps({"ok": pid, "bytes": nbytes}, ensure_ascii=False))
        time.sleep(SLEEP)

    if POI_JSON.is_file():
        bundle = json.loads(POI_JSON.read_text(encoding="utf-8"))
        pois = bundle.get("pois") or []
        for p in pois:
            if p.get("photo") or p["id"] in photo_map:
                continue
            if not poi_wants_photo(p):
                continue
            if total_bytes >= MAX_PHOTO_BYTES or len(photo_map) >= MAX_PHOTO_COUNT:
                break
            if args.dry_run:
                fn = safe_filename(p["id"])
                dest = PHOTO_DIR / fn
                if not dest.is_file():
                    dest.write_bytes(b"\xff\xd8\xff")
                photo_map[p["id"]] = fn
                continue
            query = p.get("name_ko") or p.get("id", "")
            fn = safe_filename(p["id"])
            dest = PHOTO_DIR / fn
            if dest.is_file() and dest.stat().st_size > 1000 and dest.stat().st_size <= MAX_FILE_BYTES:
                photo_map[p["id"]] = fn
                total_bytes += dest.stat().st_size
                continue
            try:
                nbytes = download_with_retry(query, dest)
            except Exception:
                continue
            total_bytes += nbytes
            photo_map[p["id"]] = fn
            print(json.dumps({"ok": p["id"], "bytes": nbytes, "source": "search"}, ensure_ascii=False))
            time.sleep(SLEEP)

        bundle["pois"] = attach_photos_to_poi(pois, photo_map)
        POI_JSON.write_text(json.dumps(bundle, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(
        json.dumps(
            {
                "ok": True,
                "photos": len(photo_map),
                "totalBytes": total_bytes,
                "budgetBytes": MAX_PHOTO_BYTES,
                "maxEdge": DEFAULT_MAX_EDGE,
            },
            ensure_ascii=False,
        )
    )
    write_photo_registry(photo_map)
    return 0


def write_photo_registry(photo_map: dict[str, str]) -> None:
    """Kotlin 앱은 poi.photo 경로로 assets 직접 로드 — 레지스트리 생성 불필요."""
    _ = photo_map


if __name__ == "__main__":
    raise SystemExit(main())
