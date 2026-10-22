package com.echobox.config;

/**
 * Die Zielgruppe ({@code aud}), die ein Token tragen muss (S-21, S-29).
 *
 * <p>Die Zielgruppe ist **Pflicht**: ein Token, das der Identity Provider fuer
 * einen anderen Client ausgestellt hat, wird abgelehnt (D-01 der
 * Tokenpruefung).
 *
 * <p><strong>Eine leere Zielgruppe ist kein „aus" (S-29).</strong> Sie waere ein
 * stilles Fail-open: der Dienst nimmte dann Token <em>irgendeines</em> Clients
 * desselben Identity Providers an, sofern Mandant und Rolle passten. Statt
 *dessen bricht der Dienst den Start ab — die Konfiguration ist damit
 * fail-closed. Der Vorgabewert {@code echobox} bleibt; ein **leerer** Wert
 * (z. B. ein leeres Secret in der Umgebung) schaltet die Pruefung nicht ab.
 *
 * @param audience die erwartete Zielgruppe (nie leer)
 */
public record AudiencePolicy(String audience) {

    /**
     * Erzeugt die Zielgruppen-Vorgabe — oder bricht den Start ab.
     *
     * @param audience die konfigurierte Zielgruppe
     * @return die Zielgruppen-Vorgabe
     * @throws IllegalStateException wenn die Zielgruppe leer ist
     */
    public static AudiencePolicy required(String audience) {
        if (audience == null || audience.isBlank()) {
            throw new IllegalStateException(
                    "Die Zielgruppe der Token ist nicht konfiguriert (ECHobox_SECURITY_AUDIENCE). "
                            + "Ohne sie kann der Dienst nicht pruefen, ob ein Token fuer EchoBox ausgestellt "
                            + "wurde — der Dienst startet nicht.");
        }
        return new AudiencePolicy(audience.trim());
    }
}
