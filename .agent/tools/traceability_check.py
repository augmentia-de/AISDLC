#!/usr/bin/env python3
"""Konsistenzcheck Anforderung -> Story -> Feature -> Test -> Code.

Prueft die Traceability-Kette des Projekts und meldet jede Luecke als Liste
(kein Prozentwert). Aufruf:

    python3 .agent/tools/traceability_check.py [--root <pfad>] [--json]

Exit-Codes:
    0  keine Luecke
    1  Luecken gefunden (Ausgabe auf stdout)
    2  Eingangsdaten fehlen oder sind unlesbar

Geprueft wird:
    1  Anforderung in der PRD, aber ohne Story
    2  Story verweist auf eine Anforderung, die nicht in der PRD steht
    3  Story ohne Feature-Datei
    4  Story ohne Anforderungs-Bezug
    5  Akzeptanzkriterium ohne Matrix-Eintrag
    6  Matrix-Eintrag ohne Test
    7  Matrix-Eintrag ohne Code-Referenz

Der Check ist stack-unabhaengig: er liest JSON, Markdown und Gherkin, nichts
spricht er ueber die Implementierungssprache.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

REQ_ID = re.compile(r"\b((?:FR|NFR|SEC|DP|CON|US|AC)-\d+)\b")
PRD_ROW = re.compile(r"^\|\s*((?:FR|NFR|SEC|DP|CON)-\d+)\s*\|", re.M)
FEATURE_STORY = re.compile(r"\b(US-\d+)\b")


def read_json(path: pathlib.Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise SystemExit(f"FEHLT: {path}")
    except json.JSONDecodeError as exc:
        raise SystemExit(f"UNGUELTIGES JSON: {path} — {exc}")


def collect_prd_ids(prd: pathlib.Path) -> set[str]:
    """Anforderungs-IDs aus den Tabellenzeilen der PRD."""
    if not prd.exists():
        raise SystemExit(f"FEHLT: {prd}")
    return set(PRD_ROW.findall(prd.read_text(encoding="utf-8")))


def collect_stories(stories_dir: pathlib.Path) -> dict[str, dict]:
    if not stories_dir.is_dir():
        raise SystemExit(f"FEHLT: {stories_dir}")
    out = {}
    for f in sorted(stories_dir.glob("*.json")):
        d = read_json(f)
        sid = d.get("id") or f.stem
        d["_file"] = str(f)
        out[sid] = d
    return out


def collect_features(features_dir: pathlib.Path) -> dict[str, list[str]]:
    """Feature-Datei -> enthaltene Story-IDs."""
    if not features_dir.is_dir():
        return {}
    out: dict[str, list[str]] = {}
    for f in sorted(features_dir.glob("*.feature")):
        text = f.read_text(encoding="utf-8")
        out[str(f)] = sorted(set(FEATURE_STORY.findall(text)))
    return out


def check(root: pathlib.Path) -> list[dict]:
    req_dir = root / "docs" / "requirements"
    prd_ids = collect_prd_ids(req_dir / "PRD.md")
    stories = collect_stories(req_dir / "stories")
    features = collect_features(req_dir / "features")
    matrix = read_json(req_dir / "traceability_matrix.json")

    gaps: list[dict] = []

    # Story -> Feature
    story_with_feature: set[str] = set()
    for path, sids in features.items():
        for sid in sids:
            story_with_feature.add(sid)
            if sid not in stories:
                gaps.append({"art": "feature", "id": sid, "grund": f"Feature {path} verweist auf unbekannte Story"})

    matrix_entries = matrix.get("entries", [])
    matrix_by_story: dict[str, list[dict]] = {}
    for e in matrix_entries:
        for sid in e.get("story_ids", []) or []:
            matrix_by_story.setdefault(sid, []).append(e)

    arch_only = {
        r.get("requirement_id") if isinstance(r, dict) else r
        for r in (matrix.get("requirements_without_story") or [])
    }
    arch_ids = {
        r.get("requirement_id") if isinstance(r, dict) else r
        for r in (matrix.get("architecture_constraints") or [])
    }

    # 1 Anforderung ohne Story — bewusst ohne Story gefuehrte Anforderungen
    # (requirements_without_story, z. B. post-MVP) sind keine Luecke.
    covered = set()
    for s in stories.values():
        covered.update(s.get("requirement_ids") or [])
    for rid in sorted(prd_ids - covered - arch_ids - arch_only):
        gaps.append({"art": "anforderung", "id": rid, "grund": "in PRD, aber ohne Story"})

    # 2 Story verweist auf unbekannte Anforderung
    for sid, s in sorted(stories.items()):
        for rid in s.get("requirement_ids") or []:
            if rid not in prd_ids and rid not in arch_ids:
                gaps.append({"art": "story", "id": sid, "grund": f"verweist auf {rid}, das nicht in der PRD steht"})

    # 3 Story ohne Feature
    for sid in sorted(stories):
        if sid not in story_with_feature:
            gaps.append({"art": "story", "id": sid, "grund": "keine Feature-Datei"})

    # 4 Story ohne Anforderungs-Bezug
    for sid, s in sorted(stories.items()):
        if not (s.get("requirement_ids") or []):
            gaps.append({"art": "story", "id": sid, "grund": "ohne Anforderungs-Bezug"})

    # 5 Akzeptanzkriterium ohne Matrix-Eintrag
    in_matrix: set[str] = set()
    for e in matrix_entries:
        in_matrix.update(e.get("acceptance_criteria") or [])
    for sid, s in sorted(stories.items()):
        for ac in s.get("acceptance_criteria") or []:
            ac_id = ac.get("id") if isinstance(ac, dict) else ac
            if ac_id and ac_id not in in_matrix:
                gaps.append({"art": "kriterium", "id": ac_id, "grund": f"kein Matrix-Eintrag fuer {sid}"})

    # 6/7 Matrix-Eintrag ohne Test / ohne Code
    for e in matrix_entries:
        rid = e.get("requirement_id", "?")
        if not e.get("tests"):
            gaps.append({"art": "matrix", "id": rid, "grund": "kein Test"})
        if not e.get("code"):
            gaps.append({"art": "matrix", "id": rid, "grund": "kein Code"})

    return gaps


def main() -> int:
    ap = argparse.ArgumentParser(description="Traceability-Konsistenzcheck")
    ap.add_argument("--root", default=str(pathlib.Path(__file__).resolve().parents[2]))
    ap.add_argument("--json", action="store_true", help="Ausgabe als JSON")
    args = ap.parse_args()

    try:
        gaps = check(pathlib.Path(args.root).resolve())
    except SystemExit as exc:
        print(str(exc), file=sys.stderr)
        return 2

    if args.json:
        print(json.dumps({"luecken": gaps, "anzahl": len(gaps)}, ensure_ascii=False, indent=2))
    else:
        if not gaps:
            print("Keine Luecken — die Kette Anforderung -> Story -> Feature -> Test -> Code ist vollstaendig.")
        else:
            by_art: dict[str, list[dict]] = {}
            for g in gaps:
                by_art.setdefault(g["art"], []).append(g)
            print(f"{len(gaps)} Luecke(n):\n")
            for art, items in by_art.items():
                print(f"  {art.upper()} ({len(items)}):")
                for g in items:
                    print(f"    {g['id']:<12} {g['grund']}")
                print()
    return 1 if gaps else 0


if __name__ == "__main__":
    sys.exit(main())
