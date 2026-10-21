# ADR-003: Verschlüsselung im Ruhezustand und Schlüsselverwaltung

## Kontext

FR-012: *„Die Nutzlast wird verschlüsselt gespeichert und nur für den
Zustellvorgang entschlüsselt. Im Ruhezustand ist sie nicht im Klartext
lesbar — weder in der Datenbank noch in einem Log."*
NFR-004: *„Verschlüsselung im Ruhezustand mit einem anerkannten Verfahren; der
Schlüssel liegt außerhalb der Datenbank."*

O-04 stellt zwei Fragen: **Muss** die Verschlüsselung? Und kann man danach im
Zustellprotokoll noch suchen? Die zweite Frage ist eine Produktfrage und
gehört dem Fachbereich. Die erste Frage beantworten die nummerierten Anforderungen
selbst: FR-012 und NFR-004 sind **Muss** — Abschnitt 13 formuliert sie nur
als offene *Zusatz*frage zur Durchsuchbarkeit.

**Diese ADR entscheidet die Architekturseite von O-04** — Verfahren,
Schlüsselverwaltung, Entschlüsselungspunkt, Durchsuchbarkeit. Die fachliche
Frage (Muss? AVV?) bleibt beim Fachbereich.

Aus dem Risikoregister R-03: ohne konkretes Verfahren ist der Nachweis
„im Klartext nicht lesbar" (Abnahmekriterium 4 der Anforderung) nicht
führbar.

## Entscheidung

### 1. Verfahren

| Aspekt | Festlegung |
|---|---|
| Algorithmus | **AES-256-GCM** (AEAD) — anerkanntes Verfahren, in Java über `javax.crypto` verfügbar, hardwarebeschleunigt (AES-NI) |
| Nonce | 12 Byte, **pro Verschlüsselung zufällig**, im Klartext neben der Chiffre gespeichert (`payload_nonce`) |
| Authentikationstag | 128 Bit, Teil der Chiffre — erkennt Manipulation an der Chiffre |
| Schlüssellänge | 256 Bit; je Mandant ein eigener **Datenverschlüsselungsschlüssel** (DEK), verschlüsselt mit einem **Schlüsselverwaltungsschlüssel** (KEK) aus dem Secret-Store |
| Schlüsselversion | `payload_key_version` je Zeile — Schlüsselrotation ohne Datenmigration |

### 2. Schlüsselverwaltung

- Der **Schlüssel liegt außerhalb der Datenbank** (NFR-004). Quelle ist ein
  **Secret-Store**, aus dem die Schlüssel als Umgebungsvariablen bzw. gemountete
  Dateien in den Container injiziert werden.
- Im **Datenbankschema**, im **Container-Image** und in der **Konfiguration**
  steht **kein** Schlüssel (AC-007-4). `.env` ist in `.gitignore`, der
  Secret-Scan läuft in der CI.
- Schlüsselrotation: neue Version erzeugen, alte Version eine Übergangsfrist
  weiterführen, dann auslaufen lassen. Die `key_version` je Zeile macht das
  ohne Umschreiben möglich.

### 3. Was wird verschlüsselt, wann wird entschlüsselt

| Objekt | Gespeichert als | Entschlüsselt |
|---|---|---|
| Ereignisnutzlast | `events.payload_ciphertext` (+ `payload_nonce`) | **nur** im Zustell-Worker unmittelbar vor dem Versand |
| Endpunkt-Secret | `endpoints.secret_ciphertext` (+ `secret_nonce`) | **nur** beim Signieren |

- Der Klartext der Nutzlast wird **nie** protokolliert (AC-007-2) und erscheint
  in **keiner** Fehlermeldung.
- **Vorverschlüsselte Einlieferung** (FR-013): die Bytes werden unverändert
  gespeichert und unverändert zugestellt; es findet **kein** Entschlüsseln
  statt. Die Signatur wird über die **Chiffre** gebildet — der Empfänger prüft
  sie über die empfangenen Bytes und entschlüsselt danach selbst.

### 4. Durchsuchbarkeit (die Architekturseite von O-04)

Die Protokollsuche läuft **ausschließlich über Metadaten** (Ereignis-ID,
Ereignistyp, Endpunkt, Status, Zeitraum). Eine Volltextsuche über Nutzlasten
wird **nicht** gebaut:

- Eine Suche über den Chiffretext ist unmöglich.
- Eine zusätzliche Klartext-Indexierung (Blind-Index) würde die Zusage
  „im Ruhezustand nicht im Klartext lesbar" aufweichen und wäre zudem ein
  zweiter, angreifbarer Ort für die Daten.

### 5. Idempotenzvergleich ohne Klartext

Um „gleicher Idempotenzschlüssel, **andere** Nutzlast" zu erkennen (HTTP 409,
D-05), wird über den Klartext ein **gezielter HMAC-SHA256** gebildet
(`events.payload_fingerprint`, 32 Byte) — mit einem vom Verschlüsselungsschlüssel
abgeleiteten Teilschlüssel. Er ist:

- kein Klartext und kein Index,
- nicht aus dem Chiffretext ableitbar,
- nur zum Vergleich geeignet.

Der Vergleich passiert **vor** der Persistenz; gespeichert wird nur der
Fingerprint.

## Konsequenzen

- **Positiv:** Der Nachweis aus Abnahmekriterium 4 wird führbar: in Tabellen,
  Indizes, Journal und Logs findet sich die Klartextmarkierung nicht.
- **Positiv:** Die Datenbank bleibt auch bei einem Datenbankleck für den
  Angreifer wertlos, solange der Schlüssel außerhalb steht.
- **Positiv:** Schlüsselrotation ist ohne Datenmigration möglich.
- **Negativ:** Jede Zustellung entschlüsselt — ein Schlüssel im Arbeitsspeicher
  des Workers. Der Worker ist deshalb Trust-Grenze und läuft ohne weitere
  Mandantenrechte.
- **Negativ:** Kein Inhaltsfilter im Protokoll. Das ist der Preis der
  Verschlüsselung und ausdrücklich so entschieden (D-11, ADR-017).
- **Negativ:** Der Idempotenz-Fingerprint braucht die kanonische
  Serialisierung der Nutzlast (RFC-8785-artig), damit derselbe fachliche
  Inhalt denselben Fingerprint ergibt.

## Vorschlag zur Freigabe bei Gate 1

Der Fachbereich entscheidet die **fachliche** Seite von O-04:

> **Vorschlag:** Die Verschlüsselung im Ruhezustand bleibt **Muss** — FR-012
> und NFR-004 sind nummerierte Anforderungen, und das Abnahmekriterium 4 der
> Anforderung verlangt den Nachweis. Die Protokollsuche bleibt auf Metadaten
> beschränkt; eine Volltextsuche über Nutzlasten wird nicht gebaut.
> *Warum das der Fachbereich entscheidet:* Die Aussage „ist das ein Muss?"
> berührt die Datenschutzzusage gegenüber den Mandanten; die Frage nach der
> Durchsuchbarkeit ist eine Produktentscheidung mit Kosten (Klartextindex vs.
> kein Index). Die Architektur hat beides so entworfen, dass **kein** Wert
> verdrahtet werden musste: Die Frist der Schlüsselrotation, die
> Aufbewahrung und der Umgang mit vorverschlüsselten Nutzlasten sind
> konfigurierbar.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Keine Verschlüsselung** (O-04 als offene Frage) | FR-012 und NFR-004 sind nummerierte Anforderungen; das Abnahmekriterium „nachweisbar" wäre nicht erfüllbar. |
| **AES-CBC ohne Authentikation** | Kein AEAD — Manipulation an der Chiffre bliebe unentdeckt. |
| **Verschlüsselung auf Anwendungsebene ohne Schlüsselversion** | Schlüsselrotation würde eine Datenmigration erfordern. |
| **Blind-Index für die Suche** | Schwächt „im Klartext nicht lesbar" auf und schafft einen zweiten Angriffspunkt. |
| **Speicherung im Klartext, Verschlüsselung nur bei Transport** | Widerspricht FR-012 ausdrücklich. |
| **Schlüssel in der Datenbank (verpackt)** | Widerspricht NFR-004 und dem Grundsatz, dass ein Datenbankleck alles offenlegt. |

## Status

**Angenommen** — Bestätigung bei Gate 1 durch den Menschen.
Die fachliche Seite von O-04 bleibt offen und ist bei Gate 1 mit dem
Fachbereich zu klären.
