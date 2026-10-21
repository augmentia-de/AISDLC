# ADR-015: Fehlersemantik, Mandantenherkunft und Rollenmatrix

## Kontext

Drei Festlegungen des PRD hängen zusammen und gehören deshalb in eine ADR:

- **D-01:** Der Mandant stammt ausschließlich aus der Identität. Ein
  mitgelieferter Mandantenparameter wird mit **HTTP 422** abgelehnt, nie als
  Filter benutzt.
- **D-02:** Fremde Ressourcen werden mit **HTTP 404** beantwortet, nicht mit 403
  — so verrät die Antwort nicht, dass ein fremdes Objekt existiert.
- **D-15:** Rollenrechte — der Empfänger-Admin des betroffenen Endpunkts darf
  eine tote Zustellung erneut auslösen; bis zur Entscheidung aus **O-03**
  erhalten alle anderen Rollen HTTP 403.

Der Widerspruch aus R-09: FR-024 nennt den Empfänger-Admin, die Rollentabelle
in Abschnitt 3 nennt auch den Entwickler. Das ist die offene Frage **O-03**.

Die Rollen selbst: Abschnitt 3 der Anforderung nennt drei Rollen —
Entwickler, Empfänger-Admin, Service-Admin.

## Entscheidung

### 1. Rollenmatrix

| Rolle | Endpunkte verwalten | Ereignisse einliefern | Protokoll lesen | Tote Zustellung erneut auslösen | Totenliste mandantenübergreifend | Kennzahlen | Aufbewahrung erzwingen |
|---|---|---|---|---|---|---|---|
| `developer` | — | **ja** | **ja** (eigener Mandant) | **nein** (bis O-03) | **nein** | eigener Mandant | **nein** |
| `receiver_admin` | **ja** (eigener Mandant) | — | **ja** (eigener Mandant) | **ja** (eigener Endpunkt) | **nein** | eigener Mandant | **nein** |
| `service_admin` | **ja** (alle Mandanten) | — | **ja** (alle Mandanten) | **nein** (bis O-03) | **ja** | **ja** | **ja** |

Zusätzliche Rollenprüfungen:

| Aktion | Regel | Verstoß |
|---|---|---|
| Secret-Rotation | nur `receiver_admin` | 403 (AC-003-3) |
| Endpunkt anlegen / ändern / pausieren | nur `receiver_admin` und `service_admin` | 403 |
| Ereignis einliefern | `developer` (und `service_admin` für Migrations-/Testzwecke) | 403 |
| Endpunkt-/Protokoll-Listen | alle Rollen, aber **mandantengefiltert** | — |
| Totenliste | nur `service_admin` | 403 (AC-016-2) |
| Replay | nur `receiver_admin` **des betroffenen Endpunkts** | 403 (AC-013-3) |
| Aufbewahrung erzwingen | nur `service_admin` | 403 |

### 2. Mandant aus der Identität

- Der Mandant steht im JWT-Claim `organisation_id`. Er wird beim Token-Austritt
  gelesen und in den `TenantContext` übernommen.
- Ein **Mandantenparameter** im Request-Body, im Query oder in einem Header wird
  mit **HTTP 422** und `code: tenant_parameter_rejected` abgelehnt (D-01,
  AC-001-5, AC-004-4). Er wird **nicht** stillschweigend ignoriert — ein
  Ignorieren würde den Aufrufer im Glauben lassen, er hätte gefiltert.
- Für `service_admin` gibt es **auch** keinen Mandantenfilter: der
  Service-Admin sieht alle Mandanten. Ein Filterparameter wäre dort zwar
  sinnvoll, er würde aber die Regel „Mandant ist ein Filterparameter“ etablieren
  und damit den Boden für einen Leck legen. Der Service-Admin filtert
  **in der Oberfläche** nach `organisation_id`, das Feld ist Teil jeder
  Antwort.

### 3. Fremde Ressourcen ⇒ 404 (D-02)

Ein Endpunkt, ein Ereignis, ein Zustelllauf oder eine Kennzahl eines **fremden**
Mandanten wird mit **HTTP 404** beantwortet — nicht mit 403 (AC-002-3,
AC-015-3, AC-017-4, AC-022-4).

**Warum 404 und nicht 403?** 403 bestätigt: „dieses Objekt existiert, du
darfst es nur nicht sehen“. Damit liefert die Schnittstelle einem Angreifer
eine Bestandsaufnahme der fremden Mandanten. 404 sagt nur: *hier ist nichts*.

**403 bleibt** für den Fall, in dem das Objekt dem eigenen Mandanten gehört,
die Rolle aber die Operation nicht ausführen darf (z. B. Secret-Rotation als
Entwickler). Diese Unterscheidung ist konsistent: 403 = „dein Mandant, deine
Rolle“; 404 = „nicht dein Mandant“.

### 4. Fehlerschema

Ein einziges Schema (`Problem`, RFC-7807-artig) für alle Fehlerantworten:

```json
{ "type": "...", "title": "...", "status": 422, "code": "...",
  "detail": "...", "instance": "...", "violations": [ ... ] }
```

`code` ist der maschinenlesbare Schlüssel; `violations` nennt die
feldbezogenen Verstoße. Die Fehler werden **ausschließlich** an einer Stelle
erzeugt (`ProblemFactory` + `GlobalExceptionHandler`) — es gibt keine
Ad-hoc-Fehlerbodies.

### 5. Statuscode-Übersicht

| Status | `code` | Wann |
|---|---|---|
| 400 | `malformed_request` | kein gültiges JSON |
| 401 | `unauthorized` | kein/ungültiges Token |
| 403 | `forbidden` | Rolle darf es nicht |
| 404 | `not_found` | fremde oder fehlende Ressource |
| 409 | `idempotency_conflict` | gleicher Schlüssel, andere Nutzlast |
| 409 | `delivery_state_conflict` | Replay einer nicht aufgegebenen Zustellung |
| 413 | `payload_too_large` | Nutzlast > 1 MB |
| 422 | `validation_failed` | semantisch ungültig |
| 422 | `tenant_parameter_rejected` | Mandantenparameter mitgeschickt |
| 429 | `rate_limit_exceeded` | API-Anfragelimit (≠ Endpunkt-Limit) |
| 500 | `internal_error` | unerwarteter Fehler, ohne internes Detail |
| 503 | `service_unavailable` | Dienst nicht bereit |

## Vorschlag zur Freigabe bei Gate 1

> **Vorschlag (Berechtigung, nicht entschieden):** Das erneute Auslösen einer
> toten Zustellung ist der **Empfänger-Admin des betroffenen Endpunkts**
> (entspricht FR-024). Der Entwickler darf es **nur** für eigene Ereignisse
> und **nur** mit ausdrücklicher Freigabe des Mandanten. Der Plattform-Betrieb
> darf es **nicht** — sonst verlöre der Empfänger die Kontrolle über seinen
> eigenen Endpunkt.
> *Warum das der Fachbereich entscheidet:* Die Rollentabelle (Abschnitt 3)
> nennt den Entwickler, FR-024 den Empfänger-Admin — das ist ein **echter
> Widerspruch in der Anforderung**, den nur der Fachbereich auflösen kann.
> Bis dahin gilt D-15: alle anderen Rollen erhalten HTTP 403.

## Konsequenzen

- **Positiv:** Die Existenz fremder Objekte wird nicht preisgegeben (D-02).
- **Positiv:** Die Regel ist eindeutig: 403 = „Rolle“, 404 = „Mandant“.
- **Positiv:** `service_admin` bekommt alle Mandanten, ohne dass ein
  Mandantenfilter in die Schnittstelle muss.
- **Negativ:** `developer` darf sein eigenes Ereignis nicht erneut auslösen —
  das ist eine **Funktionslücke**, bis O-03 entschieden ist. Sie ist bewusst
  sichtbar (403), nicht stillschweigend.
- **Negativ:** `service_admin` kann **keine** Zustellung erneut auslösen, auch
  nicht für die Wartung eines Endpunkts. Der Betrieb kann stattdessen den
  Endpunkt reaktivieren und das Ereignis neu einliefern lassen.
- **Negativ:** Die Rollenprüfung muss an **jeder** Operation sitzen. Eine
  vergessene Prüfung ist ein Leck — die Rollenmatrix ist deshalb im Vertrag an
  jeder Operation dokumentiert.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Fremde Ressourcen ⇒ 403** | Gibt einen Bestandsschutz preis (D-02). |
| **Mandantenparameter ignorieren statt ablehnen** | Der Aufrufer glaubt, er hätte gefiltert — genau die stille Fehlannahme, die der Dienst vermeiden soll. |
| **Mandantenfilter für `service_admin`** | Etabliert „Mandant ist ein Filterparameter“ als Muster. |
| **Entwickler darf grundsätzlich replayen** | Widerspricht FR-024 und der Empfänger-Kontrolle. |
| **Service-Admin darf grundsätzlich replayen** | Nimmt dem Empfänger die Kontrolle über seinen Endpunkt. |
| **Rollenprüfung nur im Frontend** | Das Frontend ist kein Schutz — jeder Aufrufer spricht die API direkt an. |
| **Fehlerbodies pro Operation frei gestaltet** | Drei Fehlerformate in einem Vertrag sind drei Fehlerformate in jedem Client. |
| **Mandant aus einem Request-Header ableiten** | Ein Header ist genauso leicht zu fälschen wie ein Parameter — und wird leicht übersehen. |

## Status

**Angenommen** — Bestätigung bei Gate 1 durch den Menschen; die Replay-Berechtigung
bleibt bis O-03 offen.
