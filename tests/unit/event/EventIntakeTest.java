package com.echobox.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit-Ebene — Einlieferung eines Ereignisses (US-005, FR-010, NFR-001).
 *
 * <p>Getestet wird die reine Groessenlogik: die Grenze betrifft die
 * <em>Nutzlastbytes</em>, nicht den Request-Body. Bei vorverschluesselter
 * Einlieferung sind die Nutzlastbytes die Chiffre.
 *
 * <p>Testorakel: `docs/api/openapi.yaml` -&gt;
 * {@code EventIntakeRequest.x-validation-rules.payload_size_limit} und die
 * Beschreibung von {@code submitEvent} (Nutzlast 1.048.576 Byte, Request-Body bis 2 MiB).
 *
 * <p>Abgedeckte Akzeptanzkriterien: AC-005-2, AC-005-3.
 */
class EventIntakeTest {

    @Test
    @DisplayName("AC-005-2: eine Nutzlast von exakt 1.048.576 Byte (1 MB) wird angenommen")
    void payload_of_exactly_one_megabyte_is_accepted() {
        assertThat(PayloadSize.exceedsPayloadLimit(PayloadSize.MAX_PAYLOAD_BYTES)).isFalse();
        assertThat(PayloadSize.exceedsPayloadLimit(1_048_576L)).isFalse();
    }

    @Test
    @DisplayName("AC-005-3: eine Nutzlast von 1.048.577 Byte wird abgelehnt (HTTP 413)")
    void payload_one_byte_over_the_limit_is_rejected() {
        assertThat(PayloadSize.exceedsPayloadLimit(1_048_577L)).isTrue();
        assertThat(PayloadSize.MAX_PAYLOAD_BYTES).isEqualTo(1_048_576);
    }

    @ParameterizedTest(name = "AC-005-3: {0} Byte ueberschreiten die Grenze")
    @ValueSource(longs = {1_048_577L, 2_000_000L, 10_000_000L})
    @DisplayName("AC-005-3: alles ueber der Grenze wird abgelehnt")
    void every_payload_above_the_limit_is_rejected(long size) {
        assertThat(PayloadSize.exceedsPayloadLimit(size)).isTrue();
    }

    @Test
    @DisplayName("AC-005-2: die Grenze misst die Nutzlastbytes, nicht den Request-Body")
    void the_limit_measures_payload_bytes_not_the_request_body() {
        int payloadBytes = PayloadSize.MAX_PAYLOAD_BYTES;

        assertThat(PayloadSize.exceedsPayloadLimit(payloadBytes))
                .as("die Nutzlast liegt genau auf der Grenze")
                .isFalse();
        assertThat(PayloadSize.exceedsRequestBodyLimit(payloadBytes))
                .as("ein base64-kodierter Body ist rund 1,4-mal so gross und bleibt unter 2 MiB")
                .isFalse();
    }

    @Test
    @DisplayName("AC-005-3: ein Request-Body ueber 2 MiB wird abgelehnt")
    void request_body_above_two_mebibytes_is_rejected() {
        assertThat(PayloadSize.exceedsRequestBodyLimit(2 * 1024 * 1024L + 1L)).isTrue();
        assertThat(PayloadSize.MAX_REQUEST_BODY_BYTES).isEqualTo(2 * 1024 * 1024);
    }
}
