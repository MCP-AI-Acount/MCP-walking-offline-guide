#!/usr/bin/env python3
"""poi.json에 rating 필드 주입 — Overpass 없이 curated·안정 hash."""

from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
POI_JSON = ASSETS / "poi.json"
CURATED_ATTR = Path(__file__).resolve().parent / "attractions_curated.json"

CURATED_DEFAULT = 4.8


def stable_rating(poi: dict) -> float:
    kind = poi.get("kind") or "attraction"
    base = {"hotel": 3.6, "restaurant": 3.8, "attraction": 4.0}.get(kind, 3.5)
    seed = abs(hash(poi.get("id", ""))) % 10
    return round(min(5.0, max(3.0, base + seed / 10.0)), 1)


def main() -> int:
    if not POI_JSON.is_file():
        print(json.dumps({"ok": False, "error": "poi.json missing"}))
        return 1
    bundle = json.loads(POI_JSON.read_text(encoding="utf-8"))
    curated_ratings: dict[str, float] = {}
    if CURATED_ATTR.is_file():
        for item in json.loads(CURATED_ATTR.read_text(encoding="utf-8")):
            curated_ratings[item["id"]] = float(item.get("rating") or CURATED_DEFAULT)

    def patch_list(pois: list[dict]) -> int:
        n = 0
        for p in pois:
            if p.get("rating") is not None:
                continue
            if p["id"] in curated_ratings:
                p["rating"] = curated_ratings[p["id"]]
            else:
                p["rating"] = stable_rating(p)
            n += 1
        return n

    n_main = patch_list(bundle.get("pois") or [])
    n_kr = patch_list(bundle.get("korea_pois") or [])
    POI_JSON.write_text(json.dumps(bundle, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"ok": True, "patched": n_main + n_kr, "italy": n_main, "korea": n_kr}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
