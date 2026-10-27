# Logging-Konvention — <Projektname>

> **Template.** Wird in Schritt 6 (Implementierung) aus dem Code heraus gefüllt — die
> Konvention folgt dem Code, nicht umgekehrt. Der Security-Scan prüft später
> **gegen diese Datei**, nicht gegen eine eigene Erwartung.
>
> Steht diese Datei noch auf dem Template-Stand, ist das selbst ein Befund:
> „Logging-Konvention nicht festgeschrieben".

## Grundsatz

```text
EINE ZEILE JE EREIGNIS
```

Kein mehrzeiliger Stacktrace im Logeintrag, keine Freitextblöcke, keine
Stacktraces in der **Antwort** an den Aufrufer.

## Pflichtfelder je Zeile

| Feld | Typ | Bedeutung |
|---|---|---|
| `ts` | ISO-8601 | Zeitpunkt des Ereignisses, UTC |
| `level` | Enum | `INFO`, `WARN`, `ERROR` |
| `event` | String | Ereignistyp, stabil, punktgetrennt (`delivery.attempt`, `auth.token.rotated`) |
| `correlationId` | UUID | **durchgängig** über Request → Datenbank → Zustellung |
| `tenant` | String | Mandant, **nur** wenn der Kontext mandantenbegrenzt ist |
| `outcome` | Enum | `ok`, `rejected`, `failed` |
| `durationMs` | Zahl | Dauer der Operation |

**Optional:** `attempt` (Versuchszahl), `status` (HTTP-Status), `errorCode`
(stabiler Fehlercode aus dem Vertrag — **kein** Exception-Text).

## Was **nicht** hinein darf

| Verboten | Warum |
|---|---|
| Nutzdaten, Payloads, Request-Bodies | Datenschutz, Datenminimierung |
| Passwörter, Tokens, Secrets, API-Keys | Credential-Leak |
| vollständige Personendatensätze | Datenschutz |
| Exception-Stacktraces **in der Antwort** | interne Details nach außen |
| SQL-Statements mit Werten | Datenminimierung, Datenleck |
| Mandantenübergreifende IDs ohne Kontext | Mandantenbruch |

Ein Stacktrace **im Log** ist erlaubt und gewünscht — er ist Diagnose. Er darf nur
nicht in der Antwort stehen.

## Korrelations-ID

Die ID entsteht am **Eingang** (Middleware/Filter) und wird mitgeführt durch:

```
Request → Service → Datenbank → Zustellprozess → Log
```

**Regeln:**
- Sie ist pro Request/Operation eindeutig, nicht pro Mandant.
- Sie wandert **nicht** in die Antwort, außer der Vertrag sieht es vor — sonst
  liefert sie einen Angriffsvektor für das Nachvollziehen fremder Requests.
- Der Zustellprozess erzeugt eine **eigene** ID je Zustellversuch und führt die
  ursprüngliche als `parentCorrelationId` mit. So bleibt der Weg vom Ereignis bis
  zur Zustellung nachvollziehbar.

## Health und Readiness

Der Health-Check liest dasselbe Format:

| Endpoint | Antwort | Zweck |
|---|---|---|
| `GET /health` | `{"status":"up"}` | lebt der Prozess |
| `GET /health/ready` | `{"status":"ready","checks":{…}}` | arbeitet er — Datenbank, Worker, Mandantenkontext |

**Der Readiness-Check prüft den letzten Heartbeat**, nicht nur, ob der Prozess
existiert. Ein Prozess, dessen Zustellung festgefahren ist, lebt — ist aber nicht
bereit. Genau das findet ein reiner Prozess-Check nicht.

## Retention

| Ebene | Dauer | Grund |
|---|---|---|
| Betriebslog | <Angabe> Tage | Diagnose |
| Audit-Log | <Angabe> Tage | Nachweis |
| Rohtelemetrie | <Angabe> Tage | Auswertung |

Nach Ablauf wird **gelöscht**, nicht nur aus der Auswertung genommen.

## Umsetzung je Stack

| Feld | Java / Spring Boot | TypeScript / Node |
|---|---|---|
| Format | JSON pro Zeile (Logback) | JSON pro Zeile (pino) |
| `correlationId` | MDC | AsyncLocalStorage |
| `tenant` | MDC, aus dem JWT | AsyncLocalStorage |
| Health | Actuator | eigener Endpunkt |

**Die konkrete Bibliothek ist Stack-Sache, die Konvention nicht.** Diese Datei
beschreibt das Format, nicht die Technik.

## Prüfung durch den Security-Scan

| # | Prüfung | Schwere |
|---|---|---|
| L-1 | Diese Konvention existiert und ist gefüllt | Lücke |
| L-2 | Kein Payload, kein Secret, keine Personendaten in Logs | Lücke |
| L-3 | Kein Stacktrace in der Antwort | Lücke |
| L-4 | Jede Zeile hat die Pflichtfelder | Lücke |
| L-5 | Die Korrelations-ID ist durchgängig | Hinweis |
| L-6 | Retention ist festgelegt und wirksam | Hinweis |
