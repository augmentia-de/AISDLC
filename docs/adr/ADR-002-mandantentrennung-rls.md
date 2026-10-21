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
3. Der Mandant wird **innerhalb der Transaktion** gesetzt, die auch liest:
   `SELECT app.set_tenant('m-1')` — **immer vor der ersten Abfrage**, in
   derselben Transaktion. Die Funktion setzt die Variable transaktionslokal
   (`set_config(…, is_local = true)`); nach `COMMIT` ist sie zurückgesetzt. Der
   Kontext ist damit **transaktionsgebunden**, nicht **verbindungsgebunden**:
   kein Autocommit, kein Zugriff auf `app.*` außerhalb einer solchen
   Transaktion, kein `REQUIRES_NEW` nach dem Setzen. Ein Connection-Pool
   ändert daran nichts — ein in einem Servlet-Filter oder beim Anlegen einer
   Pool-Verbindung gesetzter Wert wäre beim ersten Query entweder
   zurückgesetzt oder auf einer **anderen** Connection gesetzt; dann liefert
   `current_setting(…, true)` `NULL` und **jede** Abfrage liefert 0 Zeilen. Ein
   Parameter aus dem Request-Body wird **nicht** übernommen.
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
7. **Ausnahme: die mandantenübergreifende Sicht des Service-Admins.** Die
   Regel „ein Mandant pro Transaktion" gilt für die Regel **nicht**, wo ein
   Mandant nicht reicht: der `service_admin` muss alle Mandanten sehen
   (FR-004, AC-004-2, AC-016-1, AC-016-5). Sie wird über **drei schmale
   Eigentümerfunktionen** im Schema `app` hergestellt — nicht über eine
   Laufzeitrolle mit `BYPASSRLS`, nicht über eine Mandantenliste, nicht über
   ein Sonderrecht:

   | Funktion | Operation | Art |
   |---|---|---|
   | `app.list_endpoints_cross_tenant(...)` | `GET /api/v1/endpoints` | **Lesend** |
   | `app.list_dead_deliveries_cross_tenant(...)` | `GET /api/v1/dead-deliveries` | **Lesend** |
   | `app.enforce_retention_cross_tenant(...)` | `POST /api/v1/retention/enforcement` | **schreibend** (nur `deleted_at` + Purge) |

   Die Regeln:

   - `SECURITY DEFINER`, ausgeführt als `echobox_owner` (NOLOGIN, **kein**
     `BYPASSRLS`; der Eigner umgeht die RLS nur, weil `FORCE ROW LEVEL
     SECURITY` bewusst **nicht** gesetzt ist).
   - `search_path` ist fest verdrahtet (`app, pg_temp`) — kein Aufruf kann eine
     fremde Funktion einschleusen.
   - `EXECUTE` ist **nur** der Applikationsrolle `echobox_app` gegeben
     (`REVOKE … FROM PUBLIC`); der Worker bekommt ihn **nicht**.
   - **Fail-closed:** ohne `app.cross_tenant = 'service_admin'` (transaktionslokal
     gesetzt, **nachdem** die JWT-Rolle geprüft ist) liefern alle drei
     Funktionen **keine** Zeile.
   - Die Funktionen liefern **nur die jeweils benötigte Projektion** — **kein**
     Nutzlastklartext, **keine** Chiffre, **keine** Secrets, **keine** Schlüssel.
   - Die **einzige** schreibende Funktion ist `enforce_retention_cross_tenant`:
     sie schreibt ausschließlich das Aufbewahrungsfeld `deleted_at` und führt im
     Purge die dokumentierte Löschung aus. Sie kann **keine** fachlichen Daten
     anlegen, ändern oder löschen. Es gibt **keinen** allgemeinen
     Schreibzugriff mandantenübergreifend.
   - `organisation_id` ist **immer** gefüllt und benennt den **besitzenden
     Mandanten** der Ressource — im eigenen Mandanten den eigenen, in den
     mandantenübergreifenden Antworten den Mandanten, zu dem der Datensatz
     gehört (V-26). Der Client erkennt die Herkunft damit **immer**, nicht nur
     in der mandantenübergreifenden Sicht.
   - **Fail-closed auch ohne Parameter:** die Totenlisten-Funktion hat
     **keinen** Statusparameter — der Status `abandoned` ist **fest verdrahtet**.
     Mit einem optionalen Statusfilter lieferte die Funktion ohne Wert das
     vollständige Zustellprotokoll **aller** Mandanten (statt der Totenliste):
     ein Datenabfluss aus einer Funktion, die die RLS umgeht (N-14).
   - **Logische Löschung ist der Applikationsrolle nicht möglich:** PostgreSQL
     prüft den `USING`-Ausdruck einer Policy auch gegen die **neue** Zeile. Ein
     `UPDATE … SET deleted_at = now()` durch `echobox_app` endet deshalb mit
     `new row violates row-level security policy`. Markiert wird
     ausschließlich über die Eigentümerfunktion
     `app.enforce_retention_cross_tenant` (N-16). Das ist **Absicht**: die
     logische Löschung ist ein mandantenübergreifender Vorgang (ADR-012) und
     gehört damit in die schmale Eigentümerfunktion, nicht in eine
     Allzweckrolle.

## Konsequenzen

- **Positiv:** Ein vergessener `WHERE`-Zweig liefert 0 statt fremde Zeilen. Der
  Nachweis ist gegen eine echte PostgreSQL-Instanz führbar (US-022).
- **Positiv:** Der Worker braucht keinen Sonderweg; es gibt genau **eine**
  Zugriffsregel für alle Laufzeitrollen.
- **Negativ:** Jede Transaktion muss den Kontext setzen. Das ist der Preis der
  Trennung und wird durch einen Spring-Interceptor (`TenantFilter`) und einen
  `TenantConnectionInitializer` automatisiert — der Kontext wird **innerhalb
  der Transaktion** gesetzt, die auch liest, **vor** der ersten Abfrage. Der
  `TenantFilter` liest **nur** das JWT und füllt einen request-scoped
  `TenantContext` — **ohne** Datenbankzugriff und **ohne** `set_tenant()`. Der
  Name des Initializers ist **historisch** (die Traceability-Matrix aus
  Schritt 1 nennt ihn so); die **Semantik** ist transaktionsgebunden, und sie
  ist hier festgeschrieben.
- **Grenze (ehrlich benannt, Ausnahme):** Die mandantenübergreifende Sicht des
  Service-Admins ist eine **bewusste Ausnahme** von „ein Mandant pro
  Transaktion". Sie ist so eng zugeschnitten, dass sie nur die drei genannten
  Operationen betrifft, und sie ist **kein** allgemeiner Mandantenzugriff: die
  Freigabe an `echobox_app` bedeutet **nicht**, dass `developer` oder
  `receiver_admin` sie benutzen dürfen — die Rollenprüfung bleibt in der
  Anwendung, und ohne die Freigabe `app.cross_tenant` liefern die Funktionen
  nichts.
- **Negativ:** Der Worker muss Mandanten durchlaufen, statt alle Zeilen in
  einem Lauf zu sehen. Der Aufwand ist gering (die Mandatenliste ist klein);
  der Gewinn ist, dass es keine Rolle mit Sonderrechten gibt.
- **Grenze (ehrlich benannt):** RLS schützt vor **Anwendungsfehlern**, nicht vor
  einem kompromittierten Anwendungsprozess mit Datenbankzugriff. Wer die
  Anwendungsrolle kompromittiert, kann `app.set_tenant('m-2')` aufrufen. Das ist
  die Standardgrenze von PostgreSQL-RLS und wird hier nicht kaschiert: die
  Schutzbedrohung ist der **Fehler**, nicht der Angreifer mit Datenbankzugang.
- **Grenze (ehrlich benannt) — die verbleibende Lücke zwischen RLS und
  Anwendung:** dieselbe Applikationsrolle kann auch
  `app.cross_tenant = 'service_admin'` setzen und damit die
  mandantenübergreifende Sicht öffnen — Endpunktliste, Totenliste und
  Aufbewahrung **aller** Mandanten. Das ist die **einzige** Erweiterung
  gegenüber dem Normalfall und die **einzige** Stelle, an der eine
  Fehlkonfiguration mandantenübergreifend sichtbar macht: dieselbe
  Allzweckrolle, die RLS für alle anderen Zugriffe durchsetzt, hält den
  Schlüssel zur Ausnahme. Eine SQL-Injektion oder ein fehlerhafter
  Query-Builder in der Applikation hätte über `app.cross_tenant` **alle**
  Mandanten im Blick.
  **Härtung als offene Entscheidung (E-12, Gate 2):** die konsequenteste
  Härtung wäre eine **eigene Laufzeitrolle** `echobox_ops`, der die Freigabe
  der drei Funktionen gehört, während `echobox_app` sie nicht mehr bekommt.
  Das verlangt eine getrennte Verbindungsrolle im Betrieb (ADR-013: zwei
  Rollen, ein Image) und ist eine **echte Architekturentscheidung**, die der
  Mensch treffen muss. **Bis dahin gilt die Grenze so, wie hier benannt.**

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
| `app.list_endpoints_cross_tenant(...)` **ohne** `app.cross_tenant` | 0 Zeilen (fail-closed) |
| `app.list_endpoints_cross_tenant(...)` **mit** `app.cross_tenant = 'service_admin'` | Endpunkte **aller** Mandanten, je mit `organisation_id` |
| `app.list_dead_deliveries_cross_tenant(...)` / `app.enforce_retention_cross_tenant(...)` ohne Freigabe | 0 Zeilen (fail-closed) |
| `app.list_dead_deliveries_cross_tenant(...)` **ohne** Statusparameter | ausschließlich `abandoned` — kein `sent`, kein `cancelled`; `abandoned_at` immer gefüllt (N-14) |
| logische Löschung durch `echobox_app` (`UPDATE … SET deleted_at`) | `new row violates row-level security policy` — die Löschung läuft über die Eigentümerfunktion (N-16) |
| markierter Zustelllauf ⇒ seine Versuche | für die Applikationsrolle **unsichtbar** (Policy folgt dem Zustand des Vaters) — die Kennzahlen zählen sie nicht mehr (N-13) |
| `echobox_worker` ruft eine der drei Funktionen auf | `permission denied` — der Worker ist **nicht** freigegeben |
| Rückgabeprojektion der drei Funktionen | enthält **kein** Feld für Secret, Chiffre, Nutzlast oder Schlüssel |

## Status

**Angenommen** — bei Gate 1 am 2026-10-21 bestätigt.

**Korrektur vom 2026-10-21 (Rücksprung 6, nach dem Einfrieren des Vertrags):**
§7 — der Bullet zu `organisation_id` ist auf die **V-26**-Fassung umgestellt:
das Feld ist **immer** gefüllt und benennt den **besitzenden Mandanten** der
Ressource — im eigenen Mandanten den eigenen, in den mandantenübergreifenden
Antworten den Mandanten, zu dem der Datensatz gehört (V-48). Die Semantik der
drei Eigentümerfunktionen ist **unverändert**; es wurde **keine** bestätigte
Entscheidung neu aufgerollt.
