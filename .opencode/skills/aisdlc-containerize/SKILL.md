---
name: aisdlc-containerize
description: Steuert den Container-Build: Multi-Stage, non-root, gepinnte Basis-Images, keine Secrets im Image, Health-Check. Sagt, WELCHE Eigenschaften ein Image haben muss. Nutze diesen Skill in Phase 6 (Infra-Stream) und in Phase 9 vor dem ersten Deployment.
metadata:
  origin: eigenes Framework-Verfahren; Handwerk aus ECC/docker-patterns
---

## Wann

- **Phase 6**, im Infra-Stream.
- **Phase 9**, vor dem ersten Deployment.

## Das eiserne Gesetz

```text
KEIN SECRET IM IMAGE
```

Auch nicht in einer Development-Schicht. Geheimnisse kommen zur Laufzeit
herein, nie beim Build hinein.

## Pflicht-Eigenschaften

1. **Multi-Stage** — Build-Stufe mit Werkzeugkette, Runtime-Stufe nur mit Runtime.
2. **non-root** — eigener User, `USER` gesetzt, Schreibrechte nur fuer die App.
3. **Gepinnte Basis-Images** — konkreter Tag oder Digest, nie `latest`.
4. **Keine Secrets in Layers** — keine Credentials als Build-Arg, kein `.env` im Image.
5. **Keine Dev-Abhaengigkeiten in der Runtime-Stufe** — kein Test-Runner, keine
   Source-Maps, kein Build-Cache.
6. **Health-Check** — ein Endpunkt, den der Orchestrator aufrufen kann.
7. **Layer-Reihenfolge** — Abhaengigkeiten zuerst, dann Anwendungscode, damit
   Code-Aenderungen die Abhaengigkeitsschicht nicht invalidieren.
8. **Reproduzierbar** — keine Zeitstempel, keine Zufallswerte im Image; gleiche
   Eingabe ergibt denselben Digest.

## Compose

Das lokale Setup spiegelt die Produktion: gleiche Images, gleiche
Umgebungsvariablen-Namen, gleiche Ports, Datenbank aus dem kanonischen Schema beim
ersten Start.

## Logging und Health-Check gehoeren zusammen

Der Container schreibt in das Format aus `.agent/artifacts/logging.md` — eine
Zeile je Ereignis, mit Korrelations-ID. Der Health-Check liest dasselbe Format:
er pruft nicht nur, ob der Prozess lebt, sondern ob er **arbeitet** (letztes
Heartbeat-Ereignis, Zeitstempel). Ein Health-Check, der nur den Prozess sieht,
erkennt eine festgefahrene Zustellung nicht.

## Output

- `infra/<service>.Dockerfile`
- `infra/docker-compose.yml`
- `.dockerignore`
- `.agent/artifacts/containerization.md` — Basis-Image, Stufen, User, Ports,
  Health-Check und die Liste der injizierten Variablen (nur Namen)
- `.agent/artifacts/logging.md` — die Logging-Konvention des Projekts: Ereignisfeld,
  Level, Korrelations-ID-Fluss, was **nicht** geloggt wird, Retention

## Regeln

- Ein Image startet mit demselben Kommando in CI und in Produktion.
- Braucht das Basis-Image zur Laufzeit einen Paketmanager, ist das ein **Befund** —
  das Image wird repariert, nicht umgangen.
- Das kanonische Schema wird **nicht** ins Image kopiert; es wird beim Start
  eingespielt.

## Stack-Skills

> Die Namen sind die der **Stack-Schicht** des Projekts (`opencode.jsonc`,
> `skills/`). Der Workflow dieses Skills gilt auch ohne sie — sie liefern nur
> das Handwerk, nicht das Verfahren.
| Wofuer | Skill |
|---|---|
| Dockerfile-Muster, Compose-Overrides, Container-Security | `docker-patterns` |
| Kubernetes-Deployment, Health-Checks | `kubernetes-patterns` |
| CI-Pipeline mit Image-Build | `github-actions-templates` |
