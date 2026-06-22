"""Multi-region ingest helpers (Italy + Croatia + Slovenia)."""

from __future__ import annotations

import json
from pathlib import Path

INGEST = Path(__file__).resolve().parent
CATALOG_FILE = INGEST / "regions_catalog.json"


def load_catalog() -> dict:
    return json.loads(CATALOG_FILE.read_text(encoding="utf-8"))


def load_bbox_file(name: str) -> dict:
    return json.loads((INGEST / name).read_text(encoding="utf-8"))


def bbox_dict(bbox: dict) -> dict:
    return {k: bbox[k] for k in ("south", "west", "north", "east")}


def all_country_bboxes() -> list[dict]:
    cat = load_catalog()
    out = [load_bbox_file(cat["primary"]["file"])]
    for item in cat.get("extra") or []:
        out.append(load_bbox_file(item["file"]))
    return out
