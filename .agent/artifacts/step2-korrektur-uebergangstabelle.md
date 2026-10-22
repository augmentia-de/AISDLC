# Schritt 2 (Korrektur) — die Übergangstabelle des Domänenmodells (A-6 / B-17)

> Agent: `aisdlc-architecture` · Stand: 2026-10-22 · Quelle der Befunde:
> [`docs/qa/qa-report.md`](../../docs/qa/qa-report.md) (A-6, B-17) und
> [`docs/execution/step8-nacharbeit-security.md`](../../docs/execution/step8-nacharbeit-security.md) §4.2.
>
> Der **eingefrorene Vertrag** `docs/api/openapi.yaml` ist **unverändert**
> (`git diff --stat -- docs/api/` ist leer). Das **Datenbankschema** ist
> **unverändert** (beide Spiegel byte-identisch). `src/` und `tests/` sind
> **unberührt**.

---

## 1. Ergebnis in einem Satz

**Die Übergangstabelle in `docs/architecture/domain_model.md` §3.3 ist auf den
Stand des Codes (`DeliveryStateMachine.TRANSITIONS`) gebracht** — die Tabelle
ist damit die eine Quelle der Wahrheit; Code, Schema und Vertrag stimmen
überein.

---

## 2. Die Korrektur

Der Code wurde im Rücksprung (`d135cda`) an die Tabelle angeglichen; die
**Dokumentation** war nachzuziehen. Die dokumentierte Tabelle lautet jetzt:

```
pending   -> scheduled, cancelled, no_subscriber, delayed
scheduled -> sent, rejected, failed, cancelled, delayed
failed    -> scheduled, delayed, abandoned, cancelled
delayed   -> scheduled, abandoned, cancelled
```

- **Neu dokumentiert:** `pending -> delayed` und `scheduled -> delayed`.
- **Ergänzt:** `failed -> cancelled` und `delayed -> cancelled` — die Pause
  des Endpunkts bricht **jeden offenen** Zustand ab (`onEndpointPaused`).
- **`delayed -> delayed` ist kein Übergang** — der Zustand ändert sich nicht.
- **Begründung:** das Endpunkt-Limit (FR-033) wirkt auch auf den **ersten**
  Versuch eines frischen Laufs — sonst käme ein Endpunkt in der ersten Minute
  über das Limit hinaus. Vertrag und ADR-010 nennen den Zustand `delayed`
  ohne Einschränkung des Ausgangszustands.

## 3. Die geänderten Stellen

| Datei | Änderung |
|---|---|
| `docs/architecture/domain_model.md` §3.3 | Tabelle auf den Code-Stand; Begründung und der Hinweis zu `delayed -> delayed`; die Tabelle ist als **eine Quelle der Wahrheit** ausgewiesen |
| `docs/architecture/domain_model.md` §3.2 | Der Pfad über das Endpunkt-Limit (`delayed`) ist im Bild ergänzt — er fehlte dort vollständig |
| `docs/architecture/domain_model.md` §3.4 (I-8) | Die Pause bricht **alle offenen** Zustände ab (`pending`, `scheduled`, `failed`, `delayed`) — bisher waren nur zwei genannt |
| `docs/adr/ADR-010-rate-limit.md` §2 | **Klarstellung** (keine neue Festlegung): `delayed` ist aus jedem offenen Zustand erreichbar, auch beim ersten Versuch eines frischen Laufs |
| `docs/adr/ADR-006-zustellprozess-hintergrundprozess.md` §5 | **Klarstellung**: die Übergangstabelle ist die eine Quelle der Wahrheit; der Ratenbegrenzungs-Pfad leitet seinen Übergang daraus ab |

**Unverändert:** `docs/api/openapi.yaml` (eingefroren seit `8627bb6`),
`.agent/knowledge/schema.sql` und `docs/architecture/schema.sql` (beide
byte-identisch, Diff leer), `src/`, `tests/`, `docs/requirements/`,
`docs/Anforderung.md`.

## 4. Der Konsistenz-Abgleich

| Quelle | Befund |
|---|---|
| **Code** — `DeliveryStateMachine.TRANSITIONS` | **identisch** mit der dokumentierten Tabelle (alle 16 Übergänge, 4 offene Zustände, 5 endgültige ohne Ausgang) |
| **Code** — `onRateLimited` | leitet den Übergang aus der Tabelle ab (`canTransition(status, DELAYED)`) — kein Nebenschreiben mehr |
| **Code** — `DeliveryStatus` | 9 Werte; **identisch** mit dem Schema-CHECK und mit `components.schemas.DeliveryStatus` des Vertrags |
| **Code** — endgültige Zustände | `sent`, `abandoned`, `rejected`, `cancelled`, `no_subscriber` — **identisch** mit `delivery_runs_terminal_no_next_attempt_ck` und ADR-006 §7 |
| **Schema** — `delivery_runs_status_ck` | dieselben 9 Werte; das Schema kennt **keine** Übergänge, nur Zustände — **kein Widerspruch** |
| **Vertrag** — `components.schemas.DeliveryStatus` | dieselben 9 Werte; `delayed` ohne Einschränkung des Ausgangszustands — **kein Widerspruch** |

**Kein Widerspruch gefunden** — kein Befund, keine Codeänderung.

## 5. Der eine Commit

```
docs(sdlc-2): Übergangstabelle des Domänenmodells angeglichen

Schritt 2 - Konzept & Architektur (Korrektur A-6/B-17) | Agent: aisdlc-architecture | Artefakte: docs/architecture/, docs/adr/, .agent/artifacts/
```

---

*Kanonisches Artefakt: `.agent/artifacts/step2-korrektur-uebergangstabelle.md`.*
