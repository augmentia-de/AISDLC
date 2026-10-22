# Nacharbeit zu den Security-Befunden (Schritt 8, **zweite Runde**) — Ruecksprung zu Schritt 6/7

> Agent: `aisdlc-execution` · Stand: 2026-10-22 · Quelle:
> `docs/qa/security-audit.md` (S-28 … S-34) und `docs/qa/qa-report.md` (Q-8, Q-9).
>
> Ausfuehrlicher Bericht:
> [`docs/execution/step8-nacharbeit-security-run2.md`](../../docs/execution/step8-nacharbeit-security-run2.md).

## Stand

| Feld | Wert |
|---|---|
| Schritt | **Ruecksprung zu Schritt 6/7** — die Security-Befunde der zweiten Audit-Runde sind behoben |
| Befunde | **7 von 7** Security-Befunden (S-28 … S-34) behoben, davon **1 High** |
| Lueckenbelege | **alle vier gruen** (`SecurityGapRound2IT` ×2, `SecurityGapRound2Test` ×2) |
| Vertrag | `docs/api/openapi.yaml` **unveraendert** (`git diff --stat -- docs/api/` ist leer) — S-30 wurde **innerhalb** des Vertrags geloest (400 `malformed_request`) |
| Schema | unveraendert — beide Spiegel byte-identisch |
| Offen | **O-05** (postcss als Laufzeit-Abhaengigkeit, befristet), **Q-8**, **Q-9** — **keine** Befunde |

## Was geaendert wurde

| Befund | Aenderung | Fundstelle | Nachweis |
|---|---|---|---|
| **S-28** (High) | Die Grenze des Request-Bodies haengt **nicht mehr am Medientyp**: dieselbe Bedingung (Methode) fuer Vorpruefung und begrenztes Lesen; der Filter liest den Body **vor** dem Converter **begrenzt** (2 MiB) und gibt nur diese Byte weiter; die Mandantenparameter-Pruefung im Body ist ebenfalls medientyp-unabhaengig; zusaetzlich akzeptieren die Operationen mit Body nur `application/json` | `tenant/TenantParameterFilter.java` (vorher `:82`, `:98`, `:160-167`), `event/EventController.java`, `endpoint/EndpointController.java`, `delivery/DeliveryController.java`, `protocol/ProtocolController.java` | `RequestBodyMediaTypeLimitIT` (neu, 8 Tests: 6 Medientypen + **ohne** `Content-Type` + Mandantenparameter) · `SecurityGapRound2IT` (vorher 202, jetzt **413**) |
| **S-29** | Die Zielgruppen-Pruefung ist **immer** in der Pruefkette; ohne Zielgruppe wird **jedes** Token abgelehnt (fail-closed). Neu: `AudiencePolicy` — der Dienst **startet nicht** bei leerer Zielgruppe (analog S-27). Vorgabewert `echobox` bleibt | `config/SecurityConfiguration.java` (vorher `:126`), `config/AudiencePolicy.java` (neu) | `SecurityGapRound2Test` (vorher rot, jetzt gruen) · `AudienceFailClosedTest` (neu, 4 Tests) |
| **S-30** | `HttpMediaTypeNotSupportedException`, `HttpMediaTypeNotAcceptableException` und `MultipartException` → **400 `malformed_request`** (der Vertrag kennt kein 415); **kein** Stacktrace im Protokoll (hoechstens DEBUG) | `common/GlobalExceptionHandler.java` (vorher kein eigener Fall, `:230`) | `SecurityGapRound2IT` (vorher 500, jetzt 4xx) · `MediaTypeErrorIT` (neu, 6 Tests) |
| **S-31** | `<tomcat.version>` 10.1.55 → **10.1.61** — die hoechste **erreichbare** Version der 10.1.x-Linie (10.1.58 wurde nie veroeffentlicht; die Fix-Linie beginnt mit 10.1.59). Erreichbarkeit belegt: `dependency:get` → Exit 0, Download von `tomcat-embed-core-10.1.61.jar`; Wirksamkeit belegt: OSV-Abfrage **20 → 17** Advisories (die drei Tomcat-Advisories entfallen) | `pom.xml` (vorher `:52`) | `SecurityGapRound2Test` (vorher rot, jetzt gruen) · `DependencyHardeningTest` (Untergrenze 10.1.59) · `ContainerHardeningIT` (Pin 10.1.61) |
| **S-32** | **Baseline** `osv-scanner.toml` (26 Eintraege: 17 Java + 9 npm) mit Begruendung und Ablaufdatum; die Pipeline uebergibt sie (`--config=osv-scanner.toml`) — nur **neue** Advisories schlagen an. Pflege dokumentiert (hoechstens 6 Monate, Owner `aisdlc-qa`, Freigabe mit GATE 2) | `.github/workflows/deploy.yml` (vorher `:56-61`), `osv-scanner.toml` (neu) | `ScaBaselineIT` (neu, 3 Tests) · `tomllib`-Pruefung (26 Eintraege gueltig, keiner abgelaufen) |
| **S-33** | Ein Token **ohne** `organisation_id` ergibt **401 `unauthorized`** statt 500; der Mandant wird weiterhin nicht geraten (fail-closed) | `tenant/TenantTransactionRunner.java` (vorher `:92`), `common/UnauthenticatedException.java` | `MissingTenantIT` (neu, 1 Test) |
| **S-34** | Der Javadoc verweist nicht mehr auf eine ausstehende Nachziehung; er nennt die Uebergaenge `pending -> delayed` und `scheduled -> delayed` | `delivery/DeliveryStateMachine.java` (vorher `:28-29`, `:120-121`) | `DeliveryStateMachineDocumentationTest` (neu, 2 Tests) |

## Zwei Korrekturen gegenueber dem Audit (mit Beleg)

1. **`--ignore-dev` gibt es in osv-scanner v2.7.0 nicht** (Quelle des Tags
   `v2.7.0`, `cmd/osv-scanner/internal/helper/flags.go`). Stattdessen traegt
   jedes Advisory einen Einzeleintrag in der Baseline.
2. **Nicht alle npm-Advisories betreffen devDependencies.** `postcss 8.4.31`
   wird von `next 15.5.28` als **Laufzeit**-Abhaengigkeit mitgeliefert (im
   Lockfile nicht als `dev` markiert) → **O-05**, befristet ausgenommen.

## Offene Punkte (keine Befunde)

| Punkt | Sachverhalt | Owner |
|---|---|---|
| **O-05** | `postcss 8.4.31` als Laufzeit-Abhaengigkeit von `next` — vier Advisories sind befristet ausgenommen | Wartung / Schritt 9 |
| **Q-8** | Der Zaehler des Anfragelimits liegt je Instanz im Speicher | Schritt 9 |
| **Q-9** | Anfragen ohne Mandant werden nicht gezaehlt (sie werden nun mit 401 abgelehnt) | Fachbereich + Betrieb, GATE 2 |
| — | Das Secret `*_SECURITY_AUDIENCE` darf **nicht leer** sein — der Dienst startet sonst nicht mehr (fail-closed, gewollt) | Betrieb / Schritt 9 |

## Verifikation

`mvn -B verify` gruen (126 Unit + 301 Integration, Coverage-Gate erfuellt,
**85,99 %** Zeilen) · `npx tsc --noEmit` 0 Fehler · `npm run lint` 0 ·
`npm run build` ok · `npx vitest run` 30 gruen · `npm run test:e2e` 31 gruen ·
beide Schema-Diffs leer · `python3 .agent/tools/traceability_check.py` gruen ·
`git diff --stat -- docs/api/` leer · **alle vier Lueckenbelege der zweiten
Runde gruen**.
