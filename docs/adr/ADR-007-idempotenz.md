# ADR-007: Idempotenz der Einlieferung

## Kontext

FR-011: *„Derselbe Idempotenzschlüssel liefert **kein** zweites Ereignis. Die
Wiederholung ist erfolgreich und liefert die Kennung des ersten Ereignisses
zurück."*

Der Ausgangsfall aus Abschnitt 1 der Anforderung: *„Unser Bestellservice
sendet bei einem Timeout unklarerweise nochmal.“* Genau dieser Fall muss
beherrscht werden — und zwar **ohne** Datenverlust (FR-011) und **ohne**
stille Fehlannahme.

D-05 legt den Gültigkeitsbereich fest: **(Mandant, Schlüssel)**. Eine
Wiederholung ist erfolgreich und liefert die ID des ersten Ereignisses; die
erste Nutzlast wird nie überschrieben; derselbe Schlüssel mit abweichender
Nutzlast wird mit HTTP 409 abgelehnt.

Offen bleibt in dieser Festlegung der Anforderung eine Frage: Was passiert bei
**gleichem Schlüssel, abweichender Nutzlast**? Ein stilles Zurückgeben der
ersten Nutzlast wäre gefährlich — der Aufrufer hielte neue Daten für gespeichert.
D-05 beantwortet diese Frage mit HTTP 409.

## Entscheidung

### Gültigkeitsbereich

`(organisation_id, idempotency_key)` — **je Mandant**. Derselbe Schlüssel in
zwei Mandanten erzeugt zwei eigene Ereignisse (AC-006-3). Durchgesetzt wird das
auf Datenbankebene durch `UNIQUE (organisation_id, idempotency_key)` — nicht
durch Anwendungslogik.

### Verhalten

| Fall | Antwort | Regel |
|---|---|---|
| Schlüssel neu | **202 Accepted**, `idempotent_replay: false`, neue `event_id` | Normallfall |
| Schlüssel bekannt, **gleiche** Nutzlast | **202 Accepted**, `idempotent_replay: true`, **die** `event_id` des ersten Ereignisses | FR-011, AC-006-1, AC-006-2 |
| Schlüssel bekannt, **andere** Nutzlast | **409 Conflict**, `code: idempotency_conflict` | D-05, AC-006-4 |

Die erste Nutzlast wird **nie** überschrieben. Typ und Nutzlast des ersten
Ereignisses bleiben unverändert (AC-006-2).

### Wie wird „gleiche Nutzlast" entschieden?

Über einen **gezielten HMAC-SHA256** über die kanonischen Nutzlastbytes
(`events.payload_fingerprint`, ADR-003). Kein Klartext, kein Index über den
Nutzlastinhalt. Der Vergleich passiert **vor** der Persistenz.

Kanonisierung: RFC-8785-artig (sortierte Schlüssel, keine bedeutungslosen
Leerzeichen, UTF-8). Damit ergibt derselbe fachliche Inhalt denselben
Fingerprint, unabhängig von der Schreibweise im Request.

### Atomarität

Der ganze Vorgang läuft in **einer** Transaktion:

1. `INSERT … ON CONFLICT (organisation_id, idempotency_key) DO NOTHING RETURNING event_id`
2. Wurde nichts eingefügt (Konflikt), wird der bestehende Datensatz gelesen und
   der Fingerprint verglichen.
3. Nur bei **neuem** Ereignis werden die Zustellläufe angelegt.

Damit kann unter keinen Umständen ein zweites Ereignis mit demselben Schlüssel
entstehen — auch nicht bei gleichzeitigen Einlieferungen.

### Das Idempotenzfenster

Der Idempotenzschlüssel gilt, solange das Ereignis existiert. Wird das
Ereignis durch die Aufbewahrung gelöscht, ist der Schlüssel wieder frei. Das ist
die Folge eines **kurzlebigen** Dienstes (kein Event-Store) und wird im
Vertrag ausdrücklich dokumentiert.

## Konsequenzen

- **Positiv:** Der Aufrufer kann gefahrlos wiederholen — genau das, was die
  Anforderung mit dem Timeout-Szenario verlangt.
- **Positiv:** Der Konfliktfall ist **laut** (409), nicht still. Es gibt keine
  Möglichkeit, dass neue Daten für gespeichert gehalten werden.
- **Negativ:** Der Fingerprint erzwingt eine kanonische Serialisierung. Ein
  Client, der „dieselbe“ Nutzlast mit anderer Schlüsselreihenfolge oder anderen
  Leerzeichen sendet, erzeugt denselben Fingerprint — das ist gewollt. Ein
  Client, der Zahlen unterschiedlich schreibt (`1` vs `1.0`), kann einen
  scheinbaren Konflikt auslösen; das ist eine dokumentierte Eigenschaft der
  kanonischen Form.
- **Negativ:** Der Fingerprint muss beim Erzeugen der Nutzlast gebildet werden —
  das ist Arbeit, die der Client nicht sieht, aber der Dienst leisten muss.
- **Negativ:** Nach der Aufbewahrung ist der Schlüssel wiederverwendbar. Das ist
  die konsequente Folge „der Dienst hält nur, was er für die Zustellung
  braucht, und das so kurz wie möglich“.

## Bestätigung bei Gate 1 (2026-10-21)

> **Bestätigt:** Der Idempotenzschlüssel gilt je **(Mandant, Schlüssel)**. Eine
> Wiederholung mit gleicher Nutzlast ist erfolgreich und liefert die Kennung des
> ersten Ereignisses; eine Wiederholung mit abweichender Nutzlast wird mit
> **HTTP 409** abgelehnt.
> *Warum das bestätigt wurde:* D-05 ist eine
> Festlegung der Anforderungserhebung, keine Fachbereichs-Entscheidung. Die
> 409-Semantik ist die einzige Stelle, an der der Dienst dem Aufrufer sagt
> „deine neue Nutzlast ist **nicht** gespeichert“ — das Verhalten muss für die
> aufrufenden Teams eindeutig und zugesagt sein.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Idempotenz global (ohne Mandant)** | Zwei Mandanten dürfen nicht aneinander kollidieren (AC-006-3). |
| **Still die erste Nutzlast zurückgeben** | Der Aufrufer hielte neue Daten für gespeichert — genau die stille Fehlannahme, die der Dienst vermeiden soll. |
| **409 auch bei gleicher Nutzlast** | Widerspricht FR-011: „Die Wiederholung ist **erfolgreich**“. |
| **Vergleich über SHA-256 des Klartexts** | Ein ungeschützter Hash über eine möglicherweise niedrigentropie Nutzlast ist angreifbar; der gezielte HMAC ist es nicht. |
| **Idempotenzschlüssel als TTL-Tabelle** | Eine zweite Wahrheit neben dem Ereignis. Die `UNIQUE`-Constraint auf dem Ereignis ist die einfachere und stärkere Form. |

## Status

**Angenommen** — bei Gate 1 am 2026-10-21 bestätigt.
