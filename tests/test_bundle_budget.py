"""Bundle budget ingest limits."""

from __future__ import annotations

import json
import sys
from pathlib import Path

INGEST = Path(__file__).resolve().parents[1] / "ingest"
sys.path.insert(0, str(INGEST))

from budget import load_budget  # noqa: E402


def test_bundle_budget_one_gb_alloc() -> None:
    b = load_budget()
    assert b["apk_target_mb"] == 1024
    alloc = b["alloc_mb"]
    assert sum(alloc.values()) <= 1024
    assert b["poi"]["max_pois"] >= 2000
    assert b["photos"]["max_edge_px"] <= 480
    assert b["photos"]["max_file_bytes"] <= 200 * 1024
    assert "festival" in b["photos"]["skip_kinds"]
    assert b["tiles"]["max_total_bytes"] >= 400 * 1024 * 1024
    test_gps = b["tiles"]["test_gps"]
    assert test_gps["radius_km"] == 1
    assert 37.4 < test_gps["lat"] < 37.6
    assert 127.0 < test_gps["lon"] < 127.2


def test_bundle_budget_json_valid() -> None:
    raw = (INGEST / "bundle_budget.json").read_text(encoding="utf-8")
    data = json.loads(raw)
    assert data["id"].startswith("italy-offline-guide")
