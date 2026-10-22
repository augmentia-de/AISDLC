package com.echobox.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Ebene — der Javadoc der Zustandsmaschine ist **aktuell** (S-34).
 *
 * <p>Der Javadoc verweist noch auf eine ausstehende Nachziehung der
 * Uebergangstabelle: „Die Tabelle … §3.3 wird in Schritt 2 nachgezogen". Die
 * Tabelle <strong>ist</strong> nachgezogen (die Uebergaenge {@code pending ->
 * delayed} und {@code scheduled -> delayed} stehen in
 * {@code docs/architecture/domain_model.md} §3.3). Code und Doku sind inhaltlich
 * konsistent — nur der Verweis im Code war veraltet.
 *
 * <p>Der Nachweis ist der <strong>Quelltext</strong>: die Datei wird gelesen und
 * auf die veraltete Aussage geprueft.
 */
class DeliveryStateMachineDocumentationTest {

    private static final Path SOURCE = Path.of("src", "main", "java", "com", "echobox",
            "delivery", "DeliveryStateMachine.java");

    @Test
    @DisplayName("S-34: der Javadoc verweist nicht mehr auf eine ausstehende Nachziehung")
    void the_javadoc_does_not_mention_a_pending_table_update() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertThat(source)
                .as("die Uebergangstabelle ist nachgezogen — der Javadoc darf keine ausstehende "
                        + "Nachziehung mehr ankündigen")
                .doesNotContain("wird in Schritt 2 nachgezogen")
                .doesNotContain("nennt diesen Uebergang noch nicht");
    }

    @Test
    @DisplayName("S-34: der Javadoc nennt die Uebergaenge in den Zustand delayed")
    void the_javadoc_names_the_delayed_transitions() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);

        assertThat(source)
                .as("der Javadoc muss die Uebergaenge in den Zustand delayed benennen")
                .contains("pending -> delayed")
                .contains("scheduled -> delayed");
        assertThat(source)
                .as("der Verweis auf die Uebergangstabelle bleibt")
                .contains("docs/architecture/domain_model.md");
    }
}
