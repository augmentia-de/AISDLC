package com.echobox.endpoint;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import com.echobox.common.ValidationException;

/**
 * Die Ziel-URL eines Endpunkts — Wertobjekt mit der Pruefung des Vertrags.
 *
 * <p>Der Vertrag fordert (AC-001-2, AC-001-3):
 * <ul>
 *   <li>das Schema <strong>https</strong> — {@code scheme_not_allowed};</li>
 *   <li><strong>keine eingebetteten Zugangsdaten</strong> — die Zugangsdaten
 *       laufen ausschliesslich ueber das Secret des Endpunkts
 *       ({@code credentials_not_allowed});</li>
 *   <li>einen Host.</li>
 * </ul>
 *
 * <p><strong>Reihenfolge:</strong> das Schema wird <strong>zuerst</strong>
 * geprueft, dann die Zugangsdaten. Eine URL {@code http://user:pass@host} ist
 * also zuerst am falschen Schema falsch.
 */
public record EndpointTargetUrl(String value, String host) {

    /** Das einzige erlaubte Schema. */
    private static final String REQUIRED_SCHEME = "https";

    /** Der Feldname im Vertrag. */
    private static final String FIELD = "target_url";

    /**
     * Der einzige Weg zu einer gueltigen Ziel-URL.
     *
     * @param raw die Ziel-URL aus dem Request
     * @return die gepruefte Ziel-URL
     * @throws ValidationException wenn die Ziel-URL das Schema verletzt
     */
    public static EndpointTargetUrl of(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException(FIELD, "target_url_required",
                    "Die Ziel-URL ist erforderlich.");
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (URISyntaxException e) {
            throw new ValidationException(FIELD, "malformed_url",
                    "Die Ziel-URL ist keine gueltige URL.");
        }
        if (!REQUIRED_SCHEME.equals(uri.getScheme())) {
            throw new ValidationException(FIELD, "scheme_not_allowed",
                    "Die Ziel-URL muss das Schema https verwenden.");
        }
        if (uri.getRawUserInfo() != null) {
            throw new ValidationException(FIELD, "credentials_not_allowed",
                    "Eingebettete Zugangsdaten sind nicht erlaubt — die Zugangsdaten laufen "
                            + "ausschliesslich ueber das Secret des Endpunkts.");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ValidationException(FIELD, "malformed_url",
                    "Die Ziel-URL enthaelt keinen Host.");
        }
        return new EndpointTargetUrl(raw, host);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof EndpointTargetUrl that && value.equals(that.value) && host.equals(that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, host);
    }

    @Override
    public String toString() {
        return value;
    }
}
