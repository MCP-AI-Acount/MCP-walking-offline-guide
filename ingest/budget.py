"""오프라인 번들 용량 예산 — ingest/bundle_budget.json 정본."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

BUDGET_FILE = Path(__file__).resolve().parent / "bundle_budget.json"


def load_budget() -> dict[str, Any]:
    return json.loads(BUDGET_FILE.read_text(encoding="utf-8"))


def poi_limits() -> dict[str, Any]:
    return load_budget()["poi"]


def photo_limits() -> dict[str, Any]:
    return load_budget()["photos"]


def tile_limits() -> dict[str, Any]:
    return load_budget()["tiles"]
