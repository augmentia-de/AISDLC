# ADR-010: Rate-Limit je Endpunkt

> **Bestätigt am 2026-10-21 (Gate 1, F-01):** Vorgabewert **60 Zustellversuche
> je Endpunkt und Minute**, Strategie **`delay`** (verschieben). Der Wert bleibt
> **konfigurierbar** und je Endpunkt überschreibbar.

## Kontext

FR-033: *„Ein Endpunkt ist gegen zu viele Zustellungen in kurzer Zeit
geschützt."* Die zulässige Rate war in der Anforderung ausdrücklich offen.

Abschnitt 13 der Anforderung nennt **zwei** offene Fragen:

1. **Welche** Rate ist „angemessen"?
2. Was passiert bei Überschreitung — **blockieren** oder **verschieben**?

D-14 legt fest: der Begrenzungsmechanismus wird **gebaut und konfigurierbar**
gehalten; erhoben werden nur die Zähler je Endpunkt und Zeitfenster als
Datenbasis.

**O-01 ist entschieden (2026-10-21, F-01).** Diese ADR entscheidet den
**Mechanismus und den Vorgabewert**.

AC-018-1: bei Überschreitung greift die konfigurierte Strategie, und **kein
Ereignis geht verloren**. AC-018-2: die Verzögerung ist im Protokoll erkennbar
(Status und nächster Versuchszeitpunkt). AC-018-3: die Begrenzung wirkt **je
Endpunkt** — ein anderer Endpunkt wird nicht verzögert. AC-018-4: der Dienst
erhebt die Zählung je Endpunkt und Zeitfenster — unabhängig davon, ob ein
Grenzwert gesetzt ist.

## Entscheidung

### 1. Zwei Rollen: Zähler und Begrenzer

| Teil | Zweck | Zustand |
|---|---|---|
| **Zähler** (`endpoint_rate_limit_counters`) | Zählt die Zustellversuche je Endpunkt und Zeitfenster | **immer aktiv** |
| **Begrenzer** (`endpoint_rate_limits`) | Hält Grenzwert, Fensterlänge und Strategie je Endpunkt | `limit_value` `NULL` = **kein Override** — es gilt der Vorgabewert aus der Konfiguration |

Die Tabelle kennt damit **genau zwei** Zustände. Eine dritte Spalte (`enabled`)
ist bewusst **nicht** vorhanden: sie würde einen dritten, widersprüchlichen
Zustand erzeugen — `enabled = FALSE` bedeutet faktisch „die Begrenzung ist
aus", obwohl der Vorgabewert **pro Umgebung** gilt (E-02, N-08).

Der Zähler läuft **immer**. Er ist die Datenbasis, mit der der Grenzwert
belegt — statt geschätzt — eingestellt werden kann.

### 2. Strategien

| Strategie | Verhalten | Datenverlust? |
|---|---|---|
| `delay` (**Vorgabe, bestätigt**) | Der Versuch wird **verschoben**: `next_attempt_at` wird in das nächste freie Fenster gelegt. Der Lauf trägt den Zustand `delayed`. | nein |
| `block` (Alternative, konfigurierbar) | Der Versuch wird zurückgestellt, bis das Fenster wieder frei ist. | nein |

**Kein Ereignis geht verloren** — das ist AC-018-1 und die zentrale Zusage.
Ein Limit, das Zustellungen **verwirft**, wäre ein Datenverlust und ist
ausgeschlossen. Ein Limit, das den Annahme-Request blockiert, wäre ein
Latenzbruch (NFR-002) und ist ebenfalls ausgeschlossen.

> **Klarstellung (A-6 / B-17, 2026-10-22).** Der Zustand `delayed` ist aus
> **jedem offenen Zustand** erreichbar — `pending`, `scheduled` und `failed`.
> Das Endpunkt-Limit wirkt auch auf den **ersten** Versuch eines frischen
> Laufs; sonst käme ein Endpunkt in der ersten Minute über das Limit hinaus.
> Der Vertrag (`components.schemas.DeliveryStatus`) nennt `delayed` ohne
> Einschränkung des Ausgangszustands. Die Übergänge stehen in
> `docs/architecture/domain_model.md` §3.3 und sind in
> `DeliveryStateMachine.TRANSITIONS` abgebildet. Diese Klarstellung ändert
> **keine** Entscheidung dieser ADR — F-01 (Mechanismus und Vorgabewert)
> bleibt unverändert.

### 3. Vorgabewert: 60 Versuche je Endpunkt und Minute

| Wert | Konfiguration | Vorgabe (bestätigt am 2026-10-21) |
|---|---|---|
| Grenzwert | `echobox.delivery.rate-limit.limit` | **60** Versuche je Endpunkt und Minute |
| Fensterlänge | `echobox.delivery.rate-limit.window-seconds` | **60 s** |
| Strategie | `echobox.delivery.rate-limit.strategy` | **`delay`** |

- Der Vorgabewert gilt **pro Umgebung**; `echobox.delivery.rate-limit.limit`
  kann je Umgebung abweichen (z. B. in einer Staging-Umgebung).
- Je Endpunkt überschreibbar: eine Zeile in `endpoint_rate_limits` mit
  `limit_value` überschreibt den Vorgabewert für diesen Endpunkt.
  `limit_value IS NULL` heißt: **kein Override** — es gilt die Vorgabe.
  `limit_value = 0` schaltet die Begrenzung für diesen Endpunkt **ab**.
  Es gibt **keinen** dritten Zustand (E-02).
- Der Wert ist **keine** harte API-Grenze: er steht in der Konfiguration, nicht
  im Vertrag, und ändert sich ohne Codeänderung.
- Die Zählung läuft unabhängig vom Grenzwert. `GET /api/v1/endpoints/{endpointId}/metrics`
  liefert den **wirksamen** Grenzwert und die beobachteten Zählerstände
  (AC-018-4).
- **Wie der wirksame Grenzwert ausgewiesen wird** (ADR-016 — der Vertrag ist
  der einzige Ort): `rate_limit.limit` ist **immer** der wirksame Grenzwert
  (Vorgabe **60**), `rate_limit.limited` sagt, ob die Begrenzung
  **abgeschaltet** ist (`limited: false` ⇒ `limit: 0`). `null` ist als Wert
  des Grenzwerts **ausgeschlossen** — früher bedeutete `null` zwei verschiedene
  Dinge und damit zwei Zustände (S-04, V-03).

### 4. Je Endpunkt, nicht global

Die Zählung und die Begrenzung sind **je Endpunkt** (AC-018-3). Ein Endpunkt,
der sein Limit überschreitet, bremst **nicht** andere Endpunkte. Das Fenster
ist konfigurierbar (Vorgabe 60 s).

### 5. Sichtbarkeit im Protokoll

Ein verschobener Versuch hinterlässt **zwei** Spuren:

| Spur | Ort |
|---|---|
| Der Zustand `delayed` mit `next_attempt_at` | `delivery_runs` → `GET /deliveries` (AC-018-2) |
| Der Zählerstand inkl. `delayed_count` | `endpoint_rate_limit_counters` → `EndpointMetrics.rate_limit` |

`delayed` ist ein **sichtbarer Zwischenzustand** — er steht im
Zustandsautomat (`docs/architecture/domain_model.md`), nicht in einer
Fußnote.

### 6. Zählung: was zählt als Versuch?

Ein **abgeschickter HTTP-Versuch** zählt — nicht die Annahme und nicht die
Planung. Ein 2xx, ein 4xx und ein 5xx zählen **gleich**: der Endpunkt hat
arbeitet bekommen. Der Zähler wird nach jedem abgeschickten Versuch erhöht —
auch bei einem Timeout, denn der Empfänger hat die Anfrage möglicherweise
bearbeitet.

## Festlegung (Gate 1, 2026-10-21)

> **F-01 (O-01):** 60 Zustellversuche je Endpunkt und Minute; bei
> Überschreitung **verschieben** (`delay`), nicht blockieren, nicht verwerfen.
> *Warum das der Fachbereich entscheidet:* Die erlaubte Rate richtet sich
> danach, was ein abonnierendes System an Last verträgt — das ist eine
> Vereinbarung über die Betriebslast zwischen zwei Teams und keine technische
> Grenze. Der Wert ist deshalb eine **Vorgabe in der Konfiguration**, kein
> im Code verdrahteter Grenzwert: er ist jederzeit je Umgebung und je
> Endpunkt änderbar.
> *Zusammen mit entschieden:* der Mechanismus, die Zählweise, die
> Wirksamkeit **je Endpunkt**, die Sichtbarkeit im Protokoll und die Zusage,
> dass **kein** Ereignis verloren geht.

## Konsequenzen

- **Positiv:** Der Schutz ist **wirksam** — ohne Codeänderung, nur über
  Konfiguration.
- **Positiv:** Die Zählung läuft unabhängig vom Grenzwert; der Wert ist
  **belegt** eingestellt statt geschätzt.
- **Positiv:** Der Zustand `delayed` macht die Verzögerung sichtbar — der
  Empfänger-Admin sieht, dass der Endpunkt gedrosselt wird.
- **Negativ:** Der Zähler wächst auch dann, wenn kein Grenzwert gesetzt ist.
  Er wird durch die Aufbewahrung mit dem Zustellprotokoll bereinigt
  (ADR-012) — sonst wäre er eine unbegrenzte Wachstumsquelle.
- **Negativ:** Bei `delay` verschiebt sich der Versuch; der Empfänger merkt
  davon nichts (außer an der Zeit). Das ist gewollt — die Alternative
  (Verwerfen) wäre Datenverlust.
- **Negativ:** Die Zählung kostet einen `INSERT`/`UPDATE` je Versuch. Bei
  hohem Volumen wird sie in den Versuchspfad eingebettet (bereits der Fall:
  der Versuch schreibt ohnehin eine Zeile in `delivery_attempts`).

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Kein Zähler** | Dann gäbe es keine Datenbasis — der Grenzwert wäre eine Vermutung statt einer Entscheidung (AC-018-4). |
| **Globaler Grenzwert für alle Endpunkte** | Ein langsamer Endpunkt würde alle anderen bremsen (AC-018-3). |
| **Verwerfen bei Überschreitung** | Datenverlust — widerspricht AC-018-1 und dem Leitsatz des PRD. |
| **Blockieren statt Verschieben** | Hält den Worker länger; Verschieben ist die schonendere Variante und wurde bestätigt. |
| **HTTP 429 an den Aufrufer der Annahme** | Der Aufrufer ist nicht der Empfänger; das ist ein API-Limit, nicht das Endpunkt-Limit (FR-033). |
| **Zählung im Arbeitsspeicher des Workers** | Überlebt keinen Neustart und ist nicht mandantenfest; ein zweiter Worker sähe eine andere Zahl. |
| **Token-Bucket im Worker** | Verteilte Zähler über mehrere Instanzen sind ohne gemeinsamen Zustand nicht korrekt. Die Datenbank ist der gemeinsame Zustand — und hat sie schon. |
| **Grenzwert fest im Code verdrahtet** | Widerspricht der Konfigurationsregel des Projekts und der Tatsache, dass die zulässige Last eine Vereinbarung zwischen Teams ist. |

## Status

**Angenommen** — der Mechanismus und der Vorgabewert sind am 2026-10-21 am
Gate 1 bestätigt (F-01, entschieden O-01). Der Wert bleibt konfigurierbar.
