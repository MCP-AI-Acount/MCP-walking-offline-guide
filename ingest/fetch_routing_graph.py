#!/usr/bin/env python3
"""OSM 도로 → 국가별 오프라인 라우팅 그래프 (Italy / Croatia / Slovenia)."""

from __future__ import annotations

import json
import math
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

from region_utils import load_bbox_file, load_catalog  # noqa: E402

OVERPASS = "https://overpass-api.de/api/interpreter"
MAX_NODES_PER_REGION = 30_000
MAX_EDGES_PER_REGION = 80_000
HIGHWAY = (
    "footway|path|pedestrian|steps|track|living_street|"
    "service|residential|unclassified|tertiary|secondary|primary"
)


def haversine_m(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    r = 6_371_000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = math.sin(dlat / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlon / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def fetch_ways(bbox: dict, *, retries: int = 4) -> list[dict]:
    s, w, n, e = bbox["south"], bbox["west"], bbox["north"], bbox["east"]
    q = f"""
[out:json][timeout:300];
way["highway"~"{HIGHWAY}"]({s},{w},{n},{e});
out geom;
"""
    req = urllib.request.Request(
        OVERPASS,
        data=urllib.parse.urlencode({"data": q}).encode("utf-8"),
        method="POST",
        headers={
            "User-Agent": "MCP-italy-offline-guide/routing/1.1",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    last_err: Exception | None = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(req, timeout=360) as resp:
                data = json.loads(resp.read().decode("utf-8"))
            return [el for el in data.get("elements") or [] if el.get("type") == "way"]
        except Exception as e:
            last_err = e
            wait = 30 * (attempt + 1)
            print(json.dumps({"retry": attempt + 1, "region": bbox.get("id"), "wait_s": wait, "error": str(e)}, ensure_ascii=False), flush=True)
            time.sleep(wait)
    raise last_err or RuntimeError("fetch_ways failed")


def build_graph(ways: list[dict]) -> dict:
    node_ll: dict[int, tuple[float, float]] = {}
    edges: set[tuple[int, int]] = set()

    for way in ways:
        geom = way.get("geometry") or []
        if len(geom) < 2:
            continue
        prev_id: int | None = None
        for pt in geom:
            nid = int(pt.get("lat") * 1e5) * 10_000_000 + int(pt.get("lon") * 1e5)
            node_ll[nid] = (float(pt["lat"]), float(pt["lon"]))
            if prev_id is not None and prev_id != nid:
                a, b = min(prev_id, nid), max(prev_id, nid)
                edges.add((a, b))
            prev_id = nid
            if len(node_ll) >= MAX_NODES_PER_REGION:
                break
        if len(node_ll) >= MAX_NODES_PER_REGION:
            break

    id_map: dict[int, int] = {}
    nodes: list[list[float]] = []
    for osm_id, (lat, lon) in list(node_ll.items())[:MAX_NODES_PER_REGION]:
        id_map[osm_id] = len(nodes)
        nodes.append([round(lat, 6), round(lon, 6)])

    out_edges: list[list[int]] = []
    for a, b in edges:
        if a not in id_map or b not in id_map:
            continue
        ia, ib = id_map[a], id_map[b]
        lat1, lon1 = nodes[ia]
        lat2, lon2 = nodes[ib]
        w = int(haversine_m(lat1, lon1, lat2, lon2))
        if w < 1:
            continue
        out_edges.append([ia, ib, w])
        if len(out_edges) >= MAX_EDGES_PER_REGION:
            break

    return {"nodes": nodes, "edges": out_edges, "version": 1}


def main() -> int:
    catalog = load_catalog()
    assets = catalog.get("routing_assets") or {}
    targets = [(catalog["primary"]["file"], assets.get("italy") or "routing_graph_italy.json")]
    for spec in catalog.get("extra") or []:
        bbox = load_bbox_file(spec["file"])
        asset = assets.get(bbox["id"]) or f"routing_graph_{bbox['id']}.json"
        targets.append((spec["file"], asset))
    # legacy alias
    legacy = ASSETS / "routing_graph.json"

    for bbox_file, out_name in targets:
        bbox = load_bbox_file(bbox_file)
        print(json.dumps({"fetch": "routing", "region": bbox["id"]}, ensure_ascii=False), flush=True)
        ways = fetch_ways(bbox)
        graph = build_graph(ways)
        out = ASSETS / out_name
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps(graph, separators=(",", ":")), encoding="utf-8")
        if out_name == "routing_graph_italy.json":
            legacy = ASSETS / "routing_graph.json"
            legacy.write_text(out.read_text(encoding="utf-8"), encoding="utf-8")
        print(
            json.dumps(
                {
                    "ok": True,
                    "region": bbox["id"],
                    "nodes": len(graph["nodes"]),
                    "edges": len(graph["edges"]),
                    "bytes": out.stat().st_size,
                    "out": str(out),
                },
                ensure_ascii=False,
            ),
            flush=True,
        )
        time.sleep(15)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
