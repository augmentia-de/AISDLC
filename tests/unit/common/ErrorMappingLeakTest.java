package com.echobox.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MultipartException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit-Ebene — die **einzige** Fehlerabbildung des Dienstes gibt kein
 * internes Detail preis (S-30, dritte Audit-Runde).
 *
 * <p>Die Fehlerabbildung ist eine einzige Stelle
 * ({@link GlobalExceptionHandler}). Die Frage dieser Runde lautet: **leaken
 * andere Ausnahmen weiterhin Stacktraces?** Geprueft wird deshalb je Fehlerart:
 * <ul>
 *   <li>ein Fehler des **Aufrufers** (nicht unterstuetzter Medientyp, nicht
 *       annehmbarer Medientyp, Multipart-Body, unlesbarer Body) ergibt einen
 *       **4xx** und **kein** internes Detail;</li>
 *   <li>ein **unerwarteter** Fehler ergibt 500 {@code internal_error} — die
 *       Antwort traegt **keinen** Stacktrace, keine Klasse, keinen Pfad. (Im
 *       *Protokoll* bleibt der Stacktrace stehen: das ist die Aufgabe des
 *       Betriebsprotokolls, nicht der Antwort.)</li>
 * </ul>
 */
class ErrorMappingLeakTest {

    private HttpServletRequest request;

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/v1/events");
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("S-30: ein nicht unterstuetzter Medientyp ergibt 400 und kein internes Detail")
    void an_unsupported_media_type_is_a_client_error() {
        ResponseEntity<Problem> response = handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotSupportedException("text/plain"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ProblemFactory.MALFORMED_REQUEST);
        assertThat(response.getBody().detail())
                .as("die Antwort traegt weder Stacktrace noch Klasse noch Pfad")
                .doesNotContain("org.springframework")
                .doesNotContain("com.echobox")
                .doesNotContain("Exception")
                .doesNotContain("java.");
    }

    @Test
    @DisplayName("S-30: ein nicht annehmbarer Medientyp ergibt 400 und kein internes Detail")
    void an_unacceptable_media_type_is_a_client_error() {
        ResponseEntity<Problem> response = handler.handleUnsupportedMediaType(
                new HttpMediaTypeNotAcceptableException("text/plain"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ProblemFactory.MALFORMED_REQUEST);
    }

    @Test
    @DisplayName("S-30: ein Multipart-Body ergibt 400 und kein internes Detail")
    void a_multipart_body_is_a_client_error() {
        ResponseEntity<Problem> response = handler.handleMultipart(
                new MultipartException("multiparts sind deaktiviert"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ProblemFactory.MALFORMED_REQUEST);
        assertThat(response.getBody().detail()).doesNotContain("multiparts sind deaktiviert");
    }

    @Test
    @DisplayName("S-30: ein unlesbarer Body ergibt 400 (Syntaxfehler) — kein internes Detail")
    void an_unreadable_body_is_a_client_error() {
        ResponseEntity<Problem> response = handler.handleNotReadable(
                new HttpMessageNotReadableException("kaputt", new JsonParseException(null, "kaputt"), null),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ProblemFactory.MALFORMED_REQUEST);
        assertThat(response.getBody().detail()).doesNotContain("kaputt");
    }

    @Test
    @DisplayName("S-30: ein Schemaverstoss ergibt 422 — die Grenze zu 400 bleibt erhalten")
    void a_schema_violation_is_a_validation_error() {
        ResponseEntity<Problem> response = handler.handleNotReadable(
                new HttpMessageNotReadableException("unbekanntes Feld",
                        MismatchedInputException.from(null, String.class, "unbekanntes Feld"), null),
                request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ProblemFactory.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("S-30: ein unerwarteter Fehler ergibt 500 ohne internes Detail in der Antwort")
    void an_unexpected_error_is_an_internal_error_without_internal_detail() {
        ResponseEntity<Problem> response = handler.handleUnexpected(
                new IllegalStateException("Verbindung zu /home/torsten/db fehlgeschlagen"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ProblemFactory.INTERNAL_ERROR);
        assertThat(response.getBody().detail())
                .as("die Antwort traegt weder Stacktrace noch die Meldung der Ausnahme noch einen Pfad")
                .doesNotContain("Verbindung zu")
                .doesNotContain("/home/torsten")
                .doesNotContain("IllegalStateException")
                .doesNotContain("com.echobox")
                .doesNotContain("Exception");
        assertThat(response.getBody().instance())
                .as("der Pfad der Anfrage ist erlaubt — er nennt keinen internen Ort")
                .isEqualTo("/api/v1/events");
    }

    @Test
    @DisplayName("S-33: ein Token ohne Mandant ergibt 401 unauthorized")
    void a_missing_tenant_is_an_unauthorized_error() {
        ResponseEntity<Problem> response = handler.handleApiException(
                new UnauthenticatedException("Das Token traegt keinen Mandanten."), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ProblemFactory.UNAUTHORIZED);
        assertThat(response.getBody().code()).isNotEqualTo(ProblemFactory.INTERNAL_ERROR);
    }
}
