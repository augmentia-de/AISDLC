# Grill-Me Runde 1 — PromptGuard (SDLC-Schritt 1a)

> Stand 2026-10-20 · Agent `aisdlc-requirements` · Skill `aisdlc-grill-stress`
> **Zustand: beantwortet am 2026-10-20 — alle 10 Fragen entschieden.**
> 9 Entscheidungen folgen der Empfehlung, **F1 weicht bewusst ab** (unredigierte Übermittlung).
> Dieses Dokument enthält die ursprüngliche Frage, die Empfehlung **und** die getroffene
> Entscheidung — die Empfehlung bleibt als Begründung und Nachvollziehbarkeit sichtbar.

## Was ist bereits geklärt (Frontier-Eingang)

Die folgenden Fakten habe ich selbst ermittelt (nicht erfragt) — siehe `docs/requirements/PRD.md`:

- **Greenfield.** `src/` ist leer, es gibt keine Build-Dateien, keine Tests, kein `infra/`.
  Betroffene Module entstehen komplett neu.
- **Stack ist faktisch entschieden.** Der Kunde nennt **Langchain4j** (Java-Bibliothek) ⇒ Backend auf
  JVM ⇒ **Spring Boot (Java 21)**. Der Rahmen „FastAPI **oder** Spring Boot" ist damit aufgelöst
  (CON-001). Frontend: **React 19**, TypeScript strict (CON-002).
- **Rahmenbedingungen** aus `AGENTS.md` / `.agent/knowledge/stack.md`: PostgreSQL 16, Custom JWT mit
  `organisation_id`-Scoping, Docker → Cloud Run `europe-west3`, LLM konfigurierbar (Gemini 2.0 Flash
  / OpenAI), DSGVO mit 90-Tage-Aufbewahrung und Recht auf Löschung, kein Geheimnis im Code.
- **Der Stack hat keine offene Frage mehr** — deshalb ist er nicht Teil dieser Frontier.

Die **Frontier** dieser Runde sind die 9 Entscheidungen, deren Voraussetzungen jetzt vollständig
geklärt sind: Sie stehen nicht in Abhängigkeit zueinander und werden **alle gleichzeitig** gestellt.
Jede Frage ist so formuliert, dass „ja" meine Empfehlung annimmt.

---

❓ **F1** — **Datenübermittlung an den externen KI-Dienst:** Dürfen Prompt-Inhalte an ein externes
KI-Modell (z. B. Gemini/OpenAI) übermittelt werden? Der Kunde verlangt sowohl KI-Prüfung als auch
DSGVO-Einhaltung — beide gleichzeitig sind nur mit einer klaren Antwort möglich.

➡️ **Empfehlung: Ja, aber ausschließlich redigiert.** Zwei Stufen: (1) lokale deterministische
Vorprüfung erkennt Secrets/PII und **redigiert** diese Spans, (2) erst der redigierte Text geht an
den externen Dienst. Anbieter EU-basiert, Zero-Retention, kein Training, Vertrag mit
Auftragsverarbeitung. Ohne externe Übermittlung bliebe nur die lokale Regex-/Heuristik-Erkennung —
die findet keine semantischen Injection-Muster, also wäre F18 (Injection-Erkennung) nur schwach
erfüllbar. Der Nachteil der Empfehlung: semantische PII-Erkennung (echte Kundennamen im Fließtext)
bleibt unvollkommen — das ist mit dem Kunden zu kommunizieren, nicht mit Technik zu heilen.

✅ **ENTSCHIEDEN (2026-10-20, Product Owner): „Ja, unredigiert."** Der Originalinhalt wird
**unverändert** an den KI-Dienst übermittelt; es findet keine Redaktion statt.

> **[SECURITY] Auswirkung der Abweichung.** Die Plattform übermittelt damit Inhalte, die sie
> selbst als Risiko erkannt hat: ein im Prompt enthaltenes API-Key oder ein Kundenname verlässt
> das System und wird an einen Dritten verarbeitet. Die Empfehlung wurde bewusst überstimmt.
> **Verbindliche Gegenmaßnahmen (in Phase 2 umzusetzen und in Phase 8 nachzuweisen):**
> (1) Vertrag zur Auftragsverarbeitung (AVV) mit dem KI-Anbieter inkl. Trainingsverbot und
> Löschgarantie; (2) EU-Region, in der Konfiguration verankert; (3) **Provider-Verifikation vor
> dem ersten Aufruf** — schlägt sie fehl, bricht der Scan ab und **kein Inhalt verlässt das
> System** (FR-038, US-022); (4) Metadaten-Audit jeder Übermittlung ohne Inhalt (FR-074);
> (5) die lokale deterministische Vorprüfung bleibt Pflicht und fließt in das Gesamturteil ein
> (FR-031). Das verbleibende Restrisiko ist als **R-06** dokumentiert und in der
> Datenschutz-Folgenabschätzung zu führen. Betroffene Artefakte wurden entsprechend umgeschrieben:
> FR-038, DP-002, DP-005, SEC-006, FR-065, FR-074 (neu), US-019, US-022 (neu), US-034, Glossar.

---

❓ **F2** — **Mandantenmodell:** Sind „Abteilungen" eigenständige Mandanten, oder Abteilungen
innerhalb einer gemeinsamen Organisation? Die Kunden-Mail schreibt „Abteilungen/Organisationen" und
verwendet beide Begriffe synonym.

➡️ **Empfehlung: Eine Organisation pro Abteilung, ein Nutzer gehört genau einer Organisation.**
Das passt exakt zur JWT-Vorgabe `organisation_id` aus dem Rahmen und macht die Isolation einfach
beweisbar (ein Filterfeld, eine RLS-Policy). Das Modell „eine Organisation, mehrere Abteilungen
darin" würde eine zweite Trenndimension (Abteilung als Unterfilter) brauchen — mehr Komplexität in
jedem Query, mehr Fehlerquellen, weniger Nachweiswert. Wenn später Abteilungs-Workspaces
innerhalb einer Organisation gebraucht werden, ist das eine Erweiterung der Rollen, kein Umbau.

✅ **ENTSCHIEDEN (2026-10-20): Eine Organisation pro Abteilung, ein Nutzer genau einer
Organisation** — wie empfohlen.

---

❓ **F3** — **Geltungsbereich des Security-Admins:** Ist der Security-Admin **global** (über alle
Organisationen hinweg sichtbar) oder **organisationsintern**? Der Kunde schreibt „der Security-Admin"
ohne Geltungsbereich.

➡️ **Empfehlung: organisationsintern.** Ein globaler Admin, der alle Prompts aller Abteilungen sehen
kann, widerspricht direkt der Forderung „Entwickler von Abteilung A dürfen unter keinen Umständen die
Prompts von Abteilung B sehen". Beides gleichzeitig ist nur auflösbar, indem der globale Admin
ausschließlich *Metadaten ohne Inhalt* sieht — dann ist es aber ein Plattform-Reviewer, kein
Security-Admin der Fachabteilung. Empfehlung: pro Organisation eine Security-Admin-Rolle; zusätzlich
ein Plattform-Betrieb ohne Fachdatenzugriff. **Falls ein globaler Security-Bereich für den Kunden
tatsächlich existiert, ist das ein eigenes Vorhaben und muss vor Phase 2 beauftragt werden.**

✅ **ENTSCHIEDEN (2026-10-20): Security-Admin ist organisationsintern** — wie empfohlen.

---

❓ **F4** — **Nutzbarkeit ungeprüfter Prompts:** Darf ein Prompt verwendet (kopiert, exportiert,
in einen Bot übernommen) werden, solange die Prüfung nicht abgeschlossen ist oder fehlgeschlagen ist?

➡️ **Empfehlung: Nein — nur `APPROVED` oder `WAIVED` gelten als nutzbar.** Sonst wäre der
Sicherheitswert der Plattform null: Man müsste asynchron warten, und im Zweifel würde der Nutzer
trotzdem kopieren. Als Ausnahme kann ein `SECURITY_ADMIN` eine ungeprüfte Version mit Begründung
freigeben — das ist der dokumentierte Ausweg für Ausfälle, ohne die Regel aufzuweichen.

---

❓ **F5** — **Geltungsbereich der 90-Tage-Löschung:** Gilt die Löschpflicht auch für die
Prompt-Inhalte selbst, oder nur für Logs und Telemetrie?

➡️ **Empfehlung: nur für Logs, Rohtelemetrie und KI-Rohpayloads.** Prompt-Inhalte bleiben bis zur
expliziten Löschung durch Eigentümer/Org-Admin. Bei einer 90-Tage-Löschung der Inhalte wäre die
Plattform nach 90 Tagen leer und der Zweck („zentrales Register mit Historie") hinfällig. Die
Kunden-Mail nennt „sensible Logdaten" — das spricht genau für diese Lesart. Ergänzend: Rohe
KI-Anfrage-/Antwort-Payloads werden bereits nach 24 h gelöscht, das verdichtete Ergebnis
(Kategorie, Schweregrad, redigierter Ausschnitt) bleibt am Datensatz.

✅ **ENTSCHIEDEN (2026-10-20): Die 90-Tage-Löschung gilt nur für Logs, Rohtelemetrie und
KI-Rohpayloads** (Rohtelemetrie: 24 h); Prompt-Inhalte bleiben bis zur expliziten Löschung —
wie empfohlen.

---

❓ **F6** — **Pflegerechte für Kategorien:** Legen alle Nutzer Kategorien selbst an, oder nur der
`ORG_ADMIN`?

➡️ **Empfehlung: nur `ORG_ADMIN` pflegt, alle Nutzer *wählen* aus.** Sonst entstehen Kategorien wie
„Support neu", „support2", „Support (alt)" und die Suche nach Kategorie wird wertlos. Seed-Liste
beim Anlegen der Organisation. Freitext-Tags bleiben davon unberührt — die sind bewusst niedrigschwellig
und werden nur normalisiert, nicht verwaltet.

✅ **ENTSCHIEDEN (2026-10-20): Nur `ORG_ADMIN` pflegt die Kategorien**, alle Nutzer wählen aus der
Seed-Liste — wie empfohlen.

---

❓ **F7** — **SSO/OIDC und MFA im MVP:** Werden enterprise-SSO (OIDC/SAML) und Mehrfaktor-Authentifizierung
für den MVP benötigt?

➡️ **Empfehlung: für den MVP nein, MFA für `SECURITY_ADMIN`/`ORG_ADMIN` als schnelle Nachrüstung
einplanen.** Es ist ein internes Entwickler-Tool; die Kunden-Mail fordert „sicher einloggen (JWT)",
nicht Enterprise-Features. **Aber**: Der Einbaupunkt muss in Phase 2 (ADR) festgeschrieben und im
Datenmodell vorbereitet sein, damit MFA später ohne Datenmodell-Umbau nachgerüstet werden kann.
Falls der Kunde SSO bereits heute braucht (z. B. weil die Anmeldung über den IdP des Konzerns
laufen muss), ist das eine harte Voraussetzung — dann muss F7 vor Phase 2 beantwortet werden, weil es
das Auth-Modell bestimmt.

✅ **ENTSCHIEDEN (2026-10-20): Kein SSO und keine MFA im MVP**; der Einbaupunkt für MFA
(`SECURITY_ADMIN`/`ORG_ADMIN`) wird in Phase 2 per ADR und Datenmodell vorbereitet — wie empfohlen.

---

❓ **F8** — **Duplikaterkennung:** Wie streng soll der Ähnlichkeitsvergleich sein, und blockiert er?

➡️ **Empfehlung: Vektor-Ähnlichkeit ≥ 0,90 **plus** normalisierter Textvergleich, je Organisation
konfigurierbar, und **nie blockierend**.** Advisory, weil der Kunde ausdrücklich eine „Warnung"
verlangt und weil ein blockierender Ähnlichkeitsvergleich bei LLM-Prompts viele berechtigte
Varianten treffen würde (z. B. derselbe Support-Bot für zwei Produktlinien). Die Schwelle ist ein
Kompromiss: 0,90 findet eher echte Kopien, 0,80 findet mehr Verwandtschaften und mehr Rauschen.

✅ **ENTSCHIEDEN (2026-10-20): Vektor-Ähnlichkeit ≥ 0,90 plus normalisierter Textvergleich**,
organisationsweit konfigurierbar und **nie blockierend** — wie empfohlen.

---

❓ **F9** — **80-%-Abdeckung als Blocker oder als Bericht?** Wird die Testabdeckung in der CI
**hart** durchgesetzt oder nur gemeldet?

➡️ **Empfehlung: hart durchsetzen (Blocker).** Die Kunden-Mail formuliert es als Pflicht („ist
Pflicht"), und ein Quality-Gate, das nur berichtet, fällt erfahrungsgemäß nach drei Sprints zurück.
Konsequenz: Der KI-Prüfer muss hinter einem Adapter-Interface liegen (Antworten im Test per Stub),
damit der 80-%-Nachweis die Orchestrierungslogik belegt statt das Modell zu messen. Messgröße:
**Line Coverage** über Backend und Frontend, ohne generierten und ohne Testcode.

✅ **ENTSCHIEDEN (2026-10-20): Harter Blocker in der CI** — wie empfohlen.

---

❓ **F10** — **Bestätigung des Technologie-Frameworks:** Ich habe aus „React" (Frontend) und
„Langchain4j" (Java-Bibliothek) auf **React 19 + Spring Boot 3 / Java 21 + PostgreSQL 16**
geschlossen. Bestätigt der Kunde dieses Framework, oder besteht er (z. B. aus Python-Expertise
seines Teams) auf FastAPI?

➡️ **Empfehlung: React 19 + Spring Boot 3 / Java 21 bestätigen.** Fachliche Begründung:
Langchain4j ist der vom Kunden genannte Prüfbaustein und existiert nur für die JVM; ein
Python-Backend würde entweder Langchain4j erfordern (mehrere Prozesse, Chatprotokoll zwischen
Python und Java) oder den Kundenwunsch ersetzen (dann LangChain/Python). Die React-Oberfläche ist
backend-neutral, deshalb ist sie kein Argument für eine Sprachentscheidung. Wenn Python bevorzugt
wird, ist der Wechsel von „Langchain4j" zu „LangChain (Python)" der Klärungspunkt — dann ist aber
ein zusätzlicher Prozess-/Broker-Betriebsaufwand zu budgetieren.

✅ **ENTSCHIEDEN (2026-10-20): React 19 + Spring Boot 3 / Java 21 + PostgreSQL 16** bestätigt —
wie empfohlen.

---

## Was nach Beantwortung dieser Runde passiert ist (erledigt)

1. OK `open_decisions.json` ist mit allen 10 Entscheidungen gefuellt (`status: resolved`),
   `docs/requirements/PRD.md` steht auf `1.0`, die Annahmen `A-01 ... A-12` sind als
   *bestaetigt* bzw. *akzeptiert mit Restrisiko* markiert.
2. OK `docs/requirements/tools/generate_artifacts.py` wurde neu ausgefuehrt -> Stories, Features und
   Traceability-Matrix sind konsistent neu erzeugt (35 Stories, 35 Features, 108 AK,
   97 Anforderungen ohne Differenz zwischen PRD und Matrix).
3. OK Aus F1 wurden die Artefakte umgeschrieben: **FR-074 (neu)**, FR-038, DP-002, DP-005, SEC-006,
   FR-065, A-12, Glossar, US-019, US-022 (neu: "Uebermittlung an den KI-Dienst unter festen
   Auflagen"), US-034, Risiko-Register (R-06) und die DoD-Liste.
4. NEXT **Phase 2 (Architektur)** kann starten: `docs/api/openapi.yaml`,
   `docs/architecture/domain_model.md`, C4-Diagramme, ADR-001...ADR-008,
   `.agent/knowledge/schema.sql`. **ADR-006 muss die unredigierte Uebermittlung, den AVV und die
   Provider-Verifikation ausdruecklich festhalten.**


## Nicht Teil dieser Frontier (bewusst nicht erfragt)

| Thema | Warum nicht erfragt |
|---|---|
| Technologie-Stack (Spring Boot vs. FastAPI) | Faktenlage entscheidet es (CON-001); nur die Bestätigung wird verlangt (F10). |
| Tabellenformate, Indizes, Datenbankschema | Architekturentscheidung in Phase 2, aus dem Domainmodell ableitbar. |
| Transportverfahren für Live-Updates (SSE vs. Polling) | Architekturentscheidung; das beobachtbare Verhalten ist als FR-040/NFR-003 festgelegt. |
| Konkrete KI-Modelle und Prompt-Formulierungen des Prüfers | Architektur-/Implementierungsdetail; die Anforderung ist modellneutral formuliert. |
| UI-Design, Farben, Layout | Nicht Anforderungsgegenstand; WCAG 2.1 AA ist als NFR-015 gesetzt. |
