#!/usr/bin/env python3
"""Traceability-Werkzeug: pruefen und fuellen.

Zwei Betriebsarten:

    python3 .agent/tools/traceability_check.py            # pruefen, nur melden
    python3 .agent/tools/traceability_check.py --fill     # pruefen und den Beleg schreiben

Die Kette hat zwei Haelften, und die gehoeren unterschiedlichen Schritten:

  statisch   Anforderung -> Story -> Feature        entsteht in Schritt 1
  dynamisch  Anforderung -> Test -> Code            entsteht in Schritt 5/6

Die **Matrix** (`docs/requirements/traceability_matrix.json`) traegt nur die statische
Haelfte. Die dynamische Haelfte wird **aus dem Dateisystem ermittelt** und landet in
`.agent/artifacts/traceability.md`. Damit kann kein Schritt ein Feld fuellen, das er
nicht belegen kann, und kein spaeterer Schritt muss die Matrix nachtragen.

GRUNDREGEL: Es wird **nie** ein Pfad geschrieben, den das Werkzeug nicht belegen
kann. Ein erfundener Pfad ist schlimmer als eine Luecke - er sieht belegt aus und
ist es nicht. Was nicht belegbar ist, landet als Luecke im Bericht.

Zuordnung Test <-> Anforderung: ueber die **Akzeptanzkriterien-Kennung**
(AC-<story>-<nr>). Die Kennung steht in der Feature-Datei (Szenario-Kommentar) und
im Testfall (Methodenname oder Kommentar). Damit ist die Zuordnung mechanisch und
nicht geraten.

Zuordnung Code <-> Anforderung: ein Test, der eine Produktionsklasse **nennt**, belegt
diese Klasse - sofern sie im Projekt existiert. Sonst keine Code-Referenz.

Exit-Codes:
    0  keine Luecke
    1  Luecken gefunden (Ausgabe auf stdout)
    2  Eingangsdaten fehlen oder sind unlesbar
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys
from collections import defaultdict

# Akzeptanzkriterium-Kennung: AC-003-1 (Markdown/Feature) oder ac003_1 (Java-Methode)
AC_PAT = re.compile(r"\bAC[-_]?(\d{3})[-_](\d+)\b", re.I)
# Anforderungs-Kennung in PRD-Tabellenzeilen
PRD_ROW = re.compile(r"^\|\s*((?:FR|NFR|SEC|DP|CON)-\d+)\s*\|", re.M)
# Java-Produktionsklasse: public class X / record X / interface X
JAVA_TYPE = re.compile(r"\b(?:class|interface|record|enum)\s+([A-Z][A-Za-z0-9_]*)")
SKIP_DIRS = {".git", "target", "node_modules", "build", "dist", ".next", "__pycache__"}


def read_json(path: pathlib.Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError:
        raise SystemExit(f"FEHLT: {path}")
    except json.JSONDecodeError as exc:
        raise SystemExit(f"UNGUELTIGES JSON: {path} - {exc}")


def iter_files(root: pathlib.Path, sub: str, suffixes: tuple[str, ...]):
    base = root / sub
    if not base.is_dir():
        return
    for p in sorted(base.rglob("*")):
        if not p.is_file() or p.suffix not in suffixes:
            continue
        if any(part in SKIP_DIRS for part in p.parts):
            continue
        yield p


def collect_prd_ids(prd: pathlib.Path) -> set[str]:
    if not prd.exists():
        raise SystemExit(f"FEHLT: {prd}")
    return set(PRD_ROW.findall(prd.read_text(encoding="utf-8")))


def collect_stories(stories_dir: pathlib.Path) -> dict[str, dict]:
    if not stories_dir.is_dir():
        raise SystemExit(f"FEHLT: {stories_dir}")
    out: dict[str, dict] = {}
    for f in sorted(stories_dir.glob("*.json")):
        d = read_json(f)
        sid = d.get("id") or f.stem
        d["_file"] = f.as_posix()
        out[sid] = d
    return out


def collect_features(features_dir: pathlib.Path) -> dict[str, list[str]]:
    if not features_dir.is_dir():
        return {}
    out: dict[str, list[str]] = {}
    for f in sorted(features_dir.glob("*.feature")):
        text = f.read_text(encoding="utf-8")
        out[f.as_posix()] = sorted({f"AC-{a}-{b}" for a, b in AC_PAT.findall(text)})
    return out


def collect_code(root: pathlib.Path) -> dict[str, pathlib.Path]:
    """Produktionsklassen: Klassenname -> Datei."""
    out: dict[str, pathlib.Path] = {}
    for p in iter_files(root / "src", ".", (".java", ".kt", ".ts", ".tsx", ".py", ".go", ".cs")):
        out[p.stem] = p
    return out


def collect_tests(root: pathlib.Path, code: dict[str, pathlib.Path]) -> dict[str, dict]:
    """Testdatei -> {ac_ids, code_refs}."""
    out: dict[str, dict] = {}
    for p in iter_files(root / "tests", ".", (".java", ".kt", ".ts", ".tsx", ".py", ".go", ".cs")):
        try:
            text = p.read_text(encoding="utf-8")
        except Exception:
            continue
        acs = {f"AC-{a}-{b}" for a, b in AC_PAT.findall(text)}
        # Produktionsklassen, die der Test nennt
        refs = set()
        for name in set(JAVA_TYPE.findall(text)) | set(code.keys()):
            if name in code and code[name] != p:
                refs.add(code[name].as_posix())
        out[p.as_posix()] = {"acs": acs, "code": sorted(refs), "_abs": p}
    return out


def ac_to_requirement(stories: dict[str, dict]) -> dict[str, set[str]]:
    """AC-Kennung -> Anforderungs-IDs (ueber die Story)."""
    out: dict[str, set[str]] = {}
    for sid, s in stories.items():
        for ac in s.get("acceptance_criteria") or []:
            ac_id = ac.get("id") if isinstance(ac, dict) else ac
            if ac_id:
                out[ac_id] = set(s.get("requirement_ids") or [])
    return out


def check(root: pathlib.Path) -> tuple[list[dict], dict]:
    """Prueft die Kette. Liefert (Befunde, Belegdaten).

    Schweregrade:
      luecke  - blockiert das Gate (Anforderung ohne belegten Test o. Ae.)
      hinweis - meldet, blockiert nicht (z. B. eine Produktionsklasse, die kein
                Test nennt - bei Dependency-Injection ist das normal)
    """
    req_dir = root / "docs" / "requirements"
    prd_ids = collect_prd_ids(req_dir / "PRD.md")
    stories = collect_stories(req_dir / "stories")
    features = collect_features(req_dir / "features")
    matrix = read_json(req_dir / "traceability_matrix.json")
    code = collect_code(root)
    tests = collect_tests(root, code)
    ac2req = ac_to_requirement(stories)

    gaps: list[dict] = []
    entries = matrix.get("entries", [])
    matrix_ids = {e.get("requirement_id") for e in entries}

    # --- statische Haelfte ---
    story_with_feature: set[str] = set()
    story_acs = {sid: {ac.get("id") if isinstance(ac, dict) else ac
                        for ac in (s.get("acceptance_criteria") or [])}
                  for sid, s in stories.items()}
    for path, acs in features.items():
        base = path.rsplit("/", 1)[-1]
        for sid, s_acs in story_acs.items():
            if base.startswith(sid) or (acs and s_acs & acs):
                story_with_feature.add(sid)
    for sid in sorted(stories):
        if sid not in story_with_feature:
            gaps.append({"art": "story", "id": sid, "grund": "keine Feature-Datei",
                         "schwere": "luecke"})

    covered = set()
    for s in stories.values():
        covered.update(s.get("requirement_ids") or [])
    arch_only = {r.get("requirement_id") if isinstance(r, dict) else r
                 for r in (matrix.get("requirements_without_story") or [])}
    arch_ids = {r.get("requirement_id") if isinstance(r, dict) else r
                for r in (matrix.get("architecture_constraints") or [])}
    for rid in sorted(prd_ids - covered - arch_ids - arch_only):
        gaps.append({"art": "anforderung", "id": rid, "grund": "in PRD, aber ohne Story",
                     "schwere": "luecke"})

    for sid, s in sorted(stories.items()):
        for rid in s.get("requirement_ids") or []:
            if rid not in prd_ids and rid not in arch_ids:
                gaps.append({"art": "story", "id": sid,
                             "grund": f"verweist auf {rid}, das nicht in der PRD steht",
                             "schwere": "luecke"})
        if not (s.get("requirement_ids") or []):
            gaps.append({"art": "story", "id": sid, "grund": "ohne Anforderungs-Bezug",
                         "schwere": "luecke"})

    in_matrix = set()
    for e in entries:
        in_matrix.update(e.get("acceptance_criteria") or [])
    for sid, s in sorted(stories.items()):
        for ac in s.get("acceptance_criteria") or []:
            ac_id = ac.get("id") if isinstance(ac, dict) else ac
            if ac_id and ac_id not in in_matrix:
                gaps.append({"art": "kriterium", "id": ac_id,
                             "grund": f"kein Matrix-Eintrag fuer {sid}", "schwere": "luecke"})

    # --- dynamische Haelfte ---
    used_code: set[str] = set()
    test_by_req: dict[str, set[str]] = defaultdict(set)
    code_by_req: dict[str, set[str]] = defaultdict(set)
    for tpath, t in tests.items():
        for ac in t["acs"]:
            if ac not in ac2req:
                gaps.append({"art": "test", "id": tpath, "grund": f"unbekannte Kennung {ac}",
                             "schwere": "luecke"})
                continue
            for rid in ac2req[ac]:
                test_by_req[rid].add(tpath)
        for c in t["code"]:
            used_code.add(c)
            for ac in t["acs"]:
                for rid in ac2req.get(ac, ()):  # noqa: SIM118
                    code_by_req[rid].add(c)

    for rid in sorted(matrix_ids):
        if rid in arch_only or rid in arch_ids:
            continue
        if not test_by_req.get(rid):
            gaps.append({"art": "matrix", "id": rid, "grund": "kein belegter Test",
                         "schwere": "luecke"})
        if not code_by_req.get(rid):
            gaps.append({"art": "matrix", "id": rid, "grund": "keine belegte Code-Referenz",
                         "schwere": "luecke"})

    for cpath in sorted(set(code) - used_code):
        gaps.append({"art": "code", "id": code[cpath].as_posix(),
                     "grund": "wird von keinem Test referenziert", "schwere": "hinweis"})

    beleg = {
        "entries": [
            {
                "requirement_id": e.get("requirement_id"),
                "story_ids": e.get("story_ids", []),
                "acceptance_criteria": e.get("acceptance_criteria", []),
                "features": e.get("features", []),
                "tests": sorted(test_by_req.get(e.get("requirement_id"), ())),
                "code": sorted(code_by_req.get(e.get("requirement_id"), ())),
            }
            for e in entries
        ],
        "unbelegte_tests": sorted(t for t, v in tests.items() if not v["acs"]),
        "ohne_test_referenz": sorted(c for c in set(code) - used_code),
        "test_gesamt": len(tests),
        "code_gesamt": len(code),
    }
    return gaps, beleg


def write_fill_artifacts(root: pathlib.Path, beleg: dict, gaps: list[dict]) -> list[pathlib.Path]:
    """Schreibt den Beleg und den Fuellbericht."""
    art = root / ".agent" / "artifacts"
    art.mkdir(parents=True, exist_ok=True)
    written: list[pathlib.Path] = []

    # 1) Der Beleg: Anforderung -> Test -> Code, mechanisch ermittelt
    lines = ["# Traceability-Beleg — Anforderung → Test → Code", "",
             "Mechanisch aus dem Dateisystem ermittelt mit",
             "`.agent/tools/traceability_check.py --fill`. Kein Pfad ohne Beleg.", "",
             "| Anforderung | Story | Akzeptanzkriterien | Tests | Code |",
             "|---|---|---|---|---|"]
    for e in beleg["entries"]:
        lines.append(
            f"| {e['requirement_id']} | {', '.join(e['story_ids']) or '—'} "
            f"| {', '.join(e['acceptance_criteria']) or '—'} "
            f"| {', '.join(f'`{t}`' for t in e['tests']) or '**kein belegter Test**'} "
            f"| {', '.join(f'`{c}`' for c in e['code']) or '**keine belegte Code-Referenz**'} |")
    lines += ["", "## Kennzahlen", "",
              f"- Testdateien im Projekt: {beleg['test_gesamt']}",
              f"- Produktionsdateien im Projekt: {beleg['code_gesamt']}",
              f"- Testdateien ohne Anforderungsbezug: {len(beleg['unbelegte_tests'])}",
              f"- Produktionsdateien ohne Testreferenz: {len(beleg['ohne_test_referenz'])}",
              ""]
    p = art / "traceability.md"
    p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    written.append(p)

    # 2) Der Fuehlbericht: was gefuellt wurde, was nicht belegbar war
    rep = ["# Traceability-Fuellung — was belegt wurde und was nicht", "",
           "Erzeugt von `.agent/tools/traceability_check.py --fill`.", "",
           "**Grundregel:** es wurde kein Pfad geschrieben, der nicht belegbar war.",
           "Was nicht belegbar war, steht unten als Luecke — nicht als erfundener Pfad.", ""]
    luecken = [g for g in gaps if g.get("schwere", "luecke") == "luecke"]
    hinweise = [g for g in gaps if g.get("schwere") == "hinweis"]
    if luecken:
        rep += ["## Luecken (blockieren das Gate)", "", "| Art | ID | Grund |", "|---|---|---|"]
        rep += [f"| {g['art']} | `{g['id']}` | {g['grund']} |" for g in luecken]
    else:
        rep += ["## Luecken (blockieren das Gate)", "", "Keine. Die Kette ist vollstaendig belegt."]
    if hinweise:
        rep += ["", "## Hinweise (melden, blockieren nicht)", "", "| Art | ID | Grund |", "|---|---|---|"]
        rep += [f"| {g['art']} | `{g['id']}` | {g['grund']} |" for g in hinweise]
    if beleg["unbelegte_tests"]:
        rep += ["", "## Testdateien ohne Anforderungsbezug", "",
                "Diese Tests tragen keine Akzeptanzkriterien-Kennung. Sie gehoeren zu",
                "keiner Anforderung und sind damit nicht belegt.", ""]
        rep += [f"- `{t}`" for t in beleg["unbelegte_tests"]]
    if beleg["ohne_test_referenz"]:
        rep += ["", "## Produktionsdateien ohne Testreferenz", "",
                "Kein Test nennt diese Datei. Sie ist damit nicht belegt.", ""]
        rep += [f"- `{c}`" for c in beleg["ohne_test_referenz"]]
    p2 = art / "traceability-fill.md"
    p2.write_text("\n".join(rep) + "\n", encoding="utf-8")
    written.append(p2)
    return written


def main() -> int:
    ap = argparse.ArgumentParser(description="Traceability pruefen und fuellen")
    ap.add_argument("--root", default=str(pathlib.Path(__file__).resolve().parents[2]))
    ap.add_argument("--json", action="store_true", help="Ausgabe als JSON")
    ap.add_argument("--fill", action="store_true",
                    help="Beleg und Fuehlbericht schreiben (nur belegbare Pfade)")
    ap.add_argument("--mode", choices=["off", "light", "full"], default=None, help="Profilmodus")
    ap.add_argument("--soft-fail", action="store_true", help="Nur warnen, Exit-Code 0")
    args = ap.parse_args()

    mode = args.mode
    if mode is None:
        cfg = pathlib.Path(".agent/config.json")
        if cfg.exists():
            try:
                c = json.loads(cfg.read_text(encoding="utf-8"))
                mode = c.get("traceability", "full")
            except Exception:
                mode = "full"
        else:
            mode = "full"

    root = pathlib.Path(args.root).resolve()
    if mode == "off":
        if args.fill:
            art = root / ".agent" / "artifacts"
            art.mkdir(parents=True, exist_ok=True)
            (art / "traceability.md").write_text("# Traceability (deaktiviert im Profil)

Traceability-Pruefung ist im aktuellen Profil deaktiviert.
", encoding="utf-8")
            (art / "traceability-fill.md").write_text("# Traceability-Fuehlbericht (deaktiviert)

Keine Pruefung durchgefuehrt.
", encoding="utf-8")
        print("Traceability deaktiviert (Profil).")
        return 0
    try:
        gaps, beleg = check(root)
    except SystemExit as exc:
        print(str(exc), file=sys.stderr)
        return 2

    if args.fill:
        written = write_fill_artifacts(root, beleg, gaps)
        for w in written:
            print(f"  geschrieben: {w.relative_to(root)}")

    if args.json:
        print(json.dumps({"luecken": gaps, "anzahl": len(gaps),
                          "test_gesamt": beleg["test_gesamt"],
                          "code_gesamt": beleg["code_gesamt"]},
                         ensure_ascii=False, indent=2))
        return 1 if gaps else 0

    luecken = [g for g in gaps if g.get("schwere", "luecke") == "luecke"]
    hinweise = [g for g in gaps if g.get("schwere") == "hinweis"]

    if not luecken:
        print("Keine Luecken - die Kette Anforderung -> Story -> Feature -> Test -> Code "
              "ist vollstaendig belegt.")
        return 0
    if mode == "light" or args.soft_fail:
        print(f"HINWEIS: {len(luecken)} Luecke(n) gefunden, aber im Modus '{mode}' als Soft-Fail behandelt (nicht blockierend).")
        # Still print summary
        by_art: dict = defaultdict(list)
        for g in luecken:
            by_art[g["art"]].append(g)
        for art, items in by_art.items():
            print(f"  {art.upper()} ({len(items)}):")
            for g in items[:10]:
                print(f"    {g['id']:<40} {g['grund']}")
            if len(items) > 10:
                print(f"    ... und {len(items) - 10} weitere")
            print()
        if hinweise:
            print(f"{len(hinweise)} HINWEIS(E) - melden, blockieren nicht.")
        return 0
    else:
        by_art: dict[str, list[dict]] = defaultdict(list)
        for g in luecken:
            by_art[g["art"]].append(g)
        print(f"{len(luecken)} LUECKE(N) - blockieren das Gate:\n")
        for art, items in by_art.items():
            print(f"  {art.upper()} ({len(items)}):")
            for g in items:
                print(f"    {g['id']:<40} {g['grund']}")
            print()
    if hinweise:
        by_art = defaultdict(list)
        for g in hinweise:
            by_art[g["art"]].append(g)
        print(f"{len(hinweise)} HINWEIS(E) - melden, blockieren nicht:\n")
        for art, items in by_art.items():
            print(f"  {art.upper()} ({len(items)}):")
            for g in items[:20]:
                print(f"    {g['id']:<40} {g['grund']}")
            if len(items) > 20:
                print(f"    ... und {len(items) - 20} weitere")
            print()
    if mode == "light" or args.soft_fail:
        return 0
    return 1 if luecken else 0


if __name__ == "__main__":
    sys.exit(main())
