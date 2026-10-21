# ADR-013: Mandanten-Isolation pro Instanz und Containerbetrieb

## Kontext

NFR-006: *„Der Dienst läuft in Containern, ist auf eine Instanz je Mandant
vorbereitet und skaliert horizontal."*

AC-023-1: das Container-Image startet mit einem Gesundheitsendpunkt, enthält
**keine** Secrets und **keine** fest verdrahteten Zugangsdaten und läuft als
**nicht privilegierter** Benutzer.
AC-023-2: eine Konfiguration, die einem Mandanten zugeordnet wird, bedient
**nur** diesen Mandanten.
AC-023-3: bei zwei parallelen Instanzen wird ein Zustellversuch von **genau
einer** Instanz ausgeführt (keine Doppelzustellung durch konkurrierende Worker).
AC-023-4: ein Absturz während eines Zustellversuchs geht **nicht** verloren.
AC-023-5: die Pipeline stellt Staging und Produktion **getrennt** bereit, mit
getrennten Konfigurationen und Secrets.

Das PRD (Abschnitt 5.3) stellt klar: eine **feste** Instanz je Mandant ist im
Regelbetrieb **nicht** Teil des Scopes — NFR-006 verlangt nur, dass der Dienst
**vorbereitet** ist. Geteilter Betrieb bleibt Standard.

## Entscheidung

### 1. Ein Image, zwei Rollen, ein Schema

| Baustein | Entscheidung |
|---|---|
| Image | **ein** Image, zwei Startprofile: `api` (HTTP) und `worker` (Zustellung) |
| Basis | gepinnte Basis-Image-Digest; Multi-Stage-Build; Endstufe enthält **kein** Build-Werkzeug |
| Benutzer | **kein** Root; eigener unprivilegierter Benutzer; `read-only`-Root-Dateisystem, beschreibbare Volumes nur für `/tmp` |
| Secrets | **ausschließlich** über die Umgebung (Secret-Injektion). Im Image, in der Konfiguration und im `docker-compose.yml` steht **kein** Geheimnis |
| Build-Kontext | `.dockerignore` schließt `.env`, `.git`, `target/`, `node_modules/` aus |
| Health | `GET /health` (Liveness) und `GET /health/ready` (Readiness) **ohne** Authentifizierung — der Container darf erst nach Readiness Traffic annehmen |

### 2. Mandanten-Sharding über Konfiguration

```
echobox.tenant.id = <mandant>      # optional; leer = gemeinsamer Betrieb
```

- Ist der Wert gesetzt, bedient die Instanz **nur** diesen Mandanten: der
  Mandantenkontext wird **nicht** aus dem Token, sondern aus der Konfiguration
  gesetzt; ein Token eines anderen Mandanten wird mit HTTP 403 abgelehnt
  (`tenant_mismatch`).
- Ist der Wert leer, gilt der **geteilte** Betrieb: der Mandant kommt aus dem
  Token (D-01).
- Die Umschaltung ist eine **Konfigurationsentscheidung**, kein Codewechsel:
  dieselbe Datenbank, dieselbe Anwendung, nur eine andere Konfiguration.

### 3. Horizontale Skalierung und Doppelzustellung

- Die API skaliert stateless (ADR-006).
- Der Worker skaliert ebenfalls — die Koordination liegt in der Datenbank:
  `FOR UPDATE SKIP LOCKED` + Lease (ADR-006, AC-023-3). Es braucht **keinen**
  verteilten Lock-Dienst.
- Eine Mandanteninstanz und ein gemeinsamer Betrieb **dürfen nicht** parallel
  auf dieselbe Datenbank zeigen — das wäre eine geteilte Zustandshaltung ohne
  Absprache. Deshalb ist `echobox.tenant.id` **bindend** und wird beim Start
  protokolliert.

### 4. Umgebungen getrennt

| Aspekt | Staging | Produktion |
|---|---|---|
| Datenbank | eigene Instanz, eigene Credentials | eigene Instanz, eigene Credentials |
| Secrets | eigener Secret-Store-Namespace | eigener Secret-Store-Namespace |
| Empfänger-URLs | eigene Registrierungen | eigene Registrierungen |
| Image-Tag | `main` / Commit-SHA | Commit-SHA, **immutable** |

Ein Endpunkt in Staging kann keine Ereignisse aus der Produktion empfangen —
nicht über eine Konfiguration, sondern über getrennte Datenbanken.

### 5. Abbau der Aufbewahrung (kein Detail, sondern Betrieb)

Die Purge-Aufträge (ADR-012) laufen als **eigene, einmalige** Aufgabe im
gleben Image — nicht als dauerhafter Prozess. So gibt es kein „vergessenes
Cron“ und keine konkurrierenden Purge-Läufe.

## Konsequenzen

- **Positiv:** Der Container enthält keine Geheimnisse; ein Image-Leck gibt
  keine Daten preis (AC-023-1).
- **Positiv:** Mandanten-Sharding ist eine Konfigurationsentscheidung
  (AC-023-2) — kein Sondercode, keine Sondertabelle.
- **Positiv:** Die Skalierung braucht keine zusätzliche Infrastruktur
  (AC-023-3, AC-023-4).
- **Negativ:** Mandanten-Sharding teilt die Datenbank nicht — ein Mandant mit
  hoher Last kann andere Mandanten beeinflussen (CPU, I/O, Verbindungen). Für
  **echte** Isolation pro Mandant wäre je Mandant eine eigene Datenbank nötig;
  das ist **nicht** gefordert und wäre operationell unverhältnismäßig.
- **Negativ:** `echobox.tenant.id` und der Mandant aus dem Token müssen
  übereinstimmen. Ein Fehler dort ist ein **Datenleck in die falsche Richtung**
  (Mandant A sieht Daten von Mandant B) — deshalb wird der Abgleich beim
  Token-Austritt erzwungen und protokolliert.
- **Negativ:** Zwei Betriebsarten (geteilt vs. Sharding) bedeuten zwei
  Betriebsweisen. Deshalb ist der Default der geteilte Betrieb.

## Vorschlag zur Freigabe bei Gate 1

> **Vorschlag (nicht entschieden):** Im Regelbetrieb **geteilter** Betrieb
> (eine Instanz für alle Mandanten); eine Instanz je Mandant bleibt als
> **vorbereitete** Betriebsweise möglich, aber nicht Standard.
> *Warum das der Fachbereich entscheidet:* Der geteilte Betrieb ist
> wirtschaftlicher und einfacher; die Mandanteninstanz ist die Antwort auf
> Mandanten mit besonderen Anforderungen (Volumen, Datenschutz, Exit-Strategie).
> Ob und wann ein Mandant eine eigene Instanz braucht, ist eine
> Mandantenbeziehung, keine Technikfrage. Die Architektur stellt beides bereit,
> **ohne** etwas vorzubereiten, was niemand nutzt.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Je Mandant eine eigene Datenbank** | Stärkste Isolation, aber operativ unverhältnismäßig (Backup, Migration, Pool je Mandant) und nicht gefordert. |
| **Mandant als Spalte ohne Sharding-Modus** | Dann gäbe es keine vorbereitete Instanz je Mandant (NFR-006). |
| **Shared-Nothing-Worker mit Broker** | Ausdrücklich ausgeschlossen. |
| **Docker-compose als einzige Betriebsform** | Reicht für die Entwicklung, nicht für Staging/Produktion mit getrennten Secrets (AC-023-5). |
| **Secrets im Image** | Verstößt gegen die Regeln des Projekts und gegen AC-023-1. |
| **Root im Container** | Verstößt gegen AC-023-1. |
| **Automatische Mandantenverteilung über Load-Balancer** | Der Mandant steckt in der Identität, nicht in der Route — der Load-Balancer kennt den Mandanten nicht (D-01). |

## Status

**Angenommen** — Bestätigung bei Gate 1 durch den Menschen.
