package com.echobox.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Schreibt eine Fehlerantwort in eine Servlet-Antwort.
 *
 * <p><strong>Warum eigens?</strong> Zwei Stellen liegen <strong>ausserhalb</strong>
 * der Controller-Kette, in der der {@link GlobalExceptionHandler} die Fehler
 * abbildet: die Security-Kette (401/403) und der Mandantenparameter-Filter
 * (422). Beide brauchen das Fehlerschema {@code Problem} des Vertrags — und
 * beide schreiben es <strong>auf dieselbe Weise</strong>. Diese Klasse ist
 * diese <strong>eine</strong> Schreibweise.
 *
 * <p>Der Dienst spricht an dieser Stelle bereits fertige Fehlerbilder an: die
 * Abbildung von Status und Code auf HTTP bleibt beim
 * {@link GlobalExceptionHandler} (ADR-015 §4).
 */
public final class ProblemWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProblemWriter() {
        // statische Helferklasse
    }

    /**
     * Schreibt das Fehlerbild in die Antwort.
     *
     * @param response die Antwort
     * @param problem  das Fehlerbild
     * @throws IOException wenn die Antwort nicht geschrieben werden kann
     */
    public static void write(HttpServletResponse response, Problem problem) throws IOException {
        response.setStatus(problem.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(MAPPER.writeValueAsString(problem));
    }
}
