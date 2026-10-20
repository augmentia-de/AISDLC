#!/usr/bin/env python3
"""
PromptGuard — Vertragsprüfung für `docs/api/openapi.yaml` (Schritt 2).

Prüft die Pflichtangaben des openapi-first-Skills und die Story->Operation-Zuordnung.
Aufruf:  python3 docs/api/check_contract.py
Exitcode 0 = keine Fehler (Findings mit WARN sind toleriert und werden gelistet).
"""
from __future__ import annotations

import json
import pathlib
import re
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[2]
SPEC = ROOT / "docs" / "api" / "openapi.yaml"
STORIES = ROOT / "docs" / "requirements" / "stories"

METHODS = ("get", "post", "put", "patch", "delete")
MANDATORY = ("operationId", "summary", "description", "responses", "security", "tags")
ERROR_CODES = ("400", "401", "403", "404", "409", "422", "429", "500")

errors: list[str] = []
warnings: list[str] = []


def err(msg: str) -> None:
    errors.append(msg)


def warn(msg: str) -> None:
    warnings.append(msg)


spec = yaml.safe_load(SPEC.read_text(encoding="utf-8"))

# --------------------------------------------------------------------------
# 1. Grundstruktur
# --------------------------------------------------------------------------
assert spec.get("openapi", "").startswith("3.0"), "openapi-Version != 3.0.x"
paths = spec["paths"]
comps = spec.get("components", {})

operations: list[tuple[str, str, dict]] = []
for path, item in paths.items():
    for method, op in item.items():
        if method in METHODS:
            operations.append((path, method, op))

# --------------------------------------------------------------------------
# 2. Pflichtangaben je Operation (openapi-first-Skill)
# --------------------------------------------------------------------------
seen_ids: dict[str, str] = {}
for path, method, op in operations:
    oid = op.get("operationId")
    where = f"{method.upper()} {path}"
    if not oid:
        err(f"{where}: operationId fehlt")
        continue
    if oid in seen_ids:
        err(f"{where}: operationId '{oid}' doppelt (zuerst {seen_ids[oid]})")
    seen_ids[oid] = where
    if not re.fullmatch(r"[a-z][a-zA-Z0-9]*", oid):
        err(f"{where}: operationId '{oid}' ist nicht camelCase")
    for field in MANDATORY:
        if field not in op:
            err(f"{where}: Pflichtangabe '{field}' fehlt")
    if "security" in op and op["security"] != []:
        sec = op["security"]
        if not any("bearerAuth" in s for s in sec):
            err(f"{where}: security ohne bearerAuth: {sec}")
    resp = op.get("responses", {})
    if not any(str(c).startswith("2") for c in resp):
        err(f"{where}: keine Erfolgsantwort (2xx)")
    # Regelbasierte Erwartung: nicht jede Operation kann jeden Fehlercode
    # ausloesen (z. B. 409 bei einer GET-Operation). Geprüft wird, ob die für
    # die Operation *möglichen* Fehlerantworten dokumentiert sind.
    auth = bool(op.get("security"))
    has_body = "requestBody" in op
    has_path_param = "{" in path
    has_query = any(
        (p2.get("in") == "query") for p2 in op.get("parameters", []) if "$ref" not in p2
    ) or any(
        "$ref" in p2 and "Size" in p2["$ref"] for p2 in op.get("parameters", [])
    )
    role = str(op.get("x-required-role", ""))
    named = [r for r in ("DEVELOPER", "SECURITY_ADMIN", "ORG_ADMIN") if r in role]
    role_limited = (bool(named) and len(named) < 3) or "CI-Service-Token" in role

    # 422 nur, wenn der Request-Body Pflichtfelder hat (sonst nicht ableitbar)
    body_required = False
    if has_body:
        schema = (
            op["requestBody"].get("content", {}).get("application/json", {}).get("schema", {})
        )
        ref = schema.get("$ref", "")
        if ref:
            target = comps.get("schemas", {}).get(ref.rsplit("/", 1)[-1], {})
            body_required = bool(target.get("required"))
        else:
            body_required = bool(schema.get("required"))

    expected = {"500"}
    if auth:
        expected |= {"401", "429"}
    if body_required:
        expected |= {"422"}
    if has_query:
        expected |= {"400"}
    if role_limited:
        expected |= {"403"}
    if has_path_param:
        expected |= {"404"}
    for code in sorted(expected):
        if code not in resp:
            err(f"{where}: Fehlerantwort {code} fehlt (regelbasiert erwartet)")
    for code in ERROR_CODES:
        if code not in resp and code not in expected:
            warn(f"{where}: Fehlerantwort {code} nicht dokumentiert (für diese Operation nicht ableitbar)")
    if "requestBody" in op:
        rb = op["requestBody"]
        if not rb.get("content"):
            err(f"{where}: requestBody ohne content")
    for p in op.get("parameters", []):
        if "$ref" in p:
            continue
        if not p.get("name") or not p.get("in"):
            err(f"{where}: Parameter ohne name/in: {p}")
        if "schema" not in p and "content" not in p:
            err(f"{where}: Parameter '{p.get('name')}' ohne schema")
        if p.get("in") == "query" and "schema" in p and not p["schema"].get("type"):
            warn(f"{where}: Query-Parameter '{p['name']}' ohne Typ")

# --------------------------------------------------------------------------
# 3. Mandanten-Scope: jede Operation trägt x-tenant-scope
# --------------------------------------------------------------------------
for path, method, op in operations:
    if "x-tenant-scope" not in op:
        err(f"{method.upper()} {path}: x-tenant-scope fehlt")
    if "x-required-role" not in op:
        err(f"{method.upper()} {path}: x-required-role fehlt")

# --------------------------------------------------------------------------
# 4. Story -> Operation: jede Story hat eine Operation, jede Operation eine Story
# --------------------------------------------------------------------------
story_files = sorted(STORIES.glob("US-*.json"))
all_stories = {json.loads(f.read_text(encoding="utf-8"))["id"] for f in story_files}

op_stories: dict[str, list[str]] = {}
for path, method, op in operations:
    st = op.get("x-stories", [])
    if not st:
        err(f"{method.upper()} {path}: x-stories fehlt — Operation ohne Story")
    for s in st:
        op_stories.setdefault(s, []).append(f"{op.get('operationId')}")

uncovered = sorted(all_stories - set(op_stories))
for s in uncovered:
    err(f"Story {s} ohne Operation")

unknown = sorted(set(op_stories) - all_stories)
for s in unknown:
    err(f"x-stories verweist auf unbekannte Story {s}")

# --------------------------------------------------------------------------
# 5. Pagination / Filter / Sortierung sind Vertragsbestandteil
# --------------------------------------------------------------------------
paged = [n for n in comps.get("schemas", {}) if n.startswith("Paged")]
for name in paged:
    props = comps["schemas"][name].get("properties", {})
    for field in ("page", "size", "totalElements", "totalPages", "hasMore", "nextCursor"):
        if field not in props:
            err(f"Schema {name}: Paginierungsfeld '{field}' fehlt")

# --------------------------------------------------------------------------
# 6. Fehlerformat: alle Fehlerantworten referenzieren dasselbe Problem-Schema
# --------------------------------------------------------------------------
problem = comps["schemas"].get("Problem")
if not problem:
    err("components.schemas.Problem fehlt")
else:
    for f in ("type", "title", "status", "detail", "instance"):
        if f not in problem.get("properties", {}):
            err(f"Problem-Schema: Feld '{f}' fehlt")
    for f in ("type", "title", "status", "detail", "instance"):
        if f not in problem.get("required", []):
            err(f"Problem-Schema: '{f}' ist nicht required")

# --------------------------------------------------------------------------
# 7. Schemata: jeder $ref zeigt auf eine existierende Komponente
# --------------------------------------------------------------------------
def walk(node, path="#"):
    if isinstance(node, dict):
        ref = node.get("$ref")
        if isinstance(ref, str) and ref.startswith("#/components/"):
            kind, name = ref[len("#/components/"):].split("/", 1)
            if kind not in comps or name not in comps[kind]:
                err(f"{path}: $ref zeigt auf fehlende Komponente {ref}")
        for k, v in node.items():
            walk(v, f"{path}/{k}")
    elif isinstance(node, list):
        for i, v in enumerate(node):
            walk(v, f"{path}[{i}]")


walk(spec)

# --------------------------------------------------------------------------
# 8. Komponentennamen müssen der OpenAPI-Namenskonvention entsprechen
# --------------------------------------------------------------------------
for kind, entries in comps.items():
    for name in entries:
        if not re.fullmatch(r"[a-zA-Z0-9.\-_]+", name):
            err(f"components/{kind}: ungültiger Komponentenname '{name}'")

# --------------------------------------------------------------------------
# Bericht
# --------------------------------------------------------------------------
print(f"Operationen im Vertrag : {len(operations)}")
print(f"operationId (eindeutig) : {len(seen_ids)}")
print(f"Stories im Backlog      : {len(all_stories)}")
print(f"Stories mit Operation   : {len(op_stories)}")
print(f"Operationen mit Story   : {sum(1 for _, _, o in operations if o.get('x-stories'))}")
print(f"Paged-Schemata          : {len(paged)}")
print()
if warnings:
    print(f"WARNUNGEN ({len(warnings)}):")
    for w in warnings:
        print("  -", w)
    print()
if errors:
    print(f"FEHLER ({len(errors)}):")
    for e in errors:
        print("  -", e)
    sys.exit(1)
print("API-REVIEW: alle strukturellen Prüfungen bestanden.")
