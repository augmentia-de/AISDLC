# Nacharbeit zu den Security-Befunden (Schritt 8) — Ruecksprung zu Schritt 6/7

> Agent: `aisdlc-execution` · Stand: 2026-10-22 · Quelle:
> `docs/qa/security-audit.md` (S-19 … S-27) und `docs/qa/qa-report.md`
> (A-1, A-6/B-17).
>
> Ausfuehrlicher Bericht: [`docs/execution/step8-nacharbeit-security.md`](../../docs/execution/step8-nacharbeit-security.md).

## Stand

| Feld | Wert |
|---|---|
| Schritt | **Ruecksprung zu Schritt 6/7** — die Security-Befunde des Audits sind behoben |
| Befunde | **9 von 9** Security-Befunden (S-19 … S-27) behoben, **2 von 2** QA-Befunden (A-1, A-6/B-17) behandelt |
| Vertrag | `docs/api/openapi.yaml` **unveraendert** (`git diff --stat -- docs/api/` ist leer) — S-24 wurde als **Erfuellung** der Zusage umgesetzt, nicht als Vertragsaenderung |
| Schema | unveraendert — beide Spiegel byte-identisch |
| Offen | **nur** die Dokumentationszeile in Schritt 2 (Uebergangstabelle) |

## Was geaendert wurde

| Befund | Aenderung | Nachweis |
|---|---|---|
| **S-19** (High) | Spring Boot 3.3.5 → **3.5.16** (bringt `tomcat-embed-core` **10.1.55** mit), `<tomcat.version>10.1.55</tomcat.version>` als Untergrenze, `spring.servlet.multipart.enabled: false` | `mvn dependency:tree` → 10.1.55; `DependencyHardeningTest` (liest die Version aus dem Klassenpfad) |
| **S-20** (High) | `TenantParameterFilter` liest den Body **begrenzt** (max. 2 MiB, dann Abbruch → 413), unabhaengig von `Content-Length`; `server.tomcat.max-swallow-size` 4MB → 2MB, `max-http-form-post-size: 2MB` | `SecurityGapIT` (vorher 202, jetzt 413) + `RequestBodyLimitIT` |
| **S-21** | eigener `JwtValidator`: **Audience** (`echobox.security.audience`) + **`exp` Pflicht** | `SecurityGapTest` ×2 (gruen) + `JwtAudienceHardeningTest` |
| **S-22** | Schluessellaenge = **exakt 32 Byte**, sonst klarer Startfehler | `SecurityGapTest` (gruen) + `CryptoKeyLengthTest` |
| **S-23** | `DeliveryHttpClient` liest die Antwort mit `LimitedBodySubscriber` (max. 1 MiB, Rest verworfen, Koerper weiterhin konsumiert) | `SecurityGapTest` (gruen) + `DeliveryResponseLimitTest` |
| **S-24** | `ApiRateLimiter` + `ApiRateLimitFilter`: **429** `rate_limit_exceeded` mit **`Retry-After`** (>= 1), je Mandant und Fenster (Vorgabe 240/min) | `SecurityGapIT` (vorher nie 429, jetzt 429) + `ApiRateLimitIT` |
| **S-25** | Pipeline: **Secret-Scan** (gitleaks v8.30.1), **SCA-Scan** (OSV-Scanner v2.7.0), Typpruefung/Lint/Unit/Build/**E2E** der Oberflaeche, Registry-Anmeldung mit dem **Token des Laufs** (`GITHUB_TOKEN`) statt statischem Token, `id-token: write` | Datei + `ContainerHardeningIT` (Profil je Umgebung) |
| **S-26** | `apt`-Pakete gepinnt: `curl=7.81.0-1ubuntu1.30`, `tini=0.19.0-1` (aus dem Basis-Image gelesen, dort ausgefuehrt und geprueft) | `ContainerHardeningIT` |
| **S-27** | `application-production.yml` (neu, `key-required: true`), Image-Vorgabe `SPRING_PROFILES_ACTIVE=production` (nicht `staging`), Profil je Umgebung in der Pipeline | `ContainerHardeningIT` |
| **A-1** | JaCoCo-Gate in `pom.xml`: `check` mit `LINE/COVEREDRATIO >= 0.80` in der Phase `verify`; `append=true` + Loeschung von `target/jacoco.exec` in `initialize` | `mvn verify` → „All coverage checks have been met"; `ContainerHardeningIT` |
| **A-6/B-17** | `DeliveryStateMachine.TRANSITIONS` um `pending -> delayed` und `scheduled -> delayed` ergaenzt; `onRateLimited` wird aus der Tabelle abgeleitet — **das Verhalten ist unveraendert** | Code + Teststand; die **Doku** folgt in Schritt 2 |

## Was Schritt 2 zu tun hat (Dokumentation)

`docs/architecture/domain_model.md` §3.3 — die Uebergangstabelle:

```
pending   -> scheduled, cancelled, no_subscriber, delayed
scheduled -> sent, rejected, failed, cancelled, delayed
failed    -> scheduled, delayed, abandoned, cancelled
delayed   -> scheduled, abandoned, cancelled
```

`delayed -> delayed` ist **kein** Uebergang (der Zustand aendert sich nicht).

## Offene Punkte (keine Sicherheitsluecke)

| Punkt | Owner |
|---|---|
| Der Zaehler des Anfragelimits liegt **je Instanz** im Speicher; ein mandantenuebergreifender Zaehler braucht ein Schema (in diesem Schritt nicht anfassbar) | Schritt 9 |
| Der Wert des Anfragelimits (240/min) und die Audience (`echobox`) sind Betriebswerte (konfigurierbar) | Betrieb / Schritt 9 |
| Scan des **fertigen Images** in der Zielumgebung; 93 nicht erreichbare Advisories | Schritt 9 |
| Der statische Registry-Token `REGISTRY_TOKEN` kann aus dem Secret-Store geloescht werden | Betrieb |
| Q-5 / Q-6 des Audits: durch die Versionen jackson 2.21.4 und spring-security 6.5.11 materiell entschaerft | Beobachtung |

## Verifikation

`mvn -B verify` gruen (113 Unit + 281 Integration, Coverage-Gate erfuellt) ·
`npx tsc --noEmit` 0 Fehler · `npm run lint` 0 · `npm run build` ok ·
`npx vitest run` 30 gruen · `npx playwright test` 31 gruen · beide
Schema-Diffs leer · `python3 .agent/tools/traceability_check.py` gruen ·
`git diff --stat -- docs/api/` leer.
