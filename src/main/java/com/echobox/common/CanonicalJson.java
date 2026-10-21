package com.echobox.common;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Kanonische Materialisierung eines JSON-Wertes (Vertrag, Schema
 * {@code EventIntakeRequest.payload}: „materialisiert ihn **kanonisch**
 * (RFC 8785, UTF-8, sortierte Schluessel, ohne bedeutungslose Leerzeichen)“).
 *
 * <p>Die zugestellten Bytes sind **genau diese** Bytes — nicht der Rohtext, den
 * der Aufrufer gesendet hat. Damit erzeugt derselbe fachliche Inhalt stets
 * denselben Fingerabdruck (ADR-007) und derselbe Idempotenzschluessel laesst
 * sich unabhaengig von der Schreibweise des Aufrufers vergleichen.
 *
 * <p>Die Regeln:
 * <ul>
 *   <li>Objektschluessel werden nach UTF-16-Codeeinheiten sortiert,</li>
 *   <li>Arrays behalten ihre Reihenfolge,</li>
 *   <li>Zahlen ohne ueberfluessige Nullen und ohne Exponentform, sofern der
 *       Wert ganzzahlig ist,</li>
 *   <li>Zeichenketten werden mit den ueblichen JSON-Escapes geschrieben,</li>
 *   <li>das Ergebnis ist UTF-8.</li>
 * </ul>
 */
public final class CanonicalJson {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private CanonicalJson() {
        // statische Helferklasse
    }

    /**
     * Materialisiert einen JSON-Wert kanonisch.
     *
     * @param value der JSON-Wert
     * @return die kanonischen UTF-8-Bytes
     */
    public static byte[] canonicalize(JsonNode value) {
        StringBuilder builder = new StringBuilder();
        write(value, builder);
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void write(JsonNode value, StringBuilder out) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            out.append("null");
            return;
        }
        if (value.isObject()) {
            writeObject((ObjectNode) value, out);
            return;
        }
        if (value.isArray()) {
            writeArray((ArrayNode) value, out);
            return;
        }
        if (value.isTextual()) {
            out.append(quote(value.textValue()));
            return;
        }
        if (value.isBoolean()) {
            out.append(value.booleanValue() ? "true" : "false");
            return;
        }
        if (value.isNumber()) {
            writeNumber(value, out);
            return;
        }
        out.append("null");
    }

    private static void writeObject(ObjectNode node, StringBuilder out) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        Collections.sort(names);
        out.append('{');
        boolean first = true;
        for (String name : names) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append(quote(name)).append(':');
            write(node.get(name), out);
        }
        out.append('}');
    }

    private static void writeArray(ArrayNode node, StringBuilder out) {
        out.append('[');
        for (int index = 0; index < node.size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            write(node.get(index), out);
        }
        out.append(']');
    }

    private static void writeNumber(JsonNode value, StringBuilder out) {
        if (value.isIntegralNumber()) {
            out.append(value.bigIntegerValue().toString());
            return;
        }
        double number = value.doubleValue();
        if (Double.isNaN(number) || Double.isInfinite(number)) {
            out.append("null");
            return;
        }
        if (number == Math.rint(number) && Math.abs(number) < 1e21) {
            out.append(BigDecimal.valueOf(number).toBigInteger().toString());
            return;
        }
        double absolute = Math.abs(number);
        if (absolute >= 1e-6 && absolute < 1e21) {
            out.append(BigDecimal.valueOf(number).stripTrailingZeros().toPlainString());
            return;
        }
        out.append(number);
    }

    private static String quote(String text) {
        try {
            return MAPPER.writeValueAsString(text);
        } catch (IOException exception) {
            throw new IllegalStateException("Der Wert konnte nicht serialisiert werden.", exception);
        }
    }
}
