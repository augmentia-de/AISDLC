package com.echobox.common;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

/**
 * Die **einzige** Stelle, an der Ausnahmen auf HTTP-Statuscodes und das
 * {@code Problem}-Schema des Vertrags abgebildet werden (ADR-015 §4).
 *
 * <p>Die Zuordnung Status → {@code code} steht im Vertrag
 * ({@code info.description}, Abschnitt „Fehlersemantik“):
 *
 * <table>
 *   <caption>Fehlersemantik</caption>
 *   <tr><th>Status</th><th>Code</th></tr>
 *   <tr><td>400</td><td>{@code malformed_request}</td></tr>
 *   <tr><td>401</td><td>{@code unauthorized}</td></tr>
 *   <tr><td>403</td><td>{@code forbidden}</td></tr>
 *   <tr><td>404</td><td>{@code not_found}</td></tr>
 *   <tr><td>409</td><td>{@code idempotency_conflict} / {@code delivery_state_conflict}</td></tr>
 *   <tr><td>413</td><td>{@code payload_too_large}</td></tr>
 *   <tr><td>422</td><td>{@code validation_failed} / {@code tenant_parameter_rejected}</td></tr>
 *   <tr><td>429</td><td>{@code rate_limit_exceeded}</td></tr>
 *   <tr><td>500</td><td>{@code internal_error}</td></tr>
 *   <tr><td>503</td><td>{@code service_unavailable}</td></tr>
 * </table>
 *
 * <p>Die Grenze zwischen 400 und 422: **400** bedeutet *syntaktisch* fehlerhaft
 * (der Body ist kein gueltiges JSON). **Jede** Abweichung vom Schema des
 * Request-Bodys — unbekanntes Feld, falscher Typ, fehlendes Pflichtfeld — ist
 * **422** {@code validation_failed} mit einem {@code violations}-Eintrag.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Bildet einen fachlichen Fehler ab.
     *
     * @param exception der fachliche Fehler
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Problem> handleApiException(ApiException exception, HttpServletRequest request) {
        Problem problem = ProblemFactory.create(exception.code(), exception.status(), exception.title(),
                exception.detail(), request.getRequestURI(), exception.violations());
        return respond(problem);
    }

    /**
     * Bildet eine Feldverletzung ab — HTTP 422 {@code validation_failed}.
     *
     * @param exception die Verletzung
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Problem> handleValidationException(ValidationException exception,
                                                              HttpServletRequest request) {
        return respond(validationFailed(request, exception.violations()));
    }

    /**
     * Bildet die Verstoesse der Bean-Validierung ab — HTTP 422.
     *
     * @param exception die Verstoesse
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Problem> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                 HttpServletRequest request) {
        List<ValidationViolation> violations = new ArrayList<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            violations.add(new ValidationViolation(error.getField(), error.getCode(),
                    defaultMessage(error.getDefaultMessage(), error.getField())));
        }
        for (ObjectError error : exception.getBindingResult().getGlobalErrors()) {
            violations.add(new ValidationViolation(error.getObjectName(), error.getCode(),
                    defaultMessage(error.getDefaultMessage(), error.getObjectName())));
        }
        return respond(validationFailed(request, violations));
    }

    /**
     * Bildet die Verstoesse der Parametervalidierung ab — HTTP 422.
     *
     * @param exception die Verstoesse
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Problem> handleConstraintViolation(ConstraintViolationException exception,
                                                               HttpServletRequest request) {
        List<ValidationViolation> violations = new ArrayList<>();
        exception.getConstraintViolations().forEach(violation -> violations.add(new ValidationViolation(
                String.valueOf(violation.getPropertyPath()), violation.getMessageTemplate(),
                violation.getMessage())));
        return respond(validationFailed(request, violations));
    }

    /**
     * Bildet einen nicht lesbaren Request-Body ab.
     *
     * <p>Die Grenze zwischen 400 und 422 wird hier gezogen: ein **Syntaxfehler**
     * ist 400 {@code malformed_request}, eine **Abweichung vom Schema**
     * (falscher Typ, unbekanntes Feld) ist 422 {@code validation_failed}.
     *
     * @param exception die Ausnahme
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Problem> handleNotReadable(HttpMessageNotReadableException exception,
                                                     HttpServletRequest request) {
        Throwable cause = exception.getCause();
        if (cause instanceof MismatchedInputException) {
            Problem problem = ProblemFactory.create(ProblemFactory.VALIDATION_FAILED,
                    HttpStatus.UNPROCESSABLE_ENTITY.value(), "Validation failed",
                    "Die Anfrage weicht vom Schema des Request-Bodys ab.", request.getRequestURI(),
                    List.of(new ValidationViolation(fieldOf(cause), "invalid_value",
                            "Der Wert hat nicht den erwarteten Typ oder ein unbekanntes Feld wurde gesendet.")));
            return respond(problem);
        }
        if (cause instanceof JsonProcessingException) {
            Problem problem = ProblemFactory.create(ProblemFactory.MALFORMED_REQUEST,
                    HttpStatus.BAD_REQUEST.value(), "Malformed request",
                    "Der Request-Body ist kein gueltiges JSON.", request.getRequestURI());
            return respond(problem);
        }
        Problem problem = ProblemFactory.create(ProblemFactory.MALFORMED_REQUEST,
                HttpStatus.BAD_REQUEST.value(), "Malformed request",
                "Der Request-Body ist nicht lesbar.", request.getRequestURI());
        return respond(problem);
    }

    /**
     * Bildet einen fehlenden oder unbrauchbaren Request-Parameter ab — HTTP 422.
     *
     * @param exception die Ausnahme
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Problem> handleBadParameter(Exception exception, HttpServletRequest request) {
        String field = exception instanceof MissingServletRequestParameterException missing
                ? missing.getParameterName()
                : ((MethodArgumentTypeMismatchException) exception).getName();
        return respond(validationFailed(request, List.of(new ValidationViolation(field, "invalid_parameter",
                "Der Parameter fehlt oder hat einen unbrauchbaren Wert."))));
    }

    /**
     * Bildet eine fehlende Ressource (unbekannter Pfad) ab — HTTP 404.
     *
     * @param exception die Ausnahme
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Problem> handleNoResource(NoResourceFoundException exception, HttpServletRequest request) {
        Problem problem = ProblemFactory.create(ProblemFactory.NOT_FOUND, HttpStatus.NOT_FOUND.value(),
                "Not found", "Die Ressource wurde nicht gefunden.", request.getRequestURI());
        return respond(problem);
    }

    /**
     * Bildet eine fehlgeschlagene Anmeldung ab — HTTP 401.
     *
     * @param exception die Ausnahme
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Problem> handleAuthentication(AuthenticationException exception,
                                                         HttpServletRequest request) {
        Problem problem = ProblemFactory.create(ProblemFactory.UNAUTHORIZED, HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized", "Das Bearer-Token fehlt oder ist ungueltig.", request.getRequestURI());
        return respond(problem);
    }

    /**
     * Bildet eine fehlgeschlagene Berechtigungspruefung ab — HTTP 403.
     *
     * @param exception die Ausnahme
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Problem> handleAccessDenied(AccessDeniedException exception,
                                                        HttpServletRequest request) {
        Problem problem = ProblemFactory.create(ProblemFactory.FORBIDDEN, HttpStatus.FORBIDDEN.value(),
                "Forbidden", "Diese Rolle darf die Operation fuer diesen Mandanten nicht ausfuehren.",
                request.getRequestURI());
        return respond(problem);
    }

    /**
     * Bildet einen nicht unterstuetzten Medientyp ab — HTTP 400
     * {@code malformed_request}.
     *
     * <p>Ein nicht unterstuetzter Medientyp ist ein **Fehler des Aufrufers**
     * (S-30): der Vertrag kennt fuer die Einlieferung nur {@code
     * application/json} und fuer Fehler des Aufrufers nur 4xx. Ohne diesen
     * Fall fiel die Ausnahme in den allgemeinen Handler — die Antwort war
     * HTTP 500 {@code internal_error} mit Stacktrace im Protokoll; ein Aufrufer
     * haette damit **unbegrenzt** Serverfehler erzeugen koennen, ohne dass das
     * Monitoring es als Fehler des Aufrufers erkannt haette.
     *
     * <p>Der Stacktrace wird **nicht** protokolliert: der Fehler ist erwartbar
     * und traegt kein internes Detail. Im Protokoll steht er hoechstens auf
     * DEBUG.
     *
     * @param exception die Ausnahme
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler({HttpMediaTypeNotSupportedException.class, HttpMediaTypeNotAcceptableException.class})
    public ResponseEntity<Problem> handleUnsupportedMediaType(Exception exception, HttpServletRequest request) {
        LOG.debug("Nicht unterstuetzter Medientyp bei {} {}", request.getMethod(), request.getRequestURI());
        Problem problem = ProblemFactory.create(ProblemFactory.MALFORMED_REQUEST,
                HttpStatus.BAD_REQUEST.value(), "Malformed request",
                "Der Medientyp der Anfrage wird nicht unterstuetzt.", request.getRequestURI());
        return respond(problem);
    }

    /**
     * Bildet einen nicht lesbaren Multipart-Body ab — HTTP 400
     * {@code malformed_request}.
     *
     * <p>Der Dienst nimmt **nur** JSON entgegen (der Vertrag kennt keinen
     * Multipart-Koerper, der Multipart-Resolver ist abgeschaltet). Ein
     * Multipart-Body ist deshalb ein Fehler des Aufrufers — kein Serverfehler.
     *
     * @param exception der Ausnahme
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Problem> handleMultipart(MultipartException exception, HttpServletRequest request) {
        LOG.debug("Nicht unterstuetzter Multipart-Body bei {} {}", request.getMethod(), request.getRequestURI());
        Problem problem = ProblemFactory.create(ProblemFactory.MALFORMED_REQUEST,
                HttpStatus.BAD_REQUEST.value(), "Malformed request",
                "Der Medientyp der Anfrage wird nicht unterstuetzt.", request.getRequestURI());
        return respond(problem);
    }

    /**
     * Bildet jeden unerwarteten Fehler ab — HTTP 500, ohne internes Detail.
     *
     * @param exception die Ausnahme
     * @param request   die Anfrage
     * @return die Fehlerantwort
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> handleUnexpected(Exception exception, HttpServletRequest request) {
        LOG.error("Unerwarteter Fehler bei {} {}", request.getMethod(), request.getRequestURI(), exception);
        Problem problem = ProblemFactory.create(ProblemFactory.INTERNAL_ERROR,
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error",
                "Die Anfrage konnte nicht verarbeitet werden.", request.getRequestURI());
        return respond(problem);
    }

    private static ResponseEntity<Problem> respond(Problem problem) {
        return ResponseEntity.status(problem.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * Die Fehlerantwort einer semantisch ungueltigen Anfrage — HTTP 422
     * {@code validation_failed}.
     *
     * <p>Es gibt <strong>eine</strong> Abbildung: die Bean-Validierung, die
     * Parametervalidierung, die Fachverletzung und der unbrauchbare Parameter
     * ergeben <strong>dasselbe</strong> Fehlerbild — nur die Verstoesse
     * unterscheiden sich.
     *
     * @param request    die Anfrage
     * @param violations die feldbezogenen Verstoesse
     * @return die Fehlerantwort
     */
    private static Problem validationFailed(HttpServletRequest request, List<ValidationViolation> violations) {
        return ProblemFactory.create(ProblemFactory.VALIDATION_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY.value(), "Validation failed",
                "Die Anfrage ist semantisch ungueltig.", request.getRequestURI(), violations);
    }

    private static String defaultMessage(String message, String fallback) {
        return message == null || message.isBlank() ? "Das Feld ist ungueltig." : message;
    }

    private static String fieldOf(Throwable cause) {
        if (cause instanceof MismatchedInputException mismatch) {
            String path = mismatch.getPathReference();
            if (path != null && !path.isBlank()) {
                int dot = path.lastIndexOf('.');
                return dot >= 0 ? path.substring(dot + 1) : path;
            }
            return "body";
        }
        return "body";
    }
}
