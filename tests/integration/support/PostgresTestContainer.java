package com.echobox.testsupport;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Gemeinsamer PostgreSQL-16-Testcontainer fuer alle Integrationstests.
 *
 * <p>Die Mandantentrennung ist nur gegen eine <em>echte</em> PostgreSQL-Instanz
 * belegbar (AC-022-1, AC-022-2) — H2 oder SQLite kennen weder RLS noch
 * {@code bytea} noch die partiellen Indizes. Deshalb steht hier Testcontainers
 * und nicht ein Mock.
 *
 * <p>Der Container wird einmal je JVM gestartet und von allen Testklassen
 * geteilt; die Testdaten erzeugt jeder Test selbst (keine geteilten
 * Fixtures, keine Reihenfolgeabhaengigkeit).
 */
public final class PostgresTestContainer {

    private static final org.testcontainers.containers.PostgreSQLContainer<?> POSTGRES =
            new org.testcontainers.containers.PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("echobox")
                    .withUsername("postgres")
                    .withPassword("postgres");

    static {
        POSTGRES.start();
    }

    private PostgresTestContainer() {
        // statische Helferklasse
    }

    /**
     * Liefert den gestarteten Container.
     *
     * @return der Testcontainer
     */
    public static org.testcontainers.containers.PostgreSQLContainer<?> instance() {
        return POSTGRES;
    }

    /**
     * Liefert die JDBC-URL des Containers.
     *
     * @return die JDBC-URL
     */
    public static String jdbcUrl() {
        return POSTGRES.getJdbcUrl();
    }

    /**
     * Hinterlegt die Verbindungsdaten in der Spring-Konfiguration.
     *
     * <p>Aufruf aus einer {@code @DynamicPropertySource}-Methode der Testklasse.
     *
     * @param registry die Property-Registry
     */
    public static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }
}
