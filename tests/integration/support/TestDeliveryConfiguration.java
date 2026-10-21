package com.echobox.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Testkonfiguration: der ausgehende Zustellzugriff laeuft gegen einen
 * aufzeichnenden Testdouble statt gegen das Netz, und die Uhr ist testbar.
 *
 * <p>Die Anwendung bezieht den ausgehenden Zustellzugriff ueber das Interface
 * {@code com.echobox.delivery.DeliveryPort} (Port-Prinzip der Architektur).
 * Der Testdouble ersetzt die HTTP-Implementierung; die HTTP-Mechanik selbst
 * prueft {@code tests/integration/delivery/SignedDeliveryIT.java} gegen einen
 * echten Server.
 */
@TestConfiguration
public class TestDeliveryConfiguration {

    /**
     * Der aufzeichnende Testdouble — er hat Vorrang vor der HTTP-Implementierung.
     *
     * @return der Testdouble
     */
    @Bean
    @Primary
    public RecordingDeliveryPort recordingDeliveryPort() {
        return new RecordingDeliveryPort();
    }

    /**
     * Die testbare Uhr — sie hat Vorrang vor der Systemuhr.
     *
     * @return die Testuhr
     */
    @Bean
    @Primary
    public MutableClock mutableClock() {
        return new MutableClock();
    }
}
