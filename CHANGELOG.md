# Changelog

Alle wesentlichen Aenderungen an EchoBox werden in dieser Datei festgehalten.

Das Format orientiert sich an [Keep a Changelog](https://keepachangelog.com/de/1.1.0/),
die Versionierung an [Semantic Versioning](https://semver.org/lang/de/).

## [Unreleased]

### Added

- **Backend (Java 21, Spring Boot 3)** — das Fundament (`common`, `security`,
  `crypto`, `tenant`) und die Module `endpoint`, `event`, `delivery` und
  `protocol`: Endpunktverwaltung, Ereigniseinlieferung, der Zustellzyklus mit
  Backoff und Rate-Limit, das Zustellprotokoll, die Totenliste, die Kennzahlen
  und die Aufbewahrung (ADR-002, ADR-004, ADR-010, ADR-015, ADR-017).
- **Oberflaeche (Next.js 15, React 19, TypeScript strict)** — die drei Seiten
  Endpunkte, Zustellprotokoll und Tote Zustellungen als **Server Components**
  mit **Server Actions**; die Bedienung funktioniert **ohne JavaScript**
  (ADR-018 §8). Die Typen stammen aus dem eingefrorenen Vertrag und werden
  von einem Skript dagegen geprueft (`npm run contract:check`).
- **Replay fuer den Empfaenger-Admin** — die Seite der toten Zustellungen
  liest die aufgegebenen Zustellungen mit der Operation, die die Identitaet
  lesen darf, und bietet das erneute Ausloesen **nur** dem Empfaenger-Admin
  des eigenen Mandanten an (FR-024, F-03, V-39).
- **Container-Build und Bereitstellungsdeskriptor** — `Dockerfile`
  (multi-stage, gepinnte Basis-Images, nicht privilegierter Benutzer, kein
  Secret im Image) und `.github/workflows/deploy.yml` (getrennte Jobs fuer
  Staging und Produktion).

### Changed

- **Die Oberflaeche spricht den Dienst server-seitig an** — der Browser kennt
  die Dienstadresse nicht; damit entfallen die CORS-Regel und der
  Same-Origin-Rewrite fuer den Betrieb (O-15).
- **Das Bearer-Token liegt in einem httpOnly-Cookie** — ein Token im
  `localStorage` waere fuer einen Server Component nicht erreichbar und ueber
  ein XSS-Skript auslesbar.

### Security

- Die Nutzlast wird AES-256-GCM-verschluesselt abgelegt; der Schluessel kommt
  ausschliesslich aus der Umgebung.
- Die mandantenuebergreifende Sicht besteht nur aus den drei
  `SECURITY DEFINER`-Eigentuemerfunktionen des Schemas — kein vierter
  Leseweg, kein `BYPASSRLS`.
- Die Nutzlast und die Endpunkt-Secrets erscheinen in keinem Log, keiner
  Fehlermeldung und keiner `toString()`.
