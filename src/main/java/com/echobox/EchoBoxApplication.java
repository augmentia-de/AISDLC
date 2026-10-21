package com.echobox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Einstiegspunkt des EchoBox-Dienstes.
 *
 * <p>EchoBox nimmt Ereignisse entgegen und liefert sie per HTTP an registrierte
 * Empfaenger-URLs weiter. Der Dienst haelt nur, was er fuer die Zustellung
 * braucht, und das so kurz wie moeglich.
 *
 * <p>Der Paketname {@code com.echobox} ist die Wurzel des Komponentenscans: die
 * Module {@code endpoint}, {@code event}, {@code delivery}, {@code protocol},
 * {@code crypto}, {@code tenant}, {@code config} und {@code common} liegen darunter.
 *
 * <p>Der Vertrag ist die Single Source of Truth: {@code docs/api/openapi.yaml}.
 *
 * @see com.echobox.config.SecurityConfiguration
 * @see com.echobox.common.GlobalExceptionHandler
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class EchoBoxApplication {

    /**
     * Startet den Dienst.
     *
     * @param args die Startargumente
     */
    public static void main(String[] args) {
        SpringApplication.run(EchoBoxApplication.class, args);
    }
}
