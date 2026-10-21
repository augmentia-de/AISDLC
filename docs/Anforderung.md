# Anforderung: Ereignis-Briefkasten mit Zustellgarantie

**Von:** Plattform-Team Interne Systeme
**An:** Team AI-SDLC
**Datum:** 2026-10-21
**Fachlicher Ansprechpartner:** Lena Hofmann (Platform Engineering)

---

## 1. Ausgangslage

Wir produzieren Ereignisse — Bestellung geändert, Freigabe erteilt, Scan
abgeschlossen, Benutzer angelegt. Bis heute bekommen die abonnierenden Systeme diese
Ereignisse auf zwei Wegen: über einen gemeinsamen Message-Broker, den sich jeder
Selbst gebaut, oder über einen nächtlichen Batch.

Beides hat in den letzten zwei Quartalen wiederholt Ärger gemacht:

- **Der Batch verliert Ereignisse.** Ein Ausfall des Batch-Jobs um 02:00 heißt: die
  Daten des Vortags fehlen, und niemand merkt es, weil der Batch still fehlschlägt.
- **Der Broker kennt keine Zustellgarantie.** Wir wissen nicht, ob ein abonnierendes
  Team die Nachricht tatsächlich verarbeitet hat — nur, dass sie im Broker lag.
- **Jedes Team baut sich die Wiederholungslogik selbst.** Drei Teams haben inzwischen
  drei Varianten von Retry, Backoff und „was tun wir bei 500" gebaut. Eine davon
  überschlägt nach elf Fehlversuchen still Fehler.
- **Doppelte Zustellungen.** Unser Bestellservice sendet bei einem Timeout unklarerweise
  nochmal. Weil wir den Status nicht kennen, bauen die Empfänger eigene
  Duplikaterkennungen — jede für sich.

Kurz: die Zustellung liegt bei den Empfängern, aber die Verantwortung für die
Zuverlässigkeit liegt bei uns.

## 2. Worum es geht

Wir wollen einen zentralen Dienst, der Ereignisse **entgegennimmt** und sie an
registrierte Empfänger-URLs **weiterleitet** — mit Protokoll, mit Wiederholung und mit
einer nachvollziehbaren Antwort auf die Frage „ist das angekommen?".

Der Dienst ist bewusst **kein** Message-Broker und **kein** Event-Store. Er ersetzt
nicht unseren internen Bus. Er ist ein Zustell-Wrapper für ausgehende Webhooks:
eingehende Ereignisse, ausgehende HTTP-Zustellung, sichtbares Protokoll.

Warum kein Broker: wir wollen keine zweite Zustandshaltung, die wir gegen die erste
konsistent halten müssen. Der Dienst hält nur, was er für die Zustellung braucht, und
das so kurz wie möglich.

## 3. Nutzer und Rollen

| Rolle | Wer | Was er darf |
|---|---|---|
| **Entwickler** | Team, das das Ereignis erzeugt | Ereignisse einliefern, Zustellprotokoll einsehen, totes Endpunkt-Ziel neu auslösen |
| **Empfänger-Admin** | Team des abonnierenden Systems | Endpunkte registrieren, pausieren, Secret rotieren |
| **Service-Admin** | Plattform-Betrieb | Endpunkte aller Mandanten sehen, Tote-Zustellungsliste abholen, Aufbewahrung erzwingen |

Alle Daten sind **mandantenbegrenzt**. Ein Team sieht nur seine eigenen Endpunkte und
Ereignisse — in der Oberfläche genauso wie in der Schnittstelle. Der Mandant steckt
in der Identität, er ist kein Filterparameter, den ein Aufrufer frei wählen darf.

## 4. Fachbegriffe

- **Endpunkt** — eine Registrierung: Ziel-URL, Secret, gewünschte Ereignistypen, Zustand.
- **Ereignis** — eine eingelieferte Nutzlast mit Typ, Zeitpunkt und Idempotenzschlüssel.
- **Zustellung** — ein Versuch, ein Ereignis an einen Endpunkt zu senden.
- **Lieferung** — das Protokoll einer Zustellung über alle Versuche.
- **Tote Zustellung** — eine Zustellung, die nach erschöpften Versuchen aufgegeben wurde.

## 5. Die Geschichte, die wir erzählen wollen

Ein Warenwirtschafts-Team registriert bei uns eine URL und ein Secret. Unser
Bestellservice liefert danach Ereignisse. Der Dienst nimmt sie an, versieht sie mit
einer Signatur, schickt sie an die URL und behält den Verlauf.

Ist die URL gerade nicht erreichbar, versucht er es später erneut. Ist der Empfänger
der Meinung, das Ereignis will er nicht (unbekannter Typ zum Beispiel), sagt er uns
das — und wir hören auf. Der Empfänger kann das Ergebnis jeder Zustellung nachlesen,
und wir können im Betrieb sagen, wie hoch die Erfolgsquote je Endpunkt ist.

## 6. Anforderungen an die Endpunkte

- **FR-001** Ein Empfänger-Admin registriert einen Endpunkt mit Ziel-URL, Secret und den
  gewünschten Ereignistypen. Die URL muss `https` sein.
- **FR-002** Ein Endpunkt kann pausiert und wieder aktiviert werden. Ein pausierter
  Endpunkt erhält keine Zustellungen. Die Ereignisse werden **nicht** zwischengespeichert
  und **nicht** nachgeliefert — wer pausiert, entscheidet, dass er nichts mehr bekommt.
- **FR-003** Das Secret eines Endpunkts kann rotiert werden. Nach der Rotation gilt nur
  das neue Secret.
- **FR-004** Der Service-Admin sieht alle Endpunkte aller Mandanten, ein
  Empfänger-Admin nur die eigenen.

## 7. Anforderungen an den Eingang

- **FR-010** Ein Entwickler liefert ein Ereignis ein: Typ, Nutzlast, Idempotenzschlüssel.
- **FR-011** Derselbe Idempotenzschlüssel liefert **kein** zweites Ereignis. Die
  Wiederholung ist erfolgreich und liefert die Kennung des ersten Ereignisses zurück.
- **FR-012** Die Nutzlast wird **verschlüsselt** gespeichert und nur für den
  Zustellvorgang entschlüsselt. Im Ruhezustand ist sie nicht im Klartext lesbar —
  weder in der Datenbank noch in einem Log.
- **FR-013** Ereignisse können optional bereits verschlüsselt eingeliefert werden. Dann
  entfällt das Entschlüsseln vor der Zustellung.

## 8. Anforderungen an die Zustellung

- **FR-020** Jede Zustellung trägt eine Signatur über Zeitstempel und Nutzlast, damit
  der Empfänger die Echtheit prüfen kann. Verfahren und Header-Name sind Teil der
  öffentlichen Schnittstelle und dürfen sich danach nicht mehr ändern — sonst brechen
  alle bestehenden Empfänger.
- **FR-021** Die Zustellung ist **at-least-once**: ein Ereignis kann mehrfach
  ankommen. Jede Zustellung trägt eine eigene Zustell-Kennung, damit der Empfänger
  doppelte Zustellungen erkennen kann.
- **FR-022** Eine fehlgeschlagene Zustellung wird erneut versucht, mit wachsendem
  Abstand zwischen den Versuchen. Der Abstand soll nicht bei allen Endpunkten
  gleichzeitig liegen, also mit Streuung.
- **FR-023** Nach einer festen Anzahl erfolgloser Versuche wird die Zustellung
  aufgegeben und landet in der Liste der toten Zustellungen.
- **FR-024** Ein Empfänger-Admin kann ein aufgegebenes Ereignis erneut auslösen. Die
  erneute Zustellung beginnt mit der Versuchszahl neu.
- **FR-025** Der Empfänger kann in der Antwort angeben, dass er das Ereignis dauerhaft
  nicht möchte (etwa weil der Ereignistyp für ihn irrelevant ist). Dann wird die
  Zustellung sofort aufgegeben, ohne weitere Versuche.

## 9. Anforderungen an Protokoll und Betrieb

- **FR-030** Das Zustellprotokoll zeigt je Zustellung: Zustand, Anzahl Versuche,
  Zeitpunkt des letzten Versuchs, Antwortcode des Empfängers, nächsten Versuchszeitpunkt.
- **FR-031** Der Service-Admin kann die toten Zustellungen als Liste abholen.
- **FR-032** Für die Betriebsüberwachung werden je Endpunkt Erfolgsquote und
  Zustelllatenz ausgewiesen.
- **FR-033** Ein Endpunkt ist gegen zu viele Zustellungen in kurzer Zeit geschützt. Die
  zulässige Rate steht noch nicht fest.

## 10. Nicht-funktionale Anforderungen

- **NFR-001** Die Nutzlast eines Ereignisses ist auf **1 MB** begrenzt.
- **NFR-002** Das Einliefern eines Ereignisses antwortet in unter **200 ms** (p95). Die
  Zustellung ist asynchron und darf die Antwort nicht verzögern.
- **NFR-003** Die Mandantentrennung wird auf Datenbankebene erzwiesen, nicht nur in der
  Anwendung. Ein Fehler in der Anwendung darf kein fremdes Ereignis zeigen.
- **NFR-004** Verschlüsselung im Ruhezustand mit einem anerkannten Verfahren; der
  Schlüssel liegt außerhalb der Datenbank.
- **NFR-005** Die Oberfläche ist ohne Maus bedienbar und mit Screenreader nutzbar; die
  Kontraste erfüllen mindestens WCAG 2.1 AA.
- **NFR-006** Der Dienst läuft in Containern, ist auf eine Instanz je Mandanten
  vorbereitet und skaliert horizontal.

## 11. Rahmenbedingungen aus der Technik

Das sind Randbedingungen aus der Abstimmung mit unserem Plattform-Team, **keine**
Festlegungen des Fachbereichs:

- **Backend:** Java 21, Spring Boot, PostgreSQL.
- **Frontend:** React, TypeScript.
- Die **Zustellung** erfolgt aus einem Hintergrundprozess, nicht aus dem
  Request-Thread. Ein Empfänger, der 30 Sekunden braucht, darf den Annahme-Request
  nicht blockieren.
- **Betrieb** über Container; die Pipeline baut das Image und stellt Staging und
  Produktion getrennt bereit.

## 12. Woran wir erkennen, dass es fertig ist

1. Ein Empfänger kann einen Endpunkt registrieren, pausieren und sein Secret rotieren,
   ohne dass ein Mandant fremde Daten sieht.
2. Ein Ereignis wird angenommen, signiert, zugestellt; scheitert die Zustellung, wird
   erneut versucht, und die Wiederholung ist im Protokoll sichtbar.
3. Ein totes Ereignis lässt sich erneut auslösen.
4. Die Verschlüsselung im Ruhezustand ist nachweisbar — in der Datenbank und in den
   Logs.
5. Die Oberfläche zeigt Endpunkte, Zustellprotokoll und tote Zustellungen und ist mit
   Tastatur und Screenreader bedienbar.
6. Zu jeder Anforderung gibt es einen Testfall, und die Nachverfolgung weist Anforderung,
   Test und Code aus.

## 13. Was wir noch nicht entschieden haben

Diese Fragen sind bewusst offen — sie gehören in die Abstimmung, nicht in eine
Vorgabe:

- **Rate-Limit je Endpunkt.** „Angemessen" ist bisher keine Zahl. Und was passiert,
  wenn das Limit erreicht ist: blockieren wir die Zustellung, oder verschieben wir sie?
- **Aufbewahrung.** 30 Tage — für Ereignisse, für Zustellungen oder für beides? Und
  beginnt die Frist mit dem Einliefern oder mit dem letzten Versuch?
- **Wer darf ein totes Ereignis erneut auslösen?** Der Empfänger-Admin, der
  Entwickler oder wir?
- **Verschlüsselung im Ruhezustand.** „Nach Möglichkeit" — ist das ein Muss? Falls ja:
  können wir dann im Zustellprotokoll überhaupt noch nach einem Ereignis suchen?
- **Vertrag über die Auftragsverarbeitung.** Wir verarbeiten fremde Ereignis-Nutzlasten
  und geben sie an Dritte weiter. Wer ist Vertragspartner, wer ist Verantwortlicher,
  und brauchen wir eine Vereinbarung, bevor das live geht?
