# ADR-009: Ablehnungssignal des Empfängers

## Kontext

FR-025: *„Der Empfänger kann in der Antwort angeben, dass er das Ereignis
dauerhaft nicht möchte (etwa weil der Ereignistyp für ihn irrelevant ist). Dann
wird die Zustellung sofort aufgegeben, ohne weitere Versuche."*

Die Anforderung nennt **kein** Signal. Ohne ein benanntes Signal ist FR-025
nicht implementierbar — der Empfänger wüsste nicht, was er antworten muss.

D-08 legt die Wiederholungsregel fest: *„Das dokumentierte Ablehnungssignal
(Vorschlag: HTTP 410) und alle übrigen 4xx bedeuten: sofort aufgeben, Status
‚abgelehnt'.“*

AC-014-2 verlangt: das Signal ist im öffentlichen Vertrag **beschrieben**, und
ein Vertragstest schlägt fehl, wenn es fehlt. AC-014-4: ein 4xx **ohne** das
dokumentierte Signal führt ebenfalls zum Ende, wird aber als `client_error`
protokolliert.

## Entscheidung

### Das Signal

| Element | Festlegung |
|---|---|
| Statuscode | **410 Gone** |
| Header | **`X-EchoBox-Rejection: permanent`** |
| Content-Type | `application/json` |
| Body | `{ "rejection": "permanent", "reason": "<code>", "message": "<optional>", "rejected_event_types": ["<optional>"] }` |

Beispiel:

```json
{
  "rejection": "permanent",
  "reason": "event_type_not_subscribed",
  "message": "Dieser Endpunkt verarbeitet den Ereignistyp order.changed nicht.",
  "rejected_event_types": ["order.changed"]
}
```

### Warum 410 Gone

- **410** ist in HTTP ausdrücklich für *„dauerhaft nicht verfügbar"* /
  *„weg"* reserviert — genau die Semantik „ich will das dauerhaft nicht".
- **404** ist falsch: die URL ist erreichbar, der Inhalt wird abgelehnt.
- **403** ist falsch: es geht nicht um Berechtigung, sondern um den Wunsch.
- **400** ist falsch: die Anfrage war nicht fehlerhaft, nur unerwünscht.
- **204/2xx** ist falsch: der Empfänger hat die Verarbeitung **nicht**
  durchgeführt; ein 2xx würde zu „sent“ führen — der Zustand wäre falsch.
- 410 liegt in der Familie der 4xx, die ohnehin kein Wiederholen auslösen — das
  Signal ändert also **nicht** die Wiederholungsregel, sondern macht die
  Absicht des Empfängers **sichtbar**.

### Warum zusätzlich ein Header

Der Statuscode allein trägt die Information „dauerhaft abgelehnt“ nicht sauber
— jeder andere 4xx bedeutet das gleiche. Der Header macht die **Absicht**
explizit und ist maschinenlesbar, ohne den Body zu parsen. Der Body trägt den
**Grund** (`reason`) und optional die abgelehnten Ereignistypen.

### Warum `rejected` und nicht `abandoned`

Ein `abandoned` bedeutet: *wir haben es mehrfach versucht und es ging nicht*.
Ein `rejected` bedeutet: *der Empfänger hat nein gesagt*. Beides ist
endgültig, aber nur `abandoned` steht in der Totenliste und ist replaybar —
ein vom Empfänger abgelehntes Ereignis **soll** nicht wiederholt werden
(FR-025).

### Der Body-Format-Vertrag ist eingefroren

Das Format wird im Vertrag (`x-delivery-protocol.response.outcomes[410].body`
und `components.schemas.DeliveryRejection`) **festgeschrieben**. Änderungen
sind breaking changes → neue Hauptversion (wie bei der Signatur, ADR-004).

Der Vertragstest schlägt fehl, wenn Statuscode, Headername oder Body-Format
fehlen (AC-014-2).

### Behandlung übriger 4xx

Ein 4xx **ohne** das Signal:
- Der Lauf endet ebenfalls — **keine** Wiederholung (D-08, AC-014-4).
- `rejection_reason` ist dann `client_error` statt `permanent_rejection`.
- Die Antwort wird **nicht** als Ablehnung des Empfängers missverstanden: der
  Unterschied ist im Protokoll sichtbar.

### Behandlung von 3xx — Umleitungen werden nicht befolgt

Eine **3xx**-Antwort (301, 302, 307, 308 …) bedeutet: der Empfänger möchte
woanders hin. Der Dienst sendet **genau einmal** an die registrierte
Ziel-URL.

| Element | Festlegung |
|---|---|
| Wiederholung? | **nein** — ein 3xx ist keine temporäre Störung |
| Status des Laufs | `rejected` |
| `rejection_reason` | `client_error` |
| `outcome` des Versuchs | `client_error` |
| Redirects | werden **nicht** gefolgt (`followRedirects = NEVER`) |

**Warum kein Wiederholen:** Der Empfänger hat die Annahme **abgelehnt** — er
möchte an dieser URL nicht mehr zugestellt werden. Eine Wiederholung würde
denselben 3xx erneut auslösen und den Lauf nur bis zum Zeitablauf verlängern.

**Warum Redirects nicht folgen:** Die Ziel-URL ist Teil der Signatur-Eingabe
(ADR-004, ADR-005). Ein Folgeort würde die signierte URL aufweichen — der
Empfänger könnte die Signatur nicht mehr prüfen, und der Dienst würde an eine
Ziel-URL senden, die der Empfänger-Admin nie registriert hat. Das wäre eine
**Umgehung der Registrierung** durch den Empfänger selbst.

Die Ergebnismatrix des ausgehenden Vertrags
(`x-delivery-protocol.response.outcomes`) führt die 3xx **ausdrücklich** als
Zeile — ohne sie hätte der eingefrorene Fremdvertrag eine Lücke
(E-08, entschieden im Rücksprung aus Schritt 4).

## Konsequenzen

- **Positiv:** Der Empfänger kann einen Ereignistyp gezielt ablehnen, ohne den
  Endpunkt zu pausieren — das ist die leichtgewichtige Form der
  „Empfangsbereitschaft“.
- **Positiv:** Die Absicht ist dauerhaft protokolliert — ein Empfänger, der
  einen Ereignistyp nicht mehr will, sieht das in der Totenliste und im
  Protokoll.
- **Negativ:** Ein Empfänger, der 410 „aus Versehen“ schickt, beendet die
  Zustellung sofort. Das ist die beabsichtigte Semantik — 410 ist eindeutig.
- **Negativ:** Eine **3xx** beendet den Lauf genauso endgültig wie ein 4xx. Der
  Empfänger, der auf eine Umleitung zeigt, bekommt **keine** Zustellung an
  der neuen Adresse — er muss die URL im Endpunkt pflegen.
- **Negativ:** `rejected` ist **nicht** replaybar. Wer ein abgelehntes Ereignis
  doch noch zustellen will, muss den Endpunkt neu konfigurieren — das ist die
  Konsequenz aus „dauerhaft nicht gewünscht“.
- **Negativ:** Der Empfänger muss das Signal **kennen**. Das gehört in die
  Empfänger-Dokumentation und in die Bekanntmachung des Vertrags.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Nur irgendein 4xx** | Dann ist „dauerhaft nicht gewünscht“ nicht von einem Fehler zu unterscheiden — AC-014-2 verlangt ein **dokumentiertes** Signal. |
| **202 + Header als Ablehnung** | 2xx bedeutet Erfolg; die Zustellung wäre falsch als „sent“ protokolliert. |
| **400 Bad Request** | Falsche Semantik: die Anfrage war korrekt, nur unerwünscht. |
| **403 Forbidden** | Falsche Semantik: es geht nicht um Rechte. |
| **404 Not Found** | Falsche Semantik: die URL existiert. |
| **Ablehnung nur als Antwort-Header ohne Sonderstatus** | Dann wäre die Aussage „dauerhaft“ nicht vom Statuscode ableitbar, und ein 200 mit dem Header wäre widersprüchlich. |
| **Eigener Statuscode (z. B. 499)** | Nicht standardisiert; Empfänger müssten ihn kennen. 410 ist die Standardbedeutung. |

## Status

**Angenommen** — das Signal ist im Vertrag `docs/api/openapi.yaml`
**eingefroren**. Bei Gate 1 am 2026-10-21 bestätigt.
