# ADR-007 — Duplikaterkennung: Vektorähnlichkeit plus normalisierter Textvergleich, organisationsweit konfigurierbar, nie blockierend

| Feld | Wert |
|---|---|
| **ID** | ADR-007 |
| **Titel** | Duplikatverkennung: Vektorähnlichkeit plus normalisierter Textvergleich |
| **Status** | **Angenommen** |
| **Datum** | 2026-10-20 |
| **Entscheider** | Architecture Orchestrator (`aisdlc-architecture`), bestätigt durch F8 |
| **Betrifft** | FR-032, A-08, F8, K-2, NFR-004, NFR-008 |
| **Supersediert** | — |

## Kontext

K2 (KPI) verlangt: mindestens 50 % der Duplikatwarnungen führen zur **Wiederverwendung**
statt zur Neuanlage. Die Kund-Mail fordert eine „Warnung bei ähnlichen Prompts".

Die Umsetzung kollidiert mit dem härtesten Prinzip des Produkts:

* **K-2 / P1 / I-2** — der Duplikatvergleich darf **ausschließlich innerhalb derselben
  Organisation** erfolgen. Es existiert **kein** globaler Prompt-Index. Ein globaler Index
  wäre ein seitenkanalübergreifender Leck- und Profilierungsvektor (FR-023, SEC-002).

Weitere Vorgaben:

* **FR-032** — Ähnlichkeit über ein **konfigurierbares** Maß (Default: Vektorähnlichkeit
  ≥ 0,90; **zusätzlich** normalisierter Textvergleich). Ergebnis listet **bis zu 5** ähnliche
  Prompts mit Titel, Score und Verweis. Die Warnung ist **beratend** und blockiert das
  Anlegen **nie**.
* **F8 / A-08** — Vektorähnlichkeit ≥ 0,90 plus normalisierter Textvergleich,
  organisationsweit konfigurierbar, nie blockierend.
* **AC-017-2** — ein identischer Prompt in einer **anderen** Organisation erzeugt **keine**
  Warnung und **keinen** Hinweis.
* **AC-017-3** — ein nur lose verwandter Prompt erzeugt **keine** Warnung; der Schwellwert
  ist je Organisation konfigurierbar.

## Entscheidung

### 1. Zwei Verfahren, ein Score

| Verfahren | Zweck | Technik |
|---|---|---|
| **Normalisierter Textvergleich** | exakte und near-exakte Dubletten (Kopien mit Leerzeichen-/Großkleinheits-Unterschieden) | `pg_trgm` (Trigramm-Ähnlichkeit) über ein normalisiertes Textfeld |
| **Vektorähnlichkeit** | semantische Dubletten (gleiche Absicht, anderer Wortlaut) | Langchain4j `EmbeddingModel` + KNN über `pgvector` |

* Das **Embedding** wird **je Version** einmalig berechnet und gespeichert (512 Dimensionen,
  `float8`). Es ist ein **abgeleitetes Merkmal** des Inhalts, kein Inhalt — und es verlässt
  das System nicht (anderer Provider-Aufruf als die Klassifikation, siehe unten).
* Der **Score** ist das Maximum beider Verfahren. Findet eines der beiden Verfahren einen
  Treffer über dem Schwellwert, wird gemeldet.

### 2. Schwellwert und Konfiguration

* `organisation.duplicate_threshold` (Default **0,90**), änderbar durch `ORG_ADMIN` über
  `PATCH /api/v1/organisations/current` (FR-032, F8, A-08).
* `organisation.duplicate_detection_enabled` (Default `true`) erlaubt das **Abschalten** der
  Duplikatsuche je Organisation — sinnvoll für Organisationen mit bewusst redundanten
  Prompt-Sammlungen.
* Die Schwelle wirkt **ab dem nächsten Prüflauf**; laufende Prüfungen werden nicht
  abgebrochen.

### 3. Mandantengebunden — technisch erzwungen

* Die Kandidatensuche läuft **ausschließlich** über `WHERE organisation_id = :orgId`
  (ADR-002, drei Ebenen: Repository-Filter + RLS + Suchindex).
* Der Vektorindex ist **partitioniert nach `organisation_id`**; die KNN-Suche enthält den
  Mandantenfilter **im selben Query**. Ein globaler KNN-Index über alle Organisationen mit
  nachträglichem Filtern existiert **nicht**.
* Embeddings werden **je Organisation** erzeugt und gespeichert. Der Embedding-Provider
  ist konfigurierbar; es ist **nicht** zwingend derselbe wie für die Klassifikation. Wird
  ein Provider für Embeddings genutzt, gilt für ihn **dieselbe Provider-Verifikation**
  (ADR-006) — sonst entsteht eine undokumentierte Lücke.
* AC-017-2 ist damit **strukturell** erfüllt: Ein fremder Prompt kann nicht in die
  Kandidatenmenge gelangen, also auch nicht in das Ergebnis.

### 4. Ergebnisform

* `scan_run.duplicate_matches` — Liste von **max. 5** Einträgen: `promptId`, `title`,
  `versionNo`, `score`.
* Das Ergebnis erscheint **im Scan-Ergebnis** (`GET /api/v1/prompt-versions/{id}/scan`),
  nicht in einem eigenen Endpunkt — die Warnung ist Teil des Prüfergebnisses (AC-017-1).
* **Kein** eigener „Duplikat prüfen"-Endpunkt: Das Anlegen blockiert nie, eine
  Vorab-Anfrage wäre eine zusätzliche Operation ohne beobachtbares Verhalten. Bewusst
  weggelassen.
* Die Warnung ist **rein informativ**. Sie erzeugt **keinen** Statuswechsel, **keine**
  zusätzliche Prüfung und **keine** Benachrichtigung. Sie wirkt **nicht** auf `APPROVED`/
  `FLAGGED` ein — der Schweregrad der Fundstellen entscheidet, nicht die Ähnlichkeit.

### 5. Reihenfolge im Prüflauf

Die Duplikatsuche läuft als **Stufe (b)** — **vor** der LLM-Klassifikation (Stufe c) und
**nach** der lokalen Vorprüfung (Stufe a) (FR-031):

```
(a) Vorprüfung  →  (b) Duplikatvergleich  →  (c) LLM-Klassifikation  →  (d) Aggregation
```

Begründung: Die Stufen (a) und (b) sind **lokal** und laufen vor (c); ihre Fundstellen
fließen in das Gesamturteil ein. Zusätzlich ist der Duplikatvergleich die aufwendigste
lokale Stufe (Embedding + KNN) und liefert einen **unabhängigen** Hinweis darauf, dass
Stufe (c) überhaupt erforderlich ist — bei einer exakten Duplikat-Kopie kann der
Embedding-Aufruf der Stufe (b) die Stufe (c) ersetzen? **Nein** — bewusst wird die
Klassifikation **immer** durchgeführt, damit der Status der Version niemals von einem
Duplikat abhängt.

### 6. Grenzen und bewusste Nicht-Ziele

* **Kein** „Prompt kopieren" (FR-017) — das ist `SOLL`/Post-MVP (PRD §6.3) und hat bewusst
  keine Story. Ein solcher Endpunkt würde die Freigabe-Semantik berühren (Kopie = neue
  Organisationseinheit mit `PENDING`) und ist in Phase 2 **nicht** Teil des Vertrags.
* **Kein** „Platzhalter-Syntax"/Template-Engine (O3) — erhöht die Angriffsfläche der
  Erkennung erheblich.
* **Keine** grenzüberschreitende Ähnlichkeitssuche, keine „similar prompts across
  teams"-Funktion (O2, O7).

## Konsequenzen

### Positiv

* Die Mandantentrennung ist **technisch erzwungen**, nicht nur konventionell — die
  Kandidatenmenge kann eine fremde Organisation gar nicht enthalten.
* Die Zwei-Verfahren-Kombination erkennt sowohl Kopien als auch Umformulierungen.
* Die Konfigurierbarkeit je Organisation adressiert **R-02** (Fehlalarme) und **R-05**
  (ignorierter Alarm) direkt.
* Der Schwellwert `0,90` ist bewusst streng: **Falsch-Positive** in der Duplikatsuche
  nerven stärker als eine verpasste Duplikatwarnung, weil der Nutzer sonst den Hinweis
  insgesamt ignoriert.

### Negativ / Kosten

* **Embedding-Kosten und -Latenz:** ein zusätzlicher Provider-Aufruf je Version. Er
  skaliert mit dem Volumen (A-10: 1 000 000 Prompts).
* **Speicher:** `pgvector` mit 512 Dimensionen à `float8` ≈ 4 KB je Version — bei 1 000 000
  Versionen rund 4 GB plus Index. Das ist der **größte** Speicherposten nach den Inhalten
  selbst und muss in der Kapazitätsplanung berücksichtigt werden. Alternative bei
  Wachstum: `float16` oder Dimensionsreduktion.
* **Der Vektorindex ist der teuerste Index der Anwendung.** Partitionierung nach
  `organisation_id` ist zwingend; ein globaler Index wäre ein Mandantenleck in Indexform.
* **Der Schwellwert von 0,90 ist providerabhängig** — ein anderes Embedding-Modell liefert
  andere Ähnlichkeitswerte. Die Schwelle muss je Provider-Deployment kalibriert werden; ein
  Wechsel des Providers macht eine Neukalibrierung nötig. Das ist als Betriebsauftrag zu
  führen.

### Neutral

* Das Ergebnis erscheint im Scan-Ergebnis, nicht in einem eigenen Endpunkt — die UI zeigt
  die Warnung als Block im Detailbereich.

## Alternativen, die verworfen wurden

| Alternative | Warum verworfen |
|---|---|
| Globaler Prompt-Index für die Duplikatsuche | Widerspricht P1/K-2/I-2 — seitenkanalübergreifender Leck- und Profilierungsvektor. |
| Nur Trigramm-/Textvergleich | Erkennt Umformulierungen nicht; K2 verlangt semantische Ähnlichkeit. |
| Nur Vektorähnlichkeit | Findet exakte Kopien mit geringer Abweichung (Zeilenumbruch, Tippfehler) unzuverlässig. |
| Blockierender Duplikat-Check (404/409 bei Duplikat) | FR-032 und F8 sagen ausdrücklich: **nie** blockierend. |
| Eigener Endpunkt `POST /prompts/duplicate-check` | Zusätzliche Operation ohne beobachtbares Verhalten; die Warnung ist Teil des Prüfergebnisses. |
| Bloom-Filter/Hash-Bucket statt Embedding | Beschränkt sich auf exakte Dubletten. |

## Verweise

* `docs/requirements/PRD.md` §8 (FR-032), §13 (A-08), §14 (F8), §15 (K-2), §5 (K2)
* `docs/architecture/diagrams/05-sequence-scan-process.mmd` — Stufe (b)
* `docs/api/openapi.yaml` — `DuplicateMatch`, `scan_run.duplicate_matches`, `OrganisationUpdateRequest`
