# Traceability — Lückenliste

> Schritt 1 (Anforderungserhebung) · Agent: `aisdlc-requirements` · Stand: 2026-10-21
> Werkzeug: `python3 .agent/tools/traceability_check.py` · Matrix: `docs/requirements/traceability_matrix.json`

**Kein Prozentwert.** Jede Lücke bekommt eine Entscheidung und eine Begründung, warum sie
bleibt. Der Report wird in Schritt 5 (Testbezüge) und Schritt 6 (Codebezüge) fortgeschrieben.

---

## Ergebnis des Werkzeugs

```
$ python3 .agent/tools/traceability_check.py
Keine Luecken — die Kette Anforderung -> Story -> Feature -> Test -> Code ist vollstaendig.
Exit-Code: 0
```

| # | Prüfung | Ergebnis |
|---|---|---|
| 1 | Anforderung in der PRD ohne Story | **grün** — 24 Anforderungen (18 FR + 6 NFR), jede hat mindestens eine Story |
| 2 | Story verweist auf unbekannte Anforderung | **grün** — jeder Verweis zeigt auf eine PRD-Zeile |
| 3 | Story ohne Feature-Datei | **grün** — 23 Stories, 23 Feature-Dateien |
| 4 | Story ohne Anforderungs-Bezug | **grün** — jede Story nennt mindestens eine Anforderungs-ID |
| 5 | Akzeptanzkriterium ohne Matrix-Eintrag | **grün** — 101 Akzeptanzkriterien, alle in der Matrix geführt |
| 6 | Matrix-Eintrag ohne Test | **grün** (formal) — siehe L-01: die Testbezüge sind in Schritt 1 Platzhalter |
| 7 | Matrix-Eintrag ohne Code | **grün** (formal) — siehe L-02: die Codebezüge sind in Schritt 1 Platzhalter |

**Eine gefüllte Matrix ist kein Beleg.** Die Prüfungen 6 und 7 sind in Schritt 1 nur
formal grün: es existiert noch weder ein Test noch ein Quellcode. Die Prüfungen 6 und 7
müssen in Schritt 5 und Schritt 6 gegen das **Dateisystem** laufen, nicht gegen eine
gefüllte Liste.

---

## Verbleibende Lücken und ihre Entscheidungen

### L-01 — Testbezüge sind Platzhalter (`coverage_state: "seeded"`)

**Lücke:** Jeder Matrix-Eintrag verweist auf geplante Testdateien (`tests/...`), die es
noch nicht gibt. Das Werkzeug prüft nur, ob das Feld gefüllt ist — nicht, ob die Datei
existiert.

**Entscheidung:** Die Testpfade werden in **Schritt 5 (Test-Driven Setup)** erzeugt und
dort gegen das Dateisystem geprüft. In Schritt 1 werden sie bewusst **nicht** erfunden,
sondern aus der Story und dem geplanten Testtyp (Unit / Integration / E2E / Vertrag)
abgeleitet.

**Warum sie bleibt:** Der Auftrag von Schritt 1 ist die Anforderung, nicht der Test. Ein
hier erfundener Testpfad wäre eine Behauptung ohne Deckung.

**Abgebaut in:** Schritt 5 (`.agent/artifacts/test-matrix.md`, Testdatei je Story).

### L-02 — Codebezüge sind Platzhalter (`coverage_state: "seeded"`)

**Lücke:** Die Matrix nennt geplante Quelldateien (`src/main/java/com/echobox/...`,
`src/main/frontend/...`). Sie existieren nicht — `src/` ist im Projekt noch nicht angelegt.

**Entscheidung:** Die Codebezüge werden in **Schritt 6 (Implementierung)** gegen die
tatsächlich erzeugten Dateien geprüft. Die Modulnamen sind ein **Vorschlag** aus den
Anforderungen (siehe `docs/requirements/PRD.md`, Abschnitt 12) und werden in Schritt 2
bestätigt.

**Warum sie bleibt:** Die Codebasis existiert nicht (Ergebnis der Codebase-Prüfung in
Read-Only: `src/` ist leer). Der Neuaufbau ist gewollt.

**Abgebaut in:** Schritt 6.

### L-03 — Offene Punkte O-01 bis O-07

**Lücke:** Sieben Fragen sind bewusst nicht entschieden — fünf davon wörtlich aus
Abschnitt 13 der Anforderung (Rate-Limit, Aufbewahrung, Replay-Berechtigung,
Verschlüsselung im Ruhezustand, AVV bei Fremddaten), zwei aus Zahlenlücken in
FR-022/FR-023 und aus Abschnitt 11 (Versuchsanzahl/Backoff, Zeitlimit je Versuch).

**Entscheidung:** Sie sind als offene Punkte mit Owner (Fachbereich / Gate 1 bzw.
Gate 2), Empfehlung und Begründung erfasst — in `docs/requirements/PRD.md` (Abschnitt 8),
`.agent/artifacts/risk-register.md` und in der Traceability-Matrix (`open_points`). Sie
sind **kein** Blocker für Schritt 1: die betroffenen Stories sind mit konfigurierbaren
Parametern umgesetzt, sodass jede Festlegung nur noch einen Wert setzt.

**Warum sie bleiben:** Der Fachbereich hat sie bewusst offen gelassen; eine Festlegung
durch den Requirements-Orchestrator wäre eine erfundene Anforderung.

**Abgebaut in:** Gate 1 (O-01, O-02, O-03, O-04, O-06, O-07) und Gate 2 (O-05).

### L-04 — Keine Anforderung ohne Story

**Keine Lücke.** Alle 24 Anforderungen der Anforderung sind durch Stories abgedeckt:

| Anforderung | Story | Anforderung | Story |
|---|---|---|---|
| FR-001 | US-001, US-019 | FR-022 | US-011 |
| FR-002 | US-002, US-019 | FR-023 | US-012 |
| FR-003 | US-003, US-019 | FR-024 | US-013 |
| FR-004 | US-004 | FR-025 | US-014 |
| FR-010 | US-005 | FR-030 | US-015, US-020 |
| FR-011 | US-006 | FR-031 | US-016, US-020 |
| FR-012 | US-007 | FR-032 | US-017 |
| FR-013 | US-008 | FR-033 | US-018 |
| FR-020 | US-009 | NFR-001 … NFR-006 | US-005, US-005/US-011, US-022, US-007, US-021, US-023 |

---

## Was der nächste Schritt mit der Matrix macht

1. **Schritt 2 (Architektur)** bestätigt die Modulnamen und ergänzt die
   Vertragspunkte (Signaturverfahren und Header-Name, Ablehnungssignal, Kennungen-Header,
   Zustellvokabular, Rollenmatrix). Die Vertragsdatei wird nach Schritt 4 eingefroren.
2. **Schritt 5 (Test-Driven Setup)** ersetzt jeden Testplatzhalter durch eine echte
   Testdatei und prüft die Matrix gegen `tests/`.
3. **Schritt 6 (Implementierung)** ersetzt jeden Codeplatzhalter durch eine echte
   Quelldatei und prüft die Matrix gegen `src/`.
4. **Schritt 8 (Security)** prüft die Kette vor Gate 2 erneut.

**Regel:** Lücken werden nie durch Entfernen von Matrix-Einträgen geschlossen. Eine
weggelassene Zeile ist eine verschwundene Anforderung.
