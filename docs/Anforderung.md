# Anforderung: EchoBox — Ereignis-Briefkasten mit Zustellgarantie

> **Status:** Eingang für den AI-SDLC-Lauf. Das ist die **Ausgangsbeschreibung**, nicht
> das fertige PRD — Phase 1 erzeugt daraus PRD, User Stories, Features und die
> Traceability-Matrix.
>
> **Warum dieser Dienst:** Er ist bewusst klein genug, um die Pipeline in **einem**
> Durchlauf zu Ende zu bringen (PromptGuard brauchte drei Rücksprünge und 97
> Anforderungen), aber technisch anstrengend genug, um die interessanten Stellen des
> Ablaufs zu treffen — Asynchronität, Zustandsautomat, Kryptografie, Mandantentrennung,
> ein externes System und ein echtes Frontend.

## 1. Worum es geht

Unsere interne Plattform produziert Ereignisse (Bestellung geändert, Freigabe erteilt,
Scan abgeschlossen). Die abonnierenden Systeme — Warenwirtschaft, Rechnungswesen, ein
externes Partnerportal — wollen diese Ereignisse **zuverlässig** bekommen, ohne dass
jedes Abonnenten-Team sich selbst eine Zustelllogik bauen muss.

EchoBox nimmt Ereignisse entgegen und stellt sie an registrierte Empfänger-URLs weiter.
Der Kern ist die **Zustellgarantie**: Was einmal eingeliefert wurde, wird zugestellt —
und wenn es nicht klappt, weiß der Absender genau, warum.

Der Dienst ist **kein** Message-Broker und **kein** Event-Store. Er ist ein
Zustell-Wrapper: eingehende Ereignisse, ausgehende Webhooks, ein sichtbares
Zustellprotokoll.

## 2. Nutzer und Rollen

| Rolle | Wer | Was er darf |
|---|---|---|
| **Entwickler** | Team, das das Ereignis erzeugt | Ereignisse einliefern, Zustellprotokoll einsehen, totes Endpunkt-Ziel neu auslösen |
| **Empfänger-Admin** | Team des abonnierenden Systems | Endpunkte registrieren, pausieren, Secret rotieren |
| **Service-Admin** | Plattform-Betrieb | Endpunkte aller Mandanten sehen, Totgeburtsliste abholen, Aufbewahrung erzwingen |

Alle Daten sind **mandantenbegrenzt**: ein Team sieht nur seine eigenen Endpunkte und
Ereignisse. Mandant ist Teil der Identität, nicht ein Filterparameter.

## 3. Fachbegriffe

- **Endpunkt** — eine Registrierung: Ziel-URL,_secret, gewünschte Ereignistypen, Zustand.
- **Ereignis** — eine eingelieferte Nutzlast mit Typ, Zeitpunkt und Idempotenzschlüssel.
- **Lieferung** — der Versuch, ein Ereignis an einen Endpunkt zu senden, mit Protokoll.
- **Zustellung** — die Sendung an den Empfänger (Zustand, Versuche, Antwort).
- **Tote Zustellung** — eine Zustellung, die nach erschöpften Versuchen aufgegeben wurde.

## 4. Die fachliche Geschichte

Ein Warenwirtschafts-Team registriert bei uns eine URL und ein Secret. Unser
Bestellservice liefert danach Ereignisse. EchoBox nimmt sie an, versieht sie mit einer
Signatur, schickt sie an die URL und behält den Verlauf. Wenn die URL mal nicht
erreichbar ist, wird später erneut versucht. Wenn der Empfänger unsere Signatur nicht
prüfen kann, ist das sein Problem — wir dokumentieren es, aber wir liefern das Format.

Ein Problem, das wir heute haben: Empfänger bekommen dasselbe Ereignis zweimal, weil
ihr Bestellservice bei Timeouts unklarerweise nochmal sendet. Und wir wissen nicht, ob ein
Ereignis angekommen ist oder nur gesendet wurde. Beides soll EchoBox klären.

## 5. Fachliche Anforderungen

### Endpunkte

- **FR-001** Ein Empfänger-Admin registriert einen Endpunkt mit Ziel-URL, Secret und
  gewünschten Ereignistypen. Die URL muss `https` sein.
- **FR-002** Ein Endpunkt kann pausiert und wieder aktiviert werden. Ein pausierter
  Endpunkt erhält keine Zustellungen; die Ereignisse werden **nicht** zwischengespeichert
  und **nicht** nachgeliefert.
- **FR-003** Das Secret eines Endpunkts kann rotiert werden. Nach der Rotation gilt nur
  das neue Secret; alte Signaturen werden nicht mehr akzeptiert.
- **FR-004** Der Service-Admin sieht alle Endpunkte aller Mandanten, ein
  Empfänger-Admin nur die eigenen.

### Eingang

- **FR-010** Ein Entwickler liefert ein Ereignis ein: Typ, Nutzlast, Idempotenzschlüssel.
- **FR-011** Derselbe Idempotenzschlüssel liefert **kein** zweites Ereignis. Die
  Wiederholung ist erfolgreich und liefert die Kennung des ersten Ereignisses.
- **FR-012** Die Nutzlast ist **verschlüsselt** gespeichert und wird nur für den
  Zustellvorgang entschlüsselt. Im Ruhezustand ist sie nicht im Klartext lesbar —
  weder in der Datenbank noch in einem Log.
- **FR-013** Ereignisse können optional verschlüsselt eingeliefert werden. Dann
  entfällt das Entschlüsseln vor der Zustellung.

### Zustellung

- **FR-020** Jede Zustellung trägt eine Signatur über Zeitstempel und Nutzlast, damit
  der Empfänger die Echtheit prüfen kann. Das Verfahren und der Header-Name sind
  Teil der öffentlichen Schnittstelle und dürfen sich nicht mehr ändern.
- **FR-021** Die Zustellung ist **at-least-once**: ein Ereignis kann mehrfach ankommen.
  Jede Zustellung trägt eine eigene Zustell-Kennung, damit der Empfänger doppelte
  Zustellungen erkennen kann.
- **FR-022** Eine fehlgeschlagene Zustellung wird erneut versucht — mit wachsendem
  Abstand zwischen den Versuchen. Der Abstand soll nicht bei allen Endpunkten
  gleichzeitig liegen (Welle), also mit Streuung.
- **FR-023** Nach einer festen Anzahl erfolgloser Versuche wird die Zustellung
  aufgegeben und landet in der toten Zustellungsliste.
- **FR-024** Ein Empfänger-Admin kann ein aufgegebenes Ereignis erneut auslösen. Die
  erneute Zustellung beginnt mit der Versuchszahl neu.
- **FR-025** Der Empfänger kann antworten, dass er das Ereignis dauerhaft nicht
  möchte (z. B. ungültige Typen). Dann wird die Zustellung sofort aufgegeben, ohne
  weitere Versuche.

### Protokoll und Betrieb

- **FR-030** Das Zustellprotokoll zeigt je Zustellung: Zustand, Anzahl Versuche,
  Zeitpunkt des letzten Versuchs, Antwortcode des Empfängers, nächsten Versuchszeitpunkt.
- **FR-031** Der Service-Admin kann die toten Zustellungen als Liste abholen.
- **FR-032** Für die Betriebsüberwachung werden je Endpunkt Erfolgsquote und
  Zustelllatenz ausgewiesen.
- **FR-033** Ein Endpunkt ist gegen zu viele Zustellungen in kurzer Zeit geschützt.
  Die zulässige Rate ist **noch festzulegen**.

## 6. Nicht-funktionale Anforderungen

- **NFR-001** Die Nutzlast eines Ereignisses ist auf **1 MB** begrenzt.
- **NFR-002** Das Einliefern eines Ereignisses antwortet in unter **200 ms** (p95) — die
  Zustellung ist asynchron und darf die Antwort nicht verzögern.
- **NFR-003** Mandantentrennung ist auf Datenbankebene erzwungen, nicht nur in der
  Anwendung. Ein Fehler in der Anwendung darf kein fremdes Ereignis zeigen.
- **NFR-004** Verschlüsselung im Ruhezustand mit einem anerkannten Verfahren und
  Schlüssel außerhalb der Datenbank.
- **NFR-005** Die Oberfläche ist bedienbar ohne Maus und mit Screenreader; Kontraste
  mindestens nach WCAG 2.1 AA.
- **NFR-006** Der Dienst läuft in Containern, ist auf eine Instanz je Mandanten
  vorbereitet und skaliert horizontal.

## 7. Technische Rahmenbedingungen

Aus dem Kundenkontakt heraus, als Randbedingung, nicht als Festlegung:

- **Backend:** Java 21, Spring Boot, PostgreSQL.
- **Frontend:** React, TypeScript.
- **Zustellung** erfolgt aus einem Hintergrundprozess, nicht aus dem Request-Thread.
- **Betrieb** über Container; die Pipeline baut das Image und stellt Staging und Produktion
  getrennt bereit.

Die Stack-Entscheidung wird in Phase 2 getroffen und in einem ADR festgehalten.

## 8. Abnahme

Die Abnahme gilt als bestanden, wenn:

1. Ein Endpunkt registriert, pausiert und rotiert werden kann und die Mandantengrenze
   nachweislich hält.
2. Ein Ereignis eingeliefert, signiert, zugestellt und bei Fehlschlag wiederholt wird.
3. Ein totes Ereignis wieder ausgelöst werden kann.
4. Die Verschlüsselung im Ruhezustand nachweisbar ist (Datenbank und Log).
5. Das Frontend Endpunkte, Protokoll und tote Zustellungen bedienbar darstellt.
6. **Kein** Akzeptanzkriterium ohne Testfall existiert und die Traceability-Matrix weist
   für jede Anforderung Test **und** Code aus.

## 9. Offene Punkte

Diese Punkte sind bewusst offen — die Phase 1 bzw. das Grill-Me müssen sie klären:

- **F-1 (Rate-Limit)** — „angemessen" ist keine Zahl. Wie viele Zustellungen pro Sekunde
  je Endpunkt, und wird nach Erschöpfung blockiert oder verzögert?
- **F-2 (Aufbewahrung)** — 30 Tage: für Ereignisse, für Zustellungen, für beides? Und
  beginnt die Frist mit dem Einliefern oder mit dem letzten Versuch?
- **F-3 (Replay-Berechtigung)** — wer darf ein totes Ereignis erneut auslösen: der
  Empfänger-Admin, der Entwickler, der Service-Admin?
- **F-4 (Verschlüsselung im Ruhezustand)** — „nach Möglichkeit": Ist das ein Muss? Falls
  ja, ist die Suche im Zustellprotokoll dann noch möglich?
- **F-5 (Auftragsverarbeitungsvertrag)** — wir verarbeiten fremde Ereignis-Nutzlasten
  und geben sie weiter. Wer ist Vertragspartner, wer ist Verantwortlicher, und brauchen
  wir eine Vereinbarung, bevor das live geht?

---

## Was dieser Dienst am Ablauf prüft

| Ablauf-Stelle | Was EchoBox auslöst |
|---|---|
| Phase 1 (Anforderungen) | 5 offene Punkte → das Grill-Me hat echte Arbeit; 13 Anforderungen statt 97 |
| Phase 2 (Architektur) | Vertrag mit Fehlerformat, Zustandsautomat, RLS-Schema, 2–3 ADRs |
| Phase 3/4 (Simplify, Stresstest) | wenige Artefakte, aber echte Befunde (z. B. F-4 vs. Protokollsuche) |
| Phase 5 (TDD) | 7 Stories × 3–4 Akzeptanzkriterien = ein machbarer Testumfang |
| Phase 6 (Implementierung) | **zwei Streams**: Java-Backend und React-Frontend, Synchronisation über den Vertrag |
| Phase 7 (Refactoring) | findet die defensiven Stellen im Hintergrundprozess |
| Phase 8 (Security) | Kryptografie, Mandantentrennung, Signatur, Fremddaten — echte Befundlage |
| Phase 9 (Deployment) | Container, Staging, Rollback |

**Erwartetes Ergebnis:** ein vollständiger Durchlauf ohne Rücksprung, mit **einem**
Commit pro Schritt. Wenn doch ein Rücksprung passiert, ist es einer — und dann ist er
begründet, weil der Vertrag zu früh eingefroren war.
