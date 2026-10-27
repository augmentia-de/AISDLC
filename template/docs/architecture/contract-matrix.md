# Contract-Matrix

Klassifikation der Schnittstellen zur Steuerung von Vertrags-Einfrierung und Fast-Track.

| Contract | Type | Klassifikation | Verweis | Bemerkung |
| :-- | :-- | :-- | :-- | :-- |
| `docs/api/openapi.yaml` | REST/OpenAPI | external/public (default) | ADRs/Architecture | Vertragsverbindlich. Änderungen nach Schritt 4 erfordern Rücksprung zu Schritt 2 (außer wenn als `internal` umklassifiziert und begründet). |
| `docs/architecture/schema.sql` | DB-Schema | internal | ADR-019 | Spiegelregel beachten (byte-identisch zu `.agent/knowledge/schema.sql`). |
| Sonstige Schnittstellen | Frei definierbar | internal/external/public | Bitte ergänzen | Klassifikation vor Gate 1 festlegen. |

**Regeln:**
- Fast-Track nur bei `internal` und **keiner** betroffenen Vertragsdatei (`docs/api/**`, `openapi.yaml`, contract-abhängige Dateien).
- Vertrags-Einfrierung nach Schritt 4 nur für `external/public`. Für `internal` kann im MVP-Modus pragmatischer verfahren werden, jedoch niemals ohne nachvollziehbaren Grund.
- Änderungen an `external/public` nach Freeze erzwingen immer einen Rücksprung zu Schritt 2.
