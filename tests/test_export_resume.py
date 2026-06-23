"""export_region_bundle 이어받기 헬퍼 스모크."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
INGEST = ROOT / "ingest"
if str(INGEST) not in sys.path:
    sys.path.insert(0, str(INGEST))

from export_region_bundle import (  # noqa: E402
    bbox_fingerprint,
    clear_pack_cache,
    count_cached_tiles,
    save_checkpoint,
    write_cached_tile,
)


def test_tile_disk_cache(tmp_path, monkeypatch):
    import export_region_bundle as mod

    monkeypatch.setattr(mod, "CACHE_DIR", tmp_path / "cache")
    key = "test-pack"
    write_cached_tile(key, 10, 100, 200, b"\x89PNG" + b"x" * 9000)
    n, nbytes = count_cached_tiles(key)
    assert n == 1
    assert nbytes >= 9000


def test_checkpoint_roundtrip(tmp_path, monkeypatch):
    import export_region_bundle as mod

    monkeypatch.setattr(mod, "CACHE_DIR", tmp_path / "cache")
    bbox = {"south": 42.0, "west": 13.0, "north": 46.0, "east": 19.0}
    fp = bbox_fingerprint(bbox, (9, 10))
    save_checkpoint("croatia", {"fingerprint": fp, "phase": "tiles", "tiles_done": 100})
    cp = json.loads((tmp_path / "cache" / "croatia" / "checkpoint.json").read_text())
    assert cp["tiles_done"] == 100
    clear_pack_cache("croatia")
    assert not (tmp_path / "cache" / "croatia").exists()
