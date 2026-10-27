---
name: aisdlc-security-scan
description: Phase 8: Read-only Security- und Compliance-Audit.
metadata:
  origin: adaptiert aus ECC/security-review + wshobson/security-scanning + wshobson/backend-api-security + superpowers/requesting-code-review
---

## Wann

Nach der Implementierung (Phase 6/7), **vor** dem Deployment (Phase 9). Dieser Audit ist
**read-only**: keine Quelldatei aendern. Gefunde werden als Befund gemeldet, nicht fixiert.

## Pruefungen

### 1. Authentifizierung
- Alle geschuetzten Routen verlangen ein gueltiges Token.
- Test ohne Token-Header erwartet 401 (nicht 403, nicht 500).
- Token-Ablauf, Signaturpruefung, Zielgruppe (Audience) und Algorithmus werden geprueft.
- Keine selbstgebaute Kryptografie, kein `none`-Algorithmus.

### 2. Autorisierung
- **IDOR:** Jede Objekt-Route prueft den Besitz, nicht nur die Rolle.
- Standard ist verweigern. Keine `permitAll` auf ganzen Praefixen ohne Begruendung.
- Mandanten-/Organisations-Scoping auf **jeder** Query-Ebene, nicht nur im Controller.

### 3. Secrets
```bash
grep -rInE "(api[_-]?key|secret|password|token|private[_-]?key)[[:space:]]*[:=][[:space:]]*['\"][^'\"]{8,}" src/ tests/ infra/ 2>/dev/null
```
- Keine Secrets im Code, in Config-Dateien, in Dockerfiles oder CI/CD.
- `.env` in `.gitignore`; keine Secrets in der Git-Historie.
- Secrets aus Umgebung oder Secret-Store, mit Fehlermeldung bei fehlender Konfiguration.

### 4. Injection
- Alle Queries parametriert (ORM oder Prepared Statements). Kein String-Concatenation,
  kein f-String mit Variablen in SQL.
- Kein `Runtime.exec`/`os.system` mit Nutzereingabe; wenn noetig, mit Allowlist.
- Pfad-Traversal: Dateipfade aus Eingaben normalisieren und gegen ein Wurzelverzeichnis pruefen.

### 5. Ausgabe / XSS
- Benutzereingaben werden kontextabhaengig escaped.
- Suche nach `dangerouslySetInnerHTML`, `v-html`, `| safe`, `markup` ohne Escaping.
- Fehlermeldungen geben keine internen Details preis (Stacktrace, SQL, Pfade).

### 6. Session und Transport
- Secure-, HttpOnly- und SameSite-Flags; sichere Cookies, wenn Session-basiert.
- TLS erzwungen, HSTS gesetzt, sinnvolle Security-Header.
- CORS-Liste explizit, kein `*` zusammen mit Credentials.

### 7. Rate-Limiting und Ressourcen
- Ratenbegrenzung fuer Anmeldung, Registrierung und teure Endpunkte.
- Datei-Uploads: Groessenlimit, Typen-Allowlist, kein unbedienter Speicherpfad.
- Unbegrenzte Paginierung und Body-Groessen verhindert.

### 8. Abhaengigkeiten und Container
- Bekannte kritische/hohe Luecken in Abhaengigkeiten (SCA-Scan).
- Container laufen als non-root, minimale Basis-Images mit festem Tag (nicht `latest`),
  keine unnoetigen Ports, Secrets nicht ins Image.
- CI/CD: keine Secrets in Logs, OIDC statt statischer Keys, geschützte Branches.

### 9. Logging und Datenschutz
- **Gegen `.agent/artifacts/logging.md` pruefen**, nicht gegen eine eigene Erwartung.
  Die Konvention ist der Massstab; fehlt sie, ist das selbst ein Befund.
- Je Ereignis eine Zeile: Korrelations-ID, Ereignistyp, Mandant, Ergebnis, Dauer.
- Keine Passwoerter, Tokens, kompletten personenbezogenen Datensaetze, **keine
  Nutzdaten** in Logs.
- **Kein Stacktrace in der Antwort** - im Log ist er erlaubt, in der Antwort nicht.
- Retention- und Loeschkonzept dokumentiert (z. B. 90 Tage, Right to Erasure).
- Personenbezogene Daten werden nicht ueber unnoetige Kanale gesendet.

### 10. OWASP Top 10 (Querpruefung)
Die obigen Pruefungen gegen die Top-10-Kategorien gegenpruefen: A01 Broken Access Control,
A02 Cryptographic Failures, A03 Injection, A04 Insecure Design, A05 Security Misconfiguration,
A06 Vulnerable Components, A07 Identification/Auth Failures, A08 Data Integrity Failures,
A09 Logging/Monitoring Failures, A10 SSRF.

## Befundformat

| Schwere | Datei:Zeile | Befund | Ausnutzbarkeit | Empfehlung |
|---|---|---|---|---|

- **Critical:** Datenverlust, Mandanten-Durchbruch, Secrets im Repo, RCE
- **High:** Auth-Bypass, Injection mit realem Pfad, fehlende Autorisierung
- **Medium:** Defense-in-depth fehlt, Header/CORS falsch, Logging unvollstaendig
- **Low:** Härtung, Dokumentation

Jeder Befund nennt **Nachweis** (Datei:Zeile oder ausgefuehrter Check) und **Ausnutzbarkeit**
(wie ein Angreifer es nutzt). Vermutungen werden als offene Frage markiert, nicht als Befund.

## Regeln

- Read-only. Keine Datei aendern, keine Konfiguration anpassen.
- Kein "Critical" ohne belegten Nachweis.
- Jeder Fund mit `[SECURITY]`-Praefix im Bericht kennzeichnen.
- Null Findings sind ein Ergebnis - aber nur, wenn geprueft wurde.

## Ausgabeform

**Jeder Bericht über ~20 KB beginnt mit einer Tabelle**: Befund, Schwere, Status, wo
behoben. Die Herleitung steht darunter. Eine Tabelle ist der Einstieg, die Prosa ist
der Beleg - ohne die Tabelle muss jemand 100 KB lesen, um zu zählen, was offen ist.

## Output

`docs/qa/security-audit.md` mit Befundtabelle, Gesamtrating und Freigabe-Empfehlung
(freigeben / blockieren). Phase 9 startet nur bei 0 Critical und 0 High.
