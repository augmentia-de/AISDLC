# Zielstruktur `src/` — Kurzfassung

> Schritt 3 (Simplify & Reduktion, nach dem Rücksprung) · Agent: `aisdlc-architecture` · Stand: 2026-10-21
> **Wahrheit:** `docs/architecture/folder-structure.md` — dort stehen die
> **Dateien** und ihre Zuständigkeiten je Modul, die **Konventionen** und der
> **Planungsauftrag**. Diese Datei ist die Kurzfassung: sie nennt nur die
> **Module**, sonst würde sie eine zweite Dateiliste pflegen.
> **Noch kein Code** — die Pfade sind die Zielstruktur für Schritt 6.

```
src/
├── main/
│   ├── java/com/echobox/
│   │   ├── endpoint/      Endpunkte verwalten
│   │   ├── event/         Ereignisse einliefern
│   │   ├── delivery/      Zustellung und Zustellgarantie (Hintergrundprozess)
│   │   ├── crypto/        Krypto (Signatur, Verschlüsselung, Secret-Store)
│   │   ├── protocol/      Zustellprotokoll, Totenliste, Kennzahlen, Aufbewahrung
│   │   ├── tenant/        Mandantenkontext — inkl. CrossTenantScope (ADR-002 §7)
│   │   ├── config/        Konfiguration (Spring beans)
│   │   └── common/        Fehlerbild, Cursor, Zeit, IDs
│   ├── resources/         application.yml, application-staging.yml, db/migration/
│   └── frontend/src/      Oberfläche: app/, features/, components/, lib/, types/
└── test/                  → Tests liegen unter tests/, nicht unter src/test
```

**Konventionen** (Schichtentrennung, Ports, SQL, Mandant, Fehler, Klartext,
Konfiguration, Tests) stehen in `docs/architecture/folder-structure.md` §4 —
**einmal**, nicht hier wiederholt.

## Planungsauftrag an OpenCode (Schritt 6)

> Lies `docs/api/openapi.yaml` und erstelle einen Schritt-für-Schritt-Plan für
> die Implementierung der neuen API-Stubs, DTOs und Typen.

Die Abbildung der Auftragspfade (`src/types` → DTO-Spiegel des Vertrags,
`src/controllers` → je `operationId` ein Controller) und die **Reihenfolge**
des Plans stehen in `docs/architecture/folder-structure.md` §5.

**Reihenfolge (Kurzfassung):** gemeinsame Schemas → `endpoint` → `crypto` →
`event` → `delivery` → `protocol` → `common` → `tenant` → Oberfläche.

**Regeln:** kein Feld und keine Operation erfinden, die nicht im Vertrag stehen;
je Operation ein Vertragstest (Statuscode, Pflichtfelder, Fehlerformat); der
Vertrag wird in Schritt 6 **nicht** geändert.
