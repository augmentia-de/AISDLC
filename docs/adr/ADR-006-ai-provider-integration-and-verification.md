# ADR-006 — KI-Provider-Anbindung und Provider-Verifikation (Entscheidung F1, unredigierte Übermittlung)

| Feld | Wert |
|---|---|
| **ID** | ADR-006 |
| **Titel** | KI-Provider-Anbindung und Provider-Verifikation |
| **Status** | **Angenommen** |
| **Datum** | 2026-10-20 |
| **Entscheider** | Architecture Orchestrator (`aisdlc-architecture`), bestätigt durch F1 (Product Owner) |
| **Betrifft** | FR-031, FR-033, FR-034, FR-035, FR-038, FR-065, FR-074, DP-002, DP-005, SEC-006, SEC-007, A-09, A-12, F1, K-5, R-06 |
| **Supersediert** | — |

> ## ⚠️ [SECURITY] Diese Entscheidung enthält ein bewusst akzeptiertes Restrisiko
>
> **Der Originalinhalt eines Prompts wird unredigiert an einen externen KI-Dienst
> übermittelt.** Erkannte Secrets und personenbezogene Daten verlassen das System und
> werden vom Provider verarbeitet. Die Plattform verteilt damit genau die Inhalte, die sie
> selbst als Risiko markiert.
>
> Das ist **kein Versehen**, sondern die Entscheidung **F1** des Product Owners vom
> 2026-10-20, die bewusst von der Empfehlung („redigiert") abweicht. Das Restrisiko ist als
> **R-06 (akzeptiert, hoch/hoch)** dokumentiert.
>
> Die Gegenmaßnahmen in diesem ADR sind **verbindliche `MUSS`-Anforderungen**, keine
> Empfehlungen. Solange eine davon nicht erfüllt ist, **darf kein Inhalt das System
> verlassen** — der Prüflauf bricht ab.

## Kontext

Der Prüflauf braucht ein LLM, um Prompt-Injection und Jailbreak zu erkennen. Damit
verlassen Daten das System — und die Kunden-Mail nennt **Langchain4j** (CON-004), was
**zwingend** einen externen Provider voraussetzt (ein lokales Modell ist im MVP nicht
geplant).

Die Anforderungen sind in dieser Frage außergewöhnlich detailliert, weil der Product Owner
die Abweichung bewusst gewählt hat:

* **FR-038** — der zu prüfende **Originalinhalt** wird **unverändert** übermittelt; es
  findet **keine Redaktion** statt. Übermittelt wird ausschließlich an den gemäß FR-065
  **verifizierten** Dienst; schlägt die Verifikation fehl, **bricht der Scan ab, ohne dass
  Inhalt das System verlässt**.
* **FR-065 / DP-005** — alle Daten bleiben in der EU (`europe-west3`); der Provider wird
  mit „keine Datenverwendung zum Training" und **ohne dauerhafte Speicherung** konfiguriert;
  dies ist **in der Konfiguration nachweisbar**.
* **FR-074** — jede Übermittlung wird als **Metadaten-Ereignis** protokolliert (Zeitpunkt,
  Provider, Modell, Zeichenlänge, Ergebnis-ID, Korrelation-ID) — **ohne Inhalt** (Art. 30
  DSGVO).
* **DP-002** — Datenminimierung wird stattdessen umgesetzt über (a) Löschung der
  KI-Rohpayloads nach 24 h, (b) Provider-Auflagen, (c) Metadaten-only-Audit, (d) Verbot
  dauerhafter Speicherung von Roh-Inhalten im Plattform-Log.
* **SEC-006** — Prompt-Inhalt ist nicht vertrauenswürdig: nie als HTML/Markdown rendern,
  nie interpretieren, nie in Logs schreiben, nie ungeschützt in Systemprompts einsetzen,
  URLs darin nie abrufen (SSRF).
* **SEC-007** — der Prüfer wird gegen Prompt-Injection **seiner Prüflinge** gehärtet.
* **AC-022-2** — konfiguriert der Provider keine retention-freie Verarbeitung in der EU,
  wird der Aufruf **nicht ausgeführt** und die Version geht auf `FAILED`; **kein Inhalt
  verlässt das System**.

## Entscheidung

### 1. Zwei Ports, ein Provider-Adapter

| Port | Zweck | Modell |
|---|---|---|
| `ClassificationPort` | Prompt-Injection / Jailbreak | Langchain4j `ChatModel` |
| `EmbeddingPort` | Duplikatvergleich | Langchain4j `EmbeddingModel` |

Beide Ports liefern **Datenobjekte, kein Modell**. Der konkrete Provider (Gemini 2.0 Flash
oder OpenAI, EU) ist eine **Konfigurationseigenschaft** (ENV), keine Codeverzweigung. Im
Test werden die Ports durch Stubs/Fakes ersetzt (K-8).

### 2. Provider-Verifikation als **Gate** (nicht als Anzeige)

Vor **jedem** externen Aufruf prüft ein `ProviderVerificationService`:

| Prüfung | ID | Quelle | Wirkung bei Verstoß |
|---|---|---|---|
| Auftragsverarbeitungsvertrag hinterlegt | `avv-present` | Konfiguration (`AI_PROVIDER_AVV_REFERENCE`) | Abbruch |
| EU-Region `europe-west3` | `eu-region` | Konfiguration + Laufzeitprüfung des Endpunkts | Abbruch |
| Trainingsverzicht auf Eingangsdaten | `no-training` | Konfiguration (`AI_PROVIDER_TRAINING_OPT_OUT=true`) | Abbruch |
| Keine dauerhafte Speicherung | `no-durable-storage` | Konfiguration (`AI_PROVIDER_RETENTION=zero`) | Abbruch |

* **Ein Verstoß ⇒ der externe Aufruf findet nicht statt.** Die Version wechselt auf `FAILED`
  mit dem technischen Fehlercode `PROVIDER_POLICY_VIOLATION`; es wird **kein** Inhalt
  übermittelt (AC-022-2, FR-038).
* Die Verifikation ist **fail-closed** für den Aufruf, aber **fail-safe** für das System:
  ein Provider-Ausfall führt zu `FAILED` (blockiert), nicht zu einem Absturz (K-6).
* Das Ergebnis ist über `GET /api/v1/provider/verification` einsehbar
  (`SECURITY_ADMIN`/`ORG_ADMIN`) und über `POST …/provider/verification` erneut auslösbar.
  Die Oberfläche zeigt den Status — **aber** der Gate-Charakter ist der eigentliche
  Zweck: Ein nicht verifizierter Provider macht die Plattform **blind**, nicht unsicher.
* Die Verifikation wird **regelmäßig** (mind. stündlich) erneut durchgeführt, damit ein
  still geänderter Provider-Setup bemerkt wird. Der Cache ist **bestätigend**, nicht
  maßgeblich: Der Aufruf prüft die zuletzt bestätigte Konfiguration.

### 3. Unveränderte Übermittlung (F1) — wie genau

* Der **Originalinhalt** der Version wird übermittelt. Keine Redaktion, keine
  Ersetzung, keine Maskierung, kein Entfernen von Entitäten.
* Die **Maskierung** findet ausschließlich in der **Anzeige** statt: `scan_finding.
  redacted_excerpt` zeigt nur ein maskiertes Fragment (FR-037). Die Übermittlung bleibt
  unverändert.
* **Ein vorangehender lokaler Schritt (a) bleibt Pflicht:** Die lokale deterministische
  Vorprüfung (Secrets/PII) läuft **vor** dem externen Aufruf und fließt in das Gesamturteil
  ein (FR-031, F1-Mindestmaßnahme 3). Damit existiert eine **lokale** Erkennungs- und
  Meldespur, auch wenn der Provider ausfällt.
* **Was wird übermittelt:** genau der zu prüfende Inhalt der Version. **Nicht** übermittelt
  werden: Benutzerkennungen, E-Mail-Adressen von Nutzern, Mandantennamen, andere Prompts,
  Metadaten der Organisation, Audit-Einträge, Tokens, Secrets der Plattform.
  Der Provider erhält **keinen** Mandantenkontext und **keinen** Prompt-Titel — nur den
  zu klassifizierenden Text. Das ist eine bewusste Datenminimierung **innerhalb** der vom
  Product Owner akzeptierten Grenze.

### 4. Härtung des Prüfers gegen seine Prüflinge (SEC-007, R-04)

* Der Prüfling wird **ausschließlich als Datenblock** übergeben, nie als Anweisung. Die
  Systemanweisung des Prüfers bleibt dominant und wird **nicht** aus dem Prüflinginhalt
  zusammengesetzt.
* Der Prüfer hat **keine Werkzeuge**: keine Funktion-Calling-Fähigkeiten, keinen Netzzugang,
  keinen Dateizugriff. Ein injiziertes „ignoriere previous instructions“ kann deshalb
  keine Wirkung entfalten (AC-018-3).
* Eingaben werden **strukturiert** übergeben (eigener Serialisierungsabschnitt, JSON-String
  als Datenfeld), damit Delimiter-Ausbruch strukturell erschwert wird.
* Die **Antwort** des Prüfers wird **nicht** interpretiert, sondern **geparst** und gegen
  ein Schema validiert. Eine nicht parsebare Antwort ⇒ Stufe (c) schlägt fehl ⇒ `FAILED`,
  **kein** `APPROVED` aus einer nicht verstandenen Antwort.
* Es wird **ein** Aufruf pro Version mit festem Systemprompt durchgeführt; kein
  mehrstufiges Agenten-Setup (das vergrößert die Angriffsfläche).

### 5. Metadaten-Audit jeder Übermittlung (FR-074)

Pro externem Aufruf wird genau ein `provider_transfer`-Datensatz geschrieben:

| Feld | Inhalt |
|---|---|
| `transferred_at` | Zeitpunkt |
| `provider` | Anbieter-Kennung (z. B. `gemini-eu`) |
| `model` | Modell-Kennung (z. B. `gemini-2.0-flash`) |
| `content_length` | übermittelte Zeichenzahl — **kein Inhalt** |
| `result_id` | ID des erzeugten Scan-Ergebnisses (erlaubt die Zuordnung der Fundstellen) |
| `correlation_id` | Korrelations-ID (verbindet Übermittlung, Fundstellen, Logs und Audit) |

Der Datensatz trägt **keinen** Inhalt, **keinen** Ausschnitt, **keinen** Hash des Inhalts
(der wäre ein ableitbarer Fingerabdruck) — nur die Länge. Er ist der Nachweis nach
**Art. 30 DSGVO**.

### 6. Rohpayloads: kurzlebig (FR-061, DP-002)

* Rohe Anfrage-/Antwort-Payloads werden **höchstens 24 Stunden** gespeichert und dann von
  einem eigenen Job gelöscht (ADR-004).
* Dauerhaft gespeichert wird nur das **verdichtete Ergebnis**: Kategorien, Schweregrade,
  maskierte Ausschnitte, Positionen, Begründung.
* Zweck der 24-h-Speicherung: Nachvollziehbarkeit eines Fehlverhaltens des Prüfers. Der
  Zugriff ist **nur** für `SECURITY_ADMIN`/`ORG_ADMIN` und **nur** über eine dedizierte
  Betriebs-Operation — diese ist bewusst **nicht** Teil des fachlichen Vertrags in
  `docs/api/openapi.yaml` (kein fachlicher Zweck, sondern Betrieb). Sie wird in
  `.agent/artifacts/api-review.md` als Befund B-10 geführt.

### 7. Was ausdrücklich **nicht** gebaut wird

* **Keine Redaktionsstufe** (F1) — die Empfehlung wurde überstimmt.
* **Kein lokales Modell** im MVP — CON-004 verlangt Langchain4j; ein eigenes Modell wäre
  eine eigene Betriebs- und Compliance-Baustelle.
* **Keine Prompt-Injection in den Systemprompt des Providers** — der Prüfling wird
  ausschließlich als Datenblock übergeben (SEC-007).
* **Kein URL-Abruf** aus dem Prüflinginhalt (SSRF) — der Prüfer hat keinen Netzzugang
  (SEC-006).
* **Kein Prompt-Caching/Logging beim Provider über unsere Konfiguration hinaus** — die
  Auflagen (kein Training, keine dauerhafte Speicherung) sind Bestandteil des AVV und
  werden vor dem ersten Aufruf verifiziert.

## Konsequenzen

### Positiv

* Die Erkennungsqualität für **semantische** Risiken (indirekte Injektion, semantische PII)
  ist deutlich höher als mit reinen Regex-/Regelwerken.
* Die Entscheidung ist **verifizierbar**: `provider_transfer` belegt jede Übermittlung
  metadatenbasiert; die Verifikation ist einsehbar und wiederholbar.
* Die Trennung „lokale Vorprüfung zuerst" liefert eine Erkennungsspur **ohne** Provider.
* Der Prüfer ist durch die Werkzeuglosigkeit strukturell gehärtet.

### Negativ / Kosten

* **[SECURITY] Aktives Restrisiko R-06 (hoch/hoch, akzeptiert).** Secrets und
  personenbezogene Daten verlassen das System. Die Plattform ist in diesem Punkt nicht
  schützend, sondern meldend.
* **Provider-Kosten und -Latenz** sind Bestandteil der Prüfdauer; p95 < 30 s (NFR-002) ist
  nur mit Reserve erreichbar.
* **Verfügbarkeitskopplung:** Ist der Provider nicht erreichbar, entsteht kein Urteil
  (`FAILED`). Deshalb ist der degradierte Betrieb Pflicht (FR-070), nicht optional.
* **Zusätzliche Compliance-Pflicht:** AVV, EU-Region, Löschgarantie und Trainingsverbot
  müssen **vertraglich** vorliegen, bevor der erste Aufruf erfolgt. Ohne AVV ist die
  Plattform im MVP **nicht prüffähig** — das ist eine organisatorische Abhängigkeit, keine
  technische.
* **Kein Fehler-Buffer:** Jeder unerklärte Prüfer-Output führt zu `FAILED`, nicht zu
  `APPROVED`. Das ist konservativ und kann zu einer erhöhten `FAILED`-Quote führen, wenn
  das Modellformat schwankt — Gegenmaßnahme: strenges Antwortschema mit Retry.

### Neutral

* Die Mandantentrennung ist auch hier bindend: Die Übermittlung enthält **keinen**
  Mandantenbezug, und der Provider wird **nie** mandantenübergreifend mit einem
  gemeinsamen Cache/State betrieben.

## Alternativen, die verworfen wurden

| Alternative | Warum verworfen |
|---|---|
| **Redaktion vor dem Aufruf** (die Empfehlung) | **Vom Product Owner bewusst überstimmt (F1).** Nicht erneut zur Entscheidung gestellt; das Restrisiko ist dokumentiert und in der Datenschutz-Folgenabschätzung zu führen. |
| Lokales Modell im MVP | Betriebs-, Hardware- und Compliance-Aufwand; CON-004 verlangt Langchain4j. |
| Ein Aufruf pro Erkennungskategorie (4 Aufrufe) | 4× Kosten und Latenz; die Kategorien sind in einem Aufruf unterscheidbar. |
| Agenten-Setup mit Werkzeugen (Function Calling) | Vergrößert die Angriffsfläche des Prüfers erheblich (R-04) und ist für die Aufgabe nicht nötig. |
| Prüfling an den Systemprompt anhängen | Klassische Injection; SEC-007 verlangt die strikte Trennung. |
| Provider-Auflagen nur in der Dokumentation prüfen | Ohne technische Verifikation ist die Zusage nicht belegbar (FR-065 „in der Konfiguration nachweisbar", AC-022-2). |

## Verweise

* `docs/requirements/PRD.md` §8 (FR-038, FR-065, FR-074), §10 (SEC-006, SEC-007), §11 (DP-002, DP-005), §14 (F1, verbindliche Konsequenzen), §16 (R-06)
* `.agent/artifacts/risk-register.md` — R-04, R-06
* `docs/architecture/diagrams/04-component-scan-worker.mmd`, `05-sequence-scan-process.mmd`
* `docs/api/openapi.yaml` — `getProviderVerification`, `verifyProviderConfiguration`, `ProviderTransfer`
* `docs/adr/ADR-004-storage-retention-and-privacy.md` — Löschfristen der Rohpayloads
