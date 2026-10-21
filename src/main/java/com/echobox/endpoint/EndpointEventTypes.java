package com.echobox.endpoint;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.echobox.common.ValidationException;

/**
 * Die von einem Endpunkt abonnierten Ereignistypen — Wertobjekt.
 *
 * <p>Der Vertrag fordert mindestens einen und hoechstens {@value #MAX_EVENT_TYPES}
 * Ereignistypen; die Typen sind eindeutig und je 1 bis
 * {@value #MAX_EVENT_TYPE_LENGTH} Zeichen lang. Ein Endpunkt <strong>muss</strong>
 * mindestens einen Ereignistyp abonnieren — sonst haette er nichts zu liefern.
 */
public record EndpointEventTypes(List<String> values) {

    /** Die groesste Zahl abonnierter Ereignistypen. */
    public static final int MAX_EVENT_TYPES = 100;

    /** Die groesste Laenge eines Ereignistyps. */
    public static final int MAX_EVENT_TYPE_LENGTH = 200;

    /** Der Feldname im Vertrag. */
    private static final String FIELD = "event_types";

    /**
     * Erzeugt die Ereignistypen mit unabhaengiger Liste.
     *
     * @param values die Ereignistypen
     */
    public EndpointEventTypes {
        values = values == null ? List.of() : List.copyOf(values);
    }

    /**
     * Prueft und erzeugt die Ereignistypen.
     *
     * @param values die Ereignistypen aus dem Request
     * @return die geprueften Ereignistypen
     * @throws ValidationException wenn die Liste das Schema verletzt
     */
    public static EndpointEventTypes of(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new ValidationException(FIELD, "event_types_required",
                    "Mindestens ein Ereignistyp ist erforderlich.");
        }
        if (values.size() > MAX_EVENT_TYPES) {
            throw new ValidationException(FIELD, "too_many_event_types",
                    "Es duerfen hoechstens " + MAX_EVENT_TYPES + " Ereignistypen abonniert werden.");
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new ValidationException(FIELD, "event_type_invalid",
                        "Ein Ereignistyp darf nicht leer sein.");
            }
            if (value.length() > MAX_EVENT_TYPE_LENGTH) {
                throw new ValidationException(FIELD, "event_type_too_long",
                        "Ein Ereignistyp darf hoechstens " + MAX_EVENT_TYPE_LENGTH + " Zeichen lang sein.");
            }
            if (!unique.add(value)) {
                throw new ValidationException(FIELD, "duplicate_event_types",
                        "Die Ereignistypen muessen eindeutig sein.");
            }
        }
        return new EndpointEventTypes(List.copyOf(unique));
    }

    /**
     * Liefert die Ereignistypen.
     *
     * @return die Ereignistypen
     */
    public List<String> values() {
        return values;
    }

    /**
     * Liefert die Zahl der abonnierten Ereignistypen.
     *
     * @return die Zahl
     */
    public int size() {
        return values.size();
    }

    /**
     * Prueft, ob der Ereignistyp abonniert ist.
     *
     * @param eventType der Ereignistyp
     * @return {@code true}, wenn der Typ abonniert ist
     */
    public boolean contains(String eventType) {
        return values.contains(eventType);
    }
}
