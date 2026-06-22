"""한국어 POI 정규화 · 관광 유형 라벨 · TTS용 설명."""

from __future__ import annotations

import re

TOURISM_KO: dict[str, str] = {
    "attraction": "관광명소",
    "viewpoint": "전망대",
    "museum": "박물관",
    "information": "안내소",
    "artwork": "예술작품",
    "theme_park": "테마파크",
    "gallery": "미술관",
    "zoo": "동물원",
    "aquarium": "수족관",
    "castle": "성",
    "ruins": "유적",
    "monument": "기념비",
    "archaeological_site": "고고학 유적",
    "fort": "요새",
    "palace": "궁전",
    "church": "교회",
    "chapel": "예배당",
    "poi": "관광지",
    "hotel": "숙박",
    "restaurant": "식당",
    "cafe": "카페",
    "fast_food": "패스트푸드",
    "bar": "바",
    "food_court": "푸드코트",
}

AMENITY_KO = {
    "restaurant": "식당",
    "cafe": "카페",
    "fast_food": "패스트푸드",
    "bar": "바",
    "food_court": "푸드코트",
}

REGION_HINTS = (
    (45.3, 45.7, 12.0, 12.7, "베네치아·라군"),
    (46.0, 46.9, 11.2, 12.6, "돌로미티"),
    (45.2, 45.6, 10.8, 11.2, "베로나"),
    (45.42, 46.88, 13.38, 16.61, "슬로베니아"),
    (42.39, 46.55, 13.49, 19.45, "크로아티아"),
)


def region_label(lat: float, lon: float) -> str:
    for s, n, w, e, lab in REGION_HINTS:
        if s <= lat <= n and w <= lon <= e:
            return lab
    if 37.0 <= lat <= 38.0 and 126.8 <= lon <= 127.3:
        return "서울 송파 방이동"
    return "아드리아·알프스 지역"


def is_garbage_name(name: str) -> bool:
    n = name.strip()
    if len(n) < 2:
        return True
    if re.fullmatch(r"[\d\W_#\.]+", n):
        return True
    if n.lower() in {"01", "02", "test", "info"}:
        return True
    return False


def to_korean_name(name: str, tags: dict) -> str:
    for key in ("name:ko", "name:ko-Latn"):
        if tags.get(key):
            return str(tags[key]).strip()
    return name.strip()


def _first_text(*keys: str, tags: dict) -> str:
    for k in keys:
        v = tags.get(k)
        if v and str(v).strip():
            return str(v).strip()
    return ""


def _addr_ko(tags: dict) -> str:
    full = _first_text("addr:full", tags=tags)
    if full:
        return full
    parts = [
        tags.get("addr:city") or tags.get("addr:district"),
        tags.get("addr:street"),
        tags.get("addr:housenumber"),
    ]
    joined = " ".join(str(p).strip() for p in parts if p)
    return joined.strip()


def expand_description_ko(name_ko: str, tags: dict, lat: float, lon: float) -> str:
    """TTS·카드용 — OSM 태그에서 읽을 내용 최대한 수집."""
    chunks: list[str] = []

    for key in (
        "description:ko",
        "description:ko-Latn",
        "description",
        "description:en",
        "description:it",
        "note",
        "note:ko",
        "wikipedia:ko",
    ):
        raw = _first_text(key, tags=tags)
        if raw and len(raw) > 12:
            chunks.append(raw)
            break

    amenity = tags.get("amenity") or ""
    tourism = tags.get("tourism") or tags.get("historic") or ""
    if amenity in AMENITY_KO:
        label = AMENITY_KO[amenity]
        cuisine = tags.get("cuisine") or tags.get("cuisine:ko")
        if cuisine:
            chunks.append(f"{label}. 요리 종류는 {cuisine.replace(';', ', ')}입니다.")
        elif not chunks:
            chunks.append(f"{name_ko}은(는) {region_label(lat, lon)}의 {label}입니다.")
    elif not chunks:
        type_ko = TOURISM_KO.get(tourism or "poi", "관광지")
        chunks.append(f"{name_ko}은(는) {region_label(lat, lon)}의 {type_ko}입니다.")

    if tags.get("heritage"):
        chunks.append(f"문화유산 등급: {tags['heritage']}")
    if tags.get("start_date"):
        chunks.append(f"연혁: {tags['start_date']}년경")
    if tags.get("architect"):
        chunks.append(f"건축가: {tags['architect']}")
    if tags.get("artist_name"):
        chunks.append(f"작가: {tags['artist_name']}")
    if tags.get("opening_hours"):
        chunks.append(f"운영 시간: {tags['opening_hours']}")
    if tags.get("fee"):
        chunks.append(f"요금: {tags['fee']}")
    if tags.get("phone"):
        chunks.append(f"전화: {tags['phone']}")
    addr = _addr_ko(tags)
    if addr:
        chunks.append(f"주소: {addr}")
    if tags.get("wheelchair") == "yes":
        chunks.append("휠체어 접근 가능")

    text = " ".join(chunks)
    text = re.sub(r"\s+", " ", text).strip()
    return text[:2000]


def parse_osm_rating(tags: dict, kind: str) -> float | None:
    """OSM 태그 → 1.0~5.0 별점. 없으면 None (앱이 안정 fallback)."""
    for key in ("stars", "tourism:stars"):
        raw = tags.get(key)
        if raw is not None:
            try:
                n = int(float(str(raw).strip()))
                if 1 <= n <= 5:
                    return float(n)
            except ValueError:
                pass
    michelin = tags.get("michelin_stars") or tags.get("stars:michelin")
    if michelin is not None:
        try:
            n = int(float(str(michelin)))
            if 1 <= n <= 3:
                return 3.5 + n * 0.5
        except ValueError:
            pass
    heritage = str(tags.get("heritage") or "").lower()
    if "unesco" in heritage:
        return 5.0
    if kind == "attraction" and (tags.get("wikipedia") or tags.get("wikidata")):
        return 4.5
    return None
