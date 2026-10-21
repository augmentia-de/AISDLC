# ADR-014: Zeitlimit je Zustellversuch

## Kontext

Abschnitt 11 der Anforderung: *„Die Zustellung erfolgt aus einem Hintergrundprozess,
nicht aus dem Request-Thread. Ein Empfänger, der 30 Sekunden braucht, darf den
Annahme-Request nicht blockieren."*

Daraus folgt die Grenze: **ein einzelner Zustellversuch darf nicht länger als
30 Sekunden hängen**, sonst hält ein hängender Empfänger den Worker dauerhaft
belegt.

**Offener Punkt O-07** (Architektur / Gate 1): *„Zeitlimit je Zustellversuch:
Wie lange wartet der Dienst auf einen Empfänger?"*

Das PRD weist O-07 ausdrücklich als **Architekturfrage** aus — im Gegensatz zu
den übrigen offenen Punkten, die dem Fachbereich gehören. Der Auftrag dieses
Schrittes ist es daher, O-07 **technisch zu entscheiden** und zu begründen.

Auswirkungen: AC-011-3 (ein Fehlversuch, der im Hintergrund läuft),
AC-012-1 (Aufgabe nach erschöpften Versuchen), AC-023-4 (Wiederaufnahme nach
Absturz, Versuchszahl steigt nicht sprunghaft).

## Entscheidung

### Verbindungs-Timeout: 5 Sekunden

Deckt DNS-Auflösung, TCP-Handshake und TLS-Handshake ab.

### Lese-Timeout: 10 Sekunden

Deckt die Zeit zwischen „Verbindung steht“ und „Antwort vollständig gelesen“ ab.

### Gesamtgrenze: 15 Sekunden

```
Gesamtgrenze = Verbindungs-Timeout (5 s) + Lese-Timeout (10 s) = 15 s  <  30 s
```

Die 15 Sekunden liegen **unter** der in Abschnitt 11 genannten Grenze von
30 Sekunden — mit Reserve für die Antwortverarbeitung und die
Protokollauswertung.

### Beide Werte sind konfigurierbar

```
echobox.delivery.attempt.connect-timeout = 5s
echobox.delivery.attempt.read-timeout    = 10s
```

Der Wert wird **nirgends** im Code verdrahtet.

### Der Timeout ist ein Fehlversuch

Ein Timeout ist ein **Fehlversuch**, kein Sonderfall: Das Ergebnis ist
`timeout`, der Lauf geht auf `failed`, und es wird ein weiterer Versuch nach
Backoff geplant (FR-022). Der Empfänger hat möglicherweise trotzdem
verarbeitet — das ist der Preis von at-least-once und wird durch die
Zustell-Kennungen erkennbar gemacht (ADR-005).

### Verhältnis zur Lease

Die Lease des Zustelllaufs muss **länger** sein als die Gesamtgrenze plus
Reserve — sonst gäbe es ein Fenster, in dem ein noch laufender Versuch seine
Lease verliert (ADR-006). Deshalb:

```
Lease-Dauer  >  Gesamtgrenze (15 s) + Reserve
```

### Verhältnis zum Backoff

Der erste Backoff-Abstand sollte **größer** sein als die Gesamtgrenze. Sonst
wird ein hängender Empfänger in schneller Folge erneut getroffen — das ist
genau die Last, die FR-033 (Endpunkt-Begrenzung) verhindern soll. Der
Vorschlag in ADR-008 (Basis 1 Minute) erfüllt das.

## Begründung der Werte

| Wert | Begründung |
|---|---|
| **5 s** Verbindungs-Timeout | Deckt einen normalen Handshake in einem europäischen Rechenzentrum mit Reserve ab. Ein Empfänger, der in 5 s keine TCP-Verbindung zustande bringt, ist entweder nicht erreichbar oder überlastet — beides ist ein Fehlversuch, kein Grund zu warten. |
| **10 s** Lese-Timeout | Gibt einem langsamen, aber arbeitenden Empfänger Zeit, die Nutzlast (bis 1 MB) zu verarbeiten und zu antworten. Kurz genug, um den Worker nicht zu blockieren; lang genug, um False-Positives zu vermeiden. |
| **< 30 s** | Die Vorgabe aus Abschnitt 11 wird eingehalten. Die Reserve von 15 s deckt Verbindungsaufbau über Proxy, TLS-Handshake-Nacharbeit, Protokollierung und die Auswertung ab. |
| **konfigurierbar** | Ein Empfänger in einem abgeschotteten Netz mit hohem Latenz-Aufschlag braucht andere Werte. Eine Konstante im Code wäre ein Blocker für solche Mandanten. |

## Konsequenzen

- **Positiv:** Der Worker ist nicht von einem hängenden Empfänger blockiert;
  die Kapazität bleibt berechenbar.
- **Positiv:** Ein Ausfall wird **schnell** erkannt — ein Fehlversuch, der nach
  15 s endet, ist eine Kette von Fehlversuchen in Minuten, nicht in Stunden.
- **Positiv:** Die Größe ist **ableitbar**: Worker-Kapazität =
  `Threads × (3600 s / 15 s)` Versuche je Stunde im ungünstigsten Fall.
- **Negativ:** Ein Empfänger, der regelmäßig mehr als 15 s braucht, wird als
  fehlerhaft behandelt und verliert Zustellungen. Das ist die **korrekte**
  Reaktion — ein Empfänger, der 30 Sekunden braucht, ist für einen Webhook
  zu langsam. Der Wert ist konfigurierbar, falls ein Mandant das anders
  bewertet.
- **Negativ:** Der Timeout ist eine **Faustregel**: bei Netzwerkpartitionen
  können Verbindungen länger „hängen“, als der Timeout vorsieht. Der Timeout
  greift, wenn keine Daten fließen; ein langsam fließender Strom ist durch
  das Lese-Timeout begrenzt, nicht durch die Gesamtdauer. Eine zusätzliche
  **Gesamtdauergrenze** je Versuch ist deshalb sinnvoll und wird als
  Konfigurationswert ergänzt (`attempt.total-timeout`, Default 25 s).

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Ein einziger Timeout (z. B. 30 s)** | Ununterscheidbar, ob der Empfänger nicht erreichbar oder nur langsam ist — beides verdient aber ein anderes Verhalten (DNS-Fehler vs. Lesefehler). |
| **Kein Timeout** | Ein hängender Empfänger blockiert den Worker dauerhaft — der Betrieb verliert die Kontrolle über seine Kapazität. |
| **30 s exakt** | Reibt am Rand der Vorgabe aus Abschnitt 11 und lässt keine Reserve. |
| **Timeouts im Code verdrahtet** | Passt nicht zu Mandanten mit abgeschotteten Netzen; widerspricht zudem der Konfigurationsregel des Projekts. |
| **Timeout als Abbruch des ganzen Laufs** | Ein einzelner langsamer Versuch darf nicht die Kette beenden — dafür ist der Backoff da. |
| **Timeouts im HTTP-Client global konfiguriert** | Der HTTP-Client wird auch für den Identity-Provider verwendet; die Werte des Zustellversuchs gehören in den **Zustellungs**-Adapter, nicht in die globale Client-Konfiguration. |

## Status

**Angenommen** — dies ist eine **Architekturentscheidung** und wurde in
diesem Schritt getroffen. Bestätigung bei Gate 1 durch den Menschen.
