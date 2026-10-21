package com.echobox.testsupport;

import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

/**
 * Haelt die Anwendungs- und Zugriffsprotokolle waehrend eines Tests fest.
 *
 * <p>AC-007-2 verlangt: die Protokolle werden nach der Klartextmarkierung
 * durchsucht und es wird <em>kein Treffer</em> erzielt. Dazu muessen die
 * Protokollzeilen im Test verfuegbar sein — der Zugriff auf die Log-Datei des
 * Containers waere weder portabel noch deterministisch.
 *
 * <p>Die Erfassung haengt sich an den Root-Logger; der Logger wird waehrend der
 * Aufzeichnung auf DEBUG gehoben und danach auf den Ausgangswert zurueckgesetzt.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final Level previousLevel;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Logger logger) {
        this.logger = logger;
        this.previousLevel = logger.getLevel();
        this.appender.setContext(logger.getLoggerContext());
        this.appender.start();
        logger.addAppender(this.appender);
        logger.setLevel(Level.DEBUG);
    }

    /**
     * Beginnt die Aufzeichnung aller Protokollzeilen.
     *
     * @return die Aufzeichnung
     */
    public static LogCapture start() {
        return new LogCapture((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
    }

    /**
     * Liefert alle aufgezeichneten Protokollzeilen.
     *
     * @return die Protokollzeilen in der Reihenfolge des Auftretens
     */
    public List<String> messages() {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    /**
     * Liefert alle aufgezeichneten Protokollzeilen mit ihrem Logger-Namen.
     *
     * @return die Protokollzeilen
     */
    public List<String> detailedMessages() {
        return appender.list.stream()
                .map(event -> event.getLoggerName() + " - " + event.getFormattedMessage())
                .toList();
    }

    @Override
    public void close() {
        logger.detachAppender(this.appender);
        this.appender.stop();
        logger.setLevel(previousLevel);
    }
}
