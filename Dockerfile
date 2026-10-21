# =============================================================================
# EchoBox — Container-Build (AC-023-1, NFR-006)
#
# Regeln des Builds:
#   * **Multi-Stage** — der Build braucht Maven, das Laufzeitimage nicht.
#   * **Gepinnte Basis-Images** — eine feste Version, kein "latest".
#   * **Nicht privilegierter Benutzer** — der Dienst laeuft als uid 10001.
#   * **Keine Secrets im Image** — Schluessel kommen ausschliesslich aus der
#     Umgebung (ENV) zur Laufzeit; im Image steht kein Schluessel.
#   * **Health-Check** — der Container meldet seine Bereitschaft ueber /health.
# =============================================================================

# ------------------------------------------------------------------ Build
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /build

# Abhaengigkeiten zuerst — sie werden nur geaendert, wenn sich pom.xml aendert.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --------------------------------------------------------------- Laufzeit
FROM eclipse-temurin:21.0.5_11-jre-jammy AS runtime

# `curl` nur fuer den Health-Check; `tini` als PID 1 (Signalweiterleitung).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tini \
    && rm -rf /var/lib/apt/lists/*

# Ein nicht privilegierter Benutzer — der Dienst laeuft nie als root.
RUN groupadd --gid 10001 echobox \
    && useradd --uid 10001 --gid 10001 --home-dir /app --shell /usr/sbin/nologin echobox

WORKDIR /app

# Nur das Ergebnis des Builds — kein Maven, kein Quellcode, keine .git.
COPY --from=build --chown=10001:10001 /build/target/echobox.jar /app/echobox.jar

USER 10001:10001

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=staging \
    ECHobox_PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/health || exit 1

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/echobox.jar"]
