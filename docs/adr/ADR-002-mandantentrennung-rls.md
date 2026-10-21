# ADR-002: Mandantentrennung durch Row-Level-Security

## Kontext

NFR-003 verlangt: *„Die Mandantentrennung wird auf Datenbankebene erzwungen,
nicht nur in der Anwendung. Ein Fehler in der Anwendung darf kein fremdes
Ereignis zeigen."*

Das Risiko R-02 beschreibt, wo der Fehler typischerweise entsteht: nicht im
Filter des Controllers, sondern dort, wo ein Entwickler einmal den `WHERE`-Zweig
vergisst. Eine reine Anwendungskontrolle schützt gegen **Absicht**, nicht gegen
**Versehen**.

Gleichzeitig muss der Zustell-Worker über Mandanten hinweg arbeiten: er nimmt
fällige Zustellläufe aller Mandanten an. Ihm die Datenbankrolle mit
`BYPASSRLS` zu geben, würde die Trennung für eine ganze Laufzeitrolle aufheben.

## Entscheidung

1. **Jede** fachliche Tabelle des Schemas `app` trägt `organisation_id` und steht
   unter Row-Level-Security (`ENABLE ROW LEVEL SECURITY`). Es gibt **keine**
   fachliche Tabelle ohne RLS.
2. Die Policy vergleicht `organisation_id` mit
   `current_setting('app.organisation_id', true)`. Ist die Variable **nicht**
   gesetzt, liefert `current_setting(…, true)` `NULL` → die Policy liefert
   **keine** Zeile. **Fail-closed.**
3. Der Mandant wird **zu Beginn jeder Transaktion** aus dem Auftragssatz
   gesetzt: `SELECT app.set_tenant('m-1')`. Die Funktion setzt die Variable
   transaktionslokal (`set_config(…, is_local = true)`); nach `COMMIT` ist sie
   zurückgesetzt. Ein Parameter aus dem Request-Body wird **nicht** übernommen.
4. **Keine Rolle hat `BYPASSRLS`.** Der Eigner `echobox_owner` ist `NOLOGIN` und
   wird nur für Migrationen verwendet. `FORCE ROW LEVEL SECURITY` wird bewusst
   **nicht** gesetzt — der Eigner braucht die schmalen `SECURITY DEFINER`-Funktionen.
5. Der Worker ermittelt seine Mandaten über die schmale Funktion
   `app.tenant_ids_for_worker()`, die **nur** Mandantenkennungen liefert — keine
   Nutzlast, keine Mandantendaten. Danach arbeitet der Worker **mandantenweise**
   unter normaler RLS.
6. Im Vertrag wird ein mitgelieferter Mandantenparameter mit **HTTP 422**
   abgelehnt (D-01); fremde Ressourcen werden mit **HTTP 404** beantwortet (D-02),
   damit die Existenz fremder Objekte nicht preisgegeben wird.

## Konsequenzen

- **Positiv:** Ein vergessener `WHERE`-Zweig liefert 0 statt fremde Zeilen. Der
  Nachweis ist gegen eine echte PostgreSQL-Instanz führbar (US-022).
- **Positiv:** Der Worker braucht keinen Sonderweg; es gibt genau **eine**
  Zugriffsregel für alle Laufzeitrollen.
- **Negativ:** Jede Transaktion muss den Kontext setzen. Das ist der Preis der
  Trennung und wird durch einen Spring-Interceptor (`TenantFilter`) und einen
  `TenantConnectionInitializer` automatisiert — der Kontext wird **vor** dem
  ersten Repository-Zugriff gesetzt.
- **Negativ:** Der Worker muss Mandanten durchlaufen, statt alle Zeilen in
  einem Lauf zu sehen. Der Aufwand ist gering (die Mandatenliste ist klein);
  der Gewinn ist, dass es keine Rolle mit Sonderrechten gibt.
- **Grenze (ehrlich benannt):** RLS schützt vor **Anwendungsfehlern**, nicht vor
  einem kompromittierten Anwendungsprozess mit Datenbankzugriff. Wer die
  Anwendungsrolle kompromittiert, kann `app.set_tenant('m-2')` aufrufen. Das ist
  die Standardgrenze von PostgreSQL-RLS und wird hier nicht kaschiert: die
  Schutzbedrohung ist der **Fehler**, nicht der Angreifer mit Datenbankzugang.

## Alternativen

| Alternative | Warum nicht |
|---|---|
| **Mandantenfilter nur in der Anwendung** | Genau der Fall, den NFR-003 ausschließt. |
| **Mandant als Spalte + View** | Eine View ohne RLS schützt genauso wenig; die Sperre gehört an die Tabelle. |
| **`BYPASSRLS` für den Worker** | Hebt die Trennung für eine ganze Laufzeitrolle auf. Die schmale `SECURITY DEFINER`-Funktion löst es ohne Sonderrecht. |
| **Separate Datenbank je Mandant** | Technisch stärker, aber operationell unverhältnismäßig (Backup, Migration, Connection-Pool je Mandant) und nicht gefordert — NFR-006 verlangt nur, **vorbereitet** zu sein (ADR-013). |
| **`FORCE ROW LEVEL SECURITY`** | Würde die `SECURITY DEFINER`-Funktionen des Eigners ebenfalls einschränken — der Eigner braucht sie für die Wartung. |
| **Trennschema je Mandant** | Skaliert nicht und vervielfacht den Wartungsaufwand. |

## Verifikation

Gegen PostgreSQL 16 geprüft:

| Prüfung | Ergebnis |
|---|---|
| Abfrage **ohne** `set_tenant` | 0 Zeilen (fail-closed) |
| `set_tenant('m-1')` | nur die Zeilen von `m-1` |
| nach `COMMIT` | Kontext zurückgesetzt, wieder 0 Zeilen |
| gleicher Idempotenzschlüssel in `m-1` und `m-2` | je ein eigenes Ereignis (Gültigkeitsbereich je Mandant) |
| `app.tenant_ids_for_worker()` | liefert nur die Mandantenkennungen |
| Worker ohne Kontext | 0 Zeilen |

## Status

**Angenommen** — bei Gate 1 am 2026-10-21 bestätigt.
