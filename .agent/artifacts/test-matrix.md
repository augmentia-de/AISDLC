# Testmatrix — EchoBox

> Schritt 5 (Test-Driven Setup) · Agent: `aisdlc-execution` · Stand: 2026-10-21
>
> **Quelle der Akzeptanzkriterien:** die **101 AKs** in `docs/requirements/stories/US-0xx.json`
> (je `id`, `given`/`when`/`then`). Die Gherkin-Features sind der lesbare Smoke-Layer,
> **nicht** die Matrixquelle. **Testorakel:** der eingefrorene Vertrag
> `docs/api/openapi.yaml`, das kanonische Schema `.agent/knowledge/schema.sql` und die ADRs.
>
> **Alle 101 Akzeptanzkriterien haben mindestens einen Testfall** (305 Testfaelle in 58
> Testdateien; `tests/` enthaelt 70 Dateien — 58 mit Testfaellen, 11 Hilfsdateien und
> 2 maschinenlesbare Vertragsspezifikationen). Die Liste der nicht abgedeckten AKs in
> Abschnitt 3 ist leer.
> Maschinell nachweisbar: jede AC-ID kommt genau einmal als Zeile vor, und **jeder**
> Testfall nennt seine AC-ID im Namen (`@DisplayName` bzw. `it('AC-…')`).

## 1 — Matrix je Akzeptanzkriterium

Je AC-ID **eine** Zeile. Enthaelt ein Kriterium mehrere Testfaelle, stehen sie in der
Zelle — Reihenfolge: Integration vor Unit, Vertrag vor Last.

### US-001

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-001-1 | Integration, Unit | `tests/integration/endpoint/EndpointRegistrationIT.java`<br>`tests/integration/security/RoleCapabilityMatrixIT.java`<br>`tests/unit/endpoint/EndpointRegistrationTest.java` | `a_developer_may_not_register_an_endpoint()`<br>`a_malformed_body_is_a_bad_request()`<br>`an_unauthenticated_request_is_rejected()`<br>`registration_creates_an_active_endpoint()`<br>`the_response_carries_the_location_header()`<br>`the_tenant_comes_from_the_identity()`<br>`the_developer_does_not_manage_endpoints()`<br>`https_target_url_is_accepted()`<br>`https_target_url_with_port_and_path_is_accepted()` | ein Entwickler darf keinen Endpunkt registrieren (403); ein syntaktisch falscher Body ergibt 400, kein 422; ohne Token antwortet der Dienst mit 401; die Registrierung liefert 201, Status 'aktiv' und die ID des Endpunkts; die Antwort traegt den Location-Header des neuen Endpunkts; der Mandant stammt aus der Identitaet, nicht aus der Anfrage; der Entwickler verwaltet keine Endpunkte; eine https-Ziel-URL mit Host wird akzeptiert; eine https-Ziel-URL mit Port und Pfad wird akzeptiert | FR-001 |
| AC-001-2 | Integration, Unit | `tests/integration/endpoint/EndpointRegistrationIT.java`<br>`tests/integration/security/ForeignResourceIsolationIT.java`<br>`tests/unit/endpoint/EndpointRegistrationTest.java` | `a_non_https_target_url_is_rejected()`<br>`updating_a_foreign_endpoint_yields_not_found()`<br>`non_https_scheme_is_rejected()`<br>`scheme_is_checked_before_credentials()` | eine Ziel-URL ohne https wird mit 422 abgelehnt und legt keinen Endpunkt an; das Aktualisieren eines fremden Endpunkts ergibt 404; ohne https-Schema -> Verstoß scheme_not_allowed (HTTP 422); die Pruefung erkennt das Schema vor den Zugangsdaten | FR-001 |
| AC-001-3 | Integration, Unit | `tests/integration/endpoint/EndpointRegistrationIT.java`<br>`tests/unit/endpoint/EndpointRegistrationTest.java` | `embedded_credentials_are_rejected()`<br>`embedded_credentials_are_rejected()`<br>`embedded_user_information_is_rejected()` | eingebettete Zugangsdaten werden mit 422 abgelehnt; eingebettete Zugangsdaten werden abgelehnt (credentials_not_allowed); der Benutzername allein ohne Passwort wird ebenfalls abgelehnt | FR-001 |
| AC-001-4 | Integration | `tests/integration/endpoint/EndpointRegistrationIT.java` | `the_response_never_contains_the_plaintext_secret()`<br>`the_secret_is_stored_encrypted()` | die Antwort enthaelt das Secret nicht im Klartext; das Secret wird verschluesselt abgelegt | FR-001 |
| AC-001-5 | Integration | `tests/integration/endpoint/EndpointRegistrationIT.java` | `a_tenant_parameter_in_the_body_is_rejected()` | ein Mandantenparameter im Body wird mit 422 abgelehnt | FR-001 |

### US-002

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-002-1 | Integration, Unit | `tests/integration/endpoint/EndpointPauseIT.java`<br>`tests/unit/endpoint/EndpointPauseTest.java` | `a_paused_endpoint_creates_no_delivery_run()`<br>`after_pause_no_attempt_is_made()`<br>`paused_is_not_an_active_delivery_target()` | fuer einen pausierten Endpunkt entsteht kein Zustellvorgang; nach der Pause entsteht kein Zustellversuch; ein Endpunkt im Zustand 'paused' ist kein aktiver Endpunkt mehr | FR-002 |
| AC-002-2 | Integration | `tests/integration/endpoint/EndpointPauseIT.java`<br>`tests/integration/security/ForeignResourceIsolationIT.java` | `after_resume_nothing_is_delivered_afterwards()`<br>`resuming_a_foreign_endpoint_yields_not_found()` | nach der Reaktivierung wird nichts nachgeliefert; das Reaktivieren eines fremden Endpunkts ergibt 404 | FR-002 |
| AC-002-3 | Integration | `tests/integration/endpoint/EndpointPauseIT.java`<br>`tests/integration/security/ForeignResourceIsolationIT.java` | `a_developer_may_not_pause_an_endpoint()`<br>`a_foreign_endpoint_yields_not_found()`<br>`pausing_a_foreign_endpoint_yields_not_found()` | ein Entwickler darf den Endpunkt nicht pausieren (403); ein fremder Endpunkt ergibt 404 und bleibt unveraendert; das Pausieren eines fremden Endpunkts ergibt 404 | FR-002 |
| AC-002-4 | Integration, Unit | `tests/integration/endpoint/EndpointPauseIT.java`<br>`tests/unit/endpoint/EndpointPauseTest.java` | `an_open_retry_is_cancelled()`<br>`cancelled_run_is_terminal_without_next_attempt()`<br>`pause_cancels_a_scheduled_run()`<br>`pause_cancels_an_open_retry()`<br>`pause_does_not_touch_a_terminal_run()` | ein offener Wiederholungsvorgang wird abgebrochen; ein abgebrochener Lauf ist endgueltig und hat keinen naechsten Versuch; die Pause bricht auch einen laufenden Versuch ab; die Pause bricht einen offenen Wiederholungsvorgang ab; die Pause trifft keinen endgueltigen Lauf | FR-002 |
| AC-002-5 | Integration | `tests/integration/endpoint/EndpointPauseIT.java` | `the_attempt_count_stays_unchanged()`<br>`the_endpoint_list_shows_the_paused_status()` | die Anzahl der Zustellversuche bleibt unveraendert; die Endpunktliste zeigt den Status 'pausiert' | FR-002 |

### US-003

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-003-1 | Integration, Unit | `tests/integration/endpoint/SecretRotationIT.java`<br>`tests/unit/endpoint/SecretRotationTest.java` | `a_too_short_secret_is_rejected()`<br>`after_rotation_only_the_new_secret_is_stored()`<br>`rotation_increases_the_secret_version()`<br>`after_rotation_the_signature_is_produced_with_the_new_secret_only()`<br>`signature_carries_the_version_prefix()` | ein zu kurzes Secret wird mit 422 abgelehnt; nach der Rotation gilt nur das neue Secret; die Rotation erhoeht die Secret-Version; nach der Rotation wird ausschliesslich mit dem neuen Secret signiert; die Signatur traegt das Versionspraefix v1= | FR-003 |
| AC-003-2 | Integration | `tests/integration/endpoint/SecretRotationIT.java` | `a_second_rotation_creates_a_second_history_entry()`<br>`the_old_secret_is_never_returned_in_plaintext()`<br>`the_rotation_history_records_time_and_actor()` | eine zweite Rotation erzeugt einen zweiten Eintrag in der Historie; das alte Secret wird nirgends im Klartext ausgegeben; die Rotationshistorie vermerkt Zeitpunkt und Identitaet | FR-003 |
| AC-003-3 | Integration | `tests/integration/endpoint/SecretRotationIT.java`<br>`tests/integration/security/ForeignResourceIsolationIT.java`<br>`tests/integration/security/RoleCapabilityMatrixIT.java` | `a_developer_may_not_rotate()`<br>`a_foreign_endpoint_yields_not_found()`<br>`rotating_a_foreign_endpoint_yields_not_found()`<br>`the_developer_does_not_rotate_secrets()`<br>`the_service_admin_does_not_rotate_secrets()` | ein Entwickler darf nicht rotieren (403) — das Secret bleibt unveraendert; ein fremder Endpunkt ergibt 404 — auch fuer den Service-Admin; die Secret-Rotation eines fremden Endpunkts ergibt 404; der Entwickler rotiert kein Secret; der Service-Admin rotiert kein Secret | FR-003 |
| AC-003-4 | Unit | `tests/unit/endpoint/SecretRotationTest.java` | `signature_verification_follows_the_rotated_secret()` | die Signatur mit S2 wird bestaetigt, die mit S1 schlaegt fehl | FR-003 |

### US-004

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-004-1 | Integration | `tests/integration/endpoint/EndpointVisibilityIT.java`<br>`tests/integration/security/AlwaysPresentFieldsIT.java` | `the_database_still_holds_both_tenants()`<br>`the_list_of_a_developer_is_tenant_scoped()`<br>`the_list_of_a_receiver_admin_is_tenant_scoped()`<br>`organisation_id_is_always_filled_in_the_endpoint_list()` | die Liste ist mandantengefiltert — die Datenbank behauptet nichts anderes; die Liste eines Entwicklers zeigt ebenfalls nur seinen Mandanten; die Endpunktliste eines Empfaenger-Admins zeigt nur seinen Mandanten; organisation_id ist in der Endpunktliste immer gefuellt | FR-004 |
| AC-004-2 | Integration, Vertrag | `tests/integration/endpoint/EndpointVisibilityIT.java`<br>`tests/integration/security/AlwaysPresentFieldsIT.java`<br>`tests/integration/security/RoleCapabilityMatrixIT.java`<br>`tests/contract/AlwaysPresentFieldsContractTest.java` | `every_cross_tenant_record_names_its_owner()`<br>`the_service_admin_sees_all_tenants()`<br>`organisation_id_names_the_owner_in_the_cross_tenant_view()`<br>`the_capability_matrix_is_enforced()`<br>`the_service_admin_manages_endpoints_in_the_own_tenant()`<br>`organisation_id_is_described_as_always_filled()`<br>`organisation_id_is_required_wherever_it_is_promised()` | jeder Datensatz der mandantenuebergreifenden Liste traegt den besitzenden Mandanten; der Service-Admin sieht die Endpunkte aller Mandanten; organisation_id ist in der mandantenuebergreifenden Sicht der besitzende Mandant; die Rollenmatrix gilt je (Rolle, Faehigkeit) — auch fuer den Service-Admin im eigenen Mandanten; der Service-Admin verwaltet Endpunkte im eigenen Mandanten (im Vertrag zugesagt, ohne eigenes AK); organisation_id wird im Vertrag als 'immer gefuellt' beschrieben; organisation_id ist in Endpoint, DeliveryRun und DeadDelivery pflicht | FR-004 |
| AC-004-3 | Integration | `tests/integration/endpoint/EndpointVisibilityIT.java`<br>`tests/integration/security/AlwaysPresentFieldsIT.java`<br>`tests/integration/security/ForeignResourceIsolationIT.java` | `even_the_service_admin_gets_not_found_for_a_single_foreign_endpoint()`<br>`reading_a_foreign_endpoint_yields_not_found()`<br>`organisation_id_is_always_filled_in_the_single_endpoint()`<br>`reading_a_foreign_endpoint_yields_not_found()` | auch der Service-Admin bekommt beim Einzelabruf 404; der Einzelabruf eines fremden Endpunkts ergibt 404 ohne Daten; organisation_id ist im einzelnen Endpunkt immer gefuellt; das Lesen eines fremden Endpunkts ergibt 404 | FR-004 |
| AC-004-4 | Integration | `tests/integration/endpoint/EndpointVisibilityIT.java` | `a_tenant_parameter_in_the_query_is_rejected()`<br>`the_tenant_of_the_answer_is_the_tenant_of_the_identity()` | ein Mandantenparameter im Query wird mit 422 abgelehnt; der Mandant der Antwort ist immer der Mandant der Identitaet | FR-004 |

### US-005

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-005-1 | Integration | `tests/integration/event/EventIntakeIT.java`<br>`tests/integration/security/RoleCapabilityMatrixIT.java` | `a_receiver_admin_may_not_submit_events()`<br>`an_event_without_subscriber_is_visible_as_no_subscriber()`<br>`the_delivery_starts_after_the_response()`<br>`the_event_is_accepted_with_202()`<br>`the_payload_size_is_reported_in_the_metadata()`<br>`the_service_admin_does_not_submit_events()` | ein Empfaenger-Admin darf kein Ereignis einliefern (403); ohne Abnehmer traegt das Ereignis den Status 'no_subscriber'; die Zustellung startet erst nach der Antwort; die Einlieferung wird mit 202 angenommen und liefert die Ereignis-ID; die Nutzlast wird in den Metadaten als Groesse ausgewiesen; der Service-Admin liefert keine Ereignisse ein | FR-010, NFR-001, NFR-002 |
| AC-005-2 | Integration, Unit | `tests/integration/event/EventIntakeIT.java`<br>`tests/unit/event/EventIntakeTest.java` | `a_payload_of_exactly_one_megabyte_is_accepted()`<br>`payload_of_exactly_one_megabyte_is_accepted()`<br>`the_limit_measures_payload_bytes_not_the_request_body()` | eine Nutzlast von exakt 1.048.576 Byte wird angenommen; eine Nutzlast von exakt 1.048.576 Byte (1 MB) wird angenommen; die Grenze misst die Nutzlastbytes, nicht den Request-Body | FR-010, NFR-001, NFR-002 |
| AC-005-3 | Integration, Unit | `tests/integration/event/EventIntakeIT.java`<br>`tests/unit/event/EventIntakeTest.java` | `a_payload_one_byte_over_the_limit_is_rejected()`<br>`every_payload_above_the_limit_is_rejected()`<br>`payload_one_byte_over_the_limit_is_rejected()`<br>`request_body_above_two_mebibytes_is_rejected()` | eine Nutzlast von 1.048.577 Byte wird mit 413 abgelehnt; alles ueber der Grenze wird abgelehnt; eine Nutzlast von 1.048.577 Byte wird abgelehnt (HTTP 413); ein Request-Body ueber 2 MiB wird abgelehnt | FR-010, NFR-001, NFR-002 |
| AC-005-4 | Integration | `tests/integration/event/EventIntakePerformanceIT.java` | `a_slow_receiver_does_not_block_the_intake_request()` | ein langsamer Empfaenger blockiert den Annahme-Request nicht | FR-010, NFR-001, NFR-002 |
| AC-005-5 | Integration, Last | `tests/integration/event/EventIntakePerformanceIT.java`<br>`tests/performance/EventIntakeLoadTest.java` | `the_p95_of_the_intake_response_is_below_200_millis()`<br>`every_intake_is_accepted()`<br>`the_p95_over_a_thousand_intakes_is_below_200_millis()` | die p95 der Annahmeantworten liegt unter 200 ms; alle 1.000 Einlieferungen werden angenommen; die p95 der Annahmeantworten liegt ueber 1.000 Einlieferungen unter 200 ms | FR-010, NFR-001, NFR-002 |
| AC-005-6 | Integration | `tests/integration/event/EventIntakeIT.java` | `an_event_without_idempotency_key_is_rejected()`<br>`an_event_without_type_is_rejected()` | eine Einlieferung ohne Idempotenzschluessel wird mit 422 abgelehnt; eine Einlieferung ohne Ereignistyp wird mit 422 abgelehnt | FR-010, NFR-001, NFR-002 |

### US-006

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-006-1 | Integration, Unit | `tests/integration/event/EventIdempotencyIT.java`<br>`tests/unit/event/EventIdempotencyTest.java` | `the_replay_returns_the_id_of_the_first_event()`<br>`same_payload_yields_a_replay()`<br>`unknown_key_yields_a_new_event()` | die Wiederholung mit gleicher Nutzlast liefert die ID des ersten Ereignisses; dieselbe Nutzlast ergibt eine Wiederholung, kein zweites Ereignis; ein unbekannter Schluessel ergibt ein neues Ereignis | FR-011 |
| AC-006-2 | Integration, Unit | `tests/integration/event/EventIdempotencyIT.java`<br>`tests/unit/event/EventIdempotencyTest.java` | `no_second_event_is_created()`<br>`the_first_event_is_never_overwritten()`<br>`fingerprint_does_not_contain_the_plaintext()`<br>`fingerprint_is_stable_for_equal_payloads()` | es entsteht kein zweites Ereignis; Typ und Nutzlast des ersten Ereignisses bleiben unveraendert; der Fingerprint enthaelt keinen Klartextanteil; der Fingerprint ist bei gleicher Nutzlast identisch | FR-011 |
| AC-006-3 | Integration | `tests/integration/event/EventIdempotencyIT.java` | `the_replay_in_m1_is_untouched_by_m2()`<br>`the_same_key_in_another_tenant_creates_its_own_event()` | die Wiederholung in M1 bleibt von M2 unberuehrt; derselbe Schluessel in einem anderen Mandanten ergibt ein eigenes Ereignis | FR-011 |
| AC-006-4 | Integration, Unit | `tests/integration/event/EventIdempotencyIT.java`<br>`tests/unit/event/EventIdempotencyTest.java` | `a_differing_payload_yields_a_conflict()`<br>`the_first_event_survives_the_conflict()`<br>`differing_payload_yields_a_conflict()` | dieselbe Nutzlast mit abweichendem Inhalt ergibt 409; das erste Ereignis bleibt nach dem Konflikt unveraendert; eine abweichende Nutzlast ergibt einen Konflikt (HTTP 409) | FR-011 |

### US-007

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-007-1 | Integration | `tests/integration/event/PayloadEncryptionAtRestIT.java` | `the_plaintext_marker_is_nowhere_in_the_database_dump()`<br>`the_plaintext_marker_is_nowhere_in_the_indexes()`<br>`the_plaintext_marker_is_nowhere_in_the_server_log()`<br>`the_plaintext_marker_is_nowhere_in_the_tables()` | die Klartextmarkierung steht im vollstaendigen Datenbank-Dump nicht; die Klartextmarkierung steht in keinem Index; die Klartextmarkierung steht im Server-Protokoll nicht; die Klartextmarkierung steht in keiner Tabelle der Datenbank | FR-012, NFR-004 |
| AC-007-2 | Integration, Unit | `tests/integration/event/PayloadEncryptionAtRestIT.java`<br>`tests/unit/crypto/PayloadCipherTest.java` | `the_plaintext_marker_is_nowhere_in_the_application_log()`<br>`the_nonce_has_the_aead_length()` | die Klartextmarkierung steht in keinem Anwendungsprotokoll; die Nonce ist 12 Byte lang (AEAD-Standard) | FR-012, NFR-004 |
| AC-007-3 | Integration, Unit | `tests/integration/event/PayloadEncryptionAtRestIT.java`<br>`tests/unit/crypto/PayloadCipherTest.java` | `the_payload_is_stored_as_ciphertext()`<br>`encrypt_decrypt_round_trip_returns_the_same_bytes()`<br>`encryption_uses_a_fresh_nonce_per_call()`<br>`the_ciphertext_does_not_contain_the_plaintext()` | die Chiffre steht in der Datenbank und laesst sich nicht lesen; der Round-Trip liefert exakt die eingelieferten Bytes zurueck; die Chiffre ist bei gleicher Eingabe nicht identisch (frische Nonce); die Chiffre enthaelt den Klartext nicht | FR-012, NFR-004 |
| AC-007-4 | Unit | `tests/unit/crypto/PayloadCipherTest.java` | `the_encryption_key_is_not_hardcoded_in_the_project()`<br>`the_schema_contains_no_key_material()` | der Verschluesselungsschluessel steht in keinem Projektartefakt; das Schema enthaelt keinen eingebetteten Schluessel | FR-012, NFR-004 |
| AC-007-5 | Integration | `tests/integration/event/PayloadEncryptionAtRestIT.java`<br>`tests/integration/protocol/DeliveryProtocolIT.java` | `the_protocol_searches_metadata_only()`<br>`the_search_runs_over_metadata_only()` | das Zustellprotokoll kennt nur Metadaten — keine Suche nach Nutzlastinhalt; die Suche laeuft ueber Metadaten — die Nutzlast wird nie ausgegeben | FR-012, NFR-004 |

### US-008

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-008-1 | Integration, Unit | `tests/integration/event/PreEncryptedEventIT.java`<br>`tests/unit/event/PreEncryptedEventTest.java` | `the_bytes_are_stored_unchanged()`<br>`the_size_of_the_ciphertext_is_reported()`<br>`plaintext_payload_is_marked_as_plaintext()`<br>`pre_encrypted_bytes_are_taken_over_unchanged()`<br>`pre_encrypted_payload_is_not_decrypted()` | die Bytes werden unveraendert gespeichert; die Groesse der Chiffre wird ausgewiesen; eine Klartextnutzlast wird als Klartext gekennzeichnet; vorverschluesselte Bytes werden unveraendert uebernommen; eine vorverschluesselte Nutzlast wird nicht entschluesselt | FR-013 |
| AC-008-2 | Integration | `tests/integration/event/PreEncryptedEventIT.java` | `a_pre_encrypted_submission_with_plaintext_is_rejected()`<br>`pre_encrypted_requires_base64()` | eine vorverschluesselte Einlieferung mit Klartextnutzlast wird mit 422 abgelehnt; vorverschluesselt verlangt payload_base64 | FR-013 |
| AC-008-3 | Integration, Unit | `tests/integration/event/PreEncryptedEventIT.java`<br>`tests/unit/event/PreEncryptedEventTest.java` | `the_same_limit_applies_to_pre_encrypted_payloads()`<br>`the_same_size_limit_applies_to_the_ciphertext()` | dieselbe Grenze gilt fuer die vorverschluesselte Einlieferung; dieselbe Grenze gilt fuer die Chiffre | FR-013 |

### US-009

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-009-1 | Integration, Unit | `tests/integration/delivery/SignedDeliveryIT.java`<br>`tests/unit/crypto/SignatureTest.java` | `the_delivery_carries_a_verifiable_signature()`<br>`the_real_http_request_carries_the_frozen_headers()`<br>`the_request_body_is_the_payload_bytes_without_an_envelope()`<br>`the_header_value_carries_the_version_prefix()`<br>`the_receiver_can_recompute_the_signature()` | die Zustellung traegt eine Signatur, die der Empfaenger nachrechnen kann; der echte HTTP-Request traegt die eingefrorenen Kopfzeilen; der Request-Body sind genau die Nutzlastbytes — keine Huelle; der Headerwert traegt das Versionspraefix und hex-lowercase; der Empfaenger kann die Signatur mit dem Secret nachrechnen | FR-020 |
| AC-009-2 | Integration, Unit | `tests/integration/delivery/SignedDeliveryIT.java`<br>`tests/unit/crypto/SignatureTest.java` | `a_signature_over_a_tampered_payload_fails()`<br>`a_missing_signature_is_not_accepted()`<br>`a_tampered_payload_fails_the_verification()`<br>`a_wrong_secret_fails_the_verification()` | eine Signatur mit veränderter Nutzlast schlaegt fehl; eine fehlende Signatur wird nicht akzeptiert; eine veraenderte Nutzlast schlaegt fehl; ein falsches Secret schlaegt fehl | FR-020 |
| AC-009-3 | Vertrag, Unit | `tests/contract/SignatureContractTest.java`<br>`tests/unit/delivery/DeliveryIdentifiersTest.java` | `the_contract_names_the_signature_algorithm()`<br>`the_contract_names_the_signature_header()`<br>`the_contract_names_the_signing_input()`<br>`the_implementation_uses_the_contract_header_name()`<br>`the_outgoing_delivery_contract_is_marked_frozen()`<br>`the_signature_header_is_mandatory_in_the_outgoing_contract()`<br>`the_signature_header_carries_the_versioned_value()` | der Vertrag nennt das Signaturverfahren; der Vertrag nennt den Header-Namen der Signatur; die Signier-Eingabe ist festgeschrieben; die Implementierung benutzt den Header-Namen des Vertrags; der ausgehende Zustellvertrag ist als eingefroren markiert; die Signatur-Kopfzeile ist im ausgehenden Vertrag als Pflicht gefuehrt; der Signatur-Header traegt den Wert v1=<hex-lowercase> | FR-020 |
| AC-009-4 | Integration, Unit | `tests/integration/delivery/SignedDeliveryIT.java`<br>`tests/unit/crypto/SignatureTest.java` | `two_endpoints_receive_different_signatures()`<br>`two_endpoints_produce_different_signatures_for_the_same_payload()` | zwei Endpunkte mit derselben Nutzlast erhalten verschiedene Signaturen | FR-020 |
| AC-009-5 | Integration, Unit | `tests/integration/delivery/SignedDeliveryIT.java`<br>`tests/unit/crypto/SignatureTest.java` | `the_timestamp_is_utc_and_the_signature_covers_the_sent_bytes()`<br>`the_signature_covers_exactly_the_sent_payload_bytes()`<br>`the_timestamp_is_utc_based()` | der Zeitstempel ist UTC-basiert und die Signatur deckt genau die gesendeten Bytes; die Signatur wird ueber genau die gesendeten Nutzlastbytes gebildet; der Zeitstempel ist UTC-basiert und endet auf Z | FR-020 |

### US-010

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-010-1 | Integration, Unit | `tests/integration/delivery/AtLeastOnceDeliveryIT.java`<br>`tests/unit/delivery/DeliveryIdentifiersTest.java` | `every_attempt_carries_the_three_identifiers()`<br>`every_attempt_carries_the_three_identifiers()`<br>`the_header_names_are_the_frozen_contract_names()`<br>`the_user_agent_identifies_the_delivery_service()` | jeder Versuch traegt Ereignis-ID, Zustelllauf-ID und Versuchs-ID; die Header-Namen sind die eingefrorenen Namen des Vertrags; der Nutzer-Agent meldet den Zustelldienst | FR-021 |
| AC-010-2 | Integration, Unit | `tests/integration/delivery/AtLeastOnceDeliveryIT.java`<br>`tests/unit/delivery/DeliveryIdentifiersTest.java` | `the_timeout_duplicate_shares_the_run_id_but_not_the_attempt_id()`<br>`two_attempts_of_one_run_share_the_run_id_but_not_the_attempt_id()` | die Timeout-Dublette traegt gleiche Lauf-ID, aber verschiedene Versuchs-IDs; zwei Versuche desselben Laufs tragen dieselbe Zustelllauf-ID, aber verschiedene Versuchs-IDs | FR-021 |
| AC-010-3 | Integration, Unit | `tests/integration/delivery/AtLeastOnceDeliveryIT.java`<br>`tests/unit/delivery/PermanentRejectionTest.java` | `no_further_attempt_follows_a_2xx()`<br>`a_2xx_response_is_a_success()` | nach einer 2xx-Antwort erfolgt kein weiterer Versuch; eine 2xx-Antwort ist ein Erfolg | FR-021 |
| AC-010-4 | Integration, Unit | `tests/integration/delivery/AtLeastOnceDeliveryIT.java`<br>`tests/unit/delivery/DeliveryIdentifiersTest.java` | `two_endpoints_produce_two_runs_with_the_same_event_id()`<br>`two_runs_of_one_event_carry_different_run_ids()` | ein Ereignis an zwei Endpunkte erzeugt zwei Laeufe mit derselben Ereignis-ID; zwei Laeufe desselben Ereignisses tragen verschiedene Zustelllauf-IDs | FR-021 |

### US-011

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-011-1 | Integration, Unit | `tests/integration/delivery/RetryDeliveryIT.java`<br>`tests/unit/delivery/BackoffPolicyTest.java` | `a_network_error_is_retried_like_a_server_error()`<br>`the_next_attempt_is_planned_with_double_the_distance()`<br>`every_value_is_configurable()`<br>`the_delay_doubles_with_every_attempt()`<br>`the_delay_is_at_least_double_the_previous_one()`<br>`the_delay_never_exceeds_the_ceiling()` | ein Netzwerkfehler wird wie ein 5xx behandelt; nach einem 5xx wird der naechste Versuch mit dem doppelten Abstand geplant; alle Werte sind konfigurierbar; der Abstand verdoppelt sich mit jedem Versuch; der Abstand ist mindestens doppelt so hoch wie der vorherige; der Deckel von 60 Minuten wird nicht ueberschritten | FR-022 |
| AC-011-2 | Unit | `tests/unit/delivery/JitterTest.java` | `a_hundred_simultaneous_failures_get_different_time_points()`<br>`the_jitter_is_centred_on_the_base_delay()`<br>`the_jitter_stays_inside_the_configured_band()`<br>`without_jitter_the_time_points_are_identical()` | 100 gleichzeitig fehlschlagende Endpunkte erhalten verschiedene Zeitpunkte; die Streuung ist symmetrisch um den Basisabstand; die Streuung bleibt innerhalb der Konfiguration (+/- 20 %); ohne Streuung sind die Zeitpunkte identisch — der Kontrast belegt die Streuung | FR-022 |
| AC-011-3 | Integration | `tests/integration/delivery/RetryDeliveryIT.java` | `the_intake_request_answers_although_the_receiver_is_slow()`<br>`the_retry_runs_in_the_background_process()` | der Annahme-Request antwortet, obwohl der Empfaenger langsam ist; die Wiederholung laeuft im Hintergrundprozess, nicht im Annahme-Request | FR-022 |
| AC-011-4 | Integration | `tests/integration/delivery/RetryDeliveryIT.java` | `a_second_worker_run_makes_no_further_attempt()`<br>`no_further_attempt_is_planned_after_a_success()` | ein zweiter Durchlauf des Workers erzeugt keinen weiteren Versuch; nach einer 2xx-Antwort wird kein weiterer Versuch geplant | FR-022 |
| AC-011-5 | Integration, Unit | `tests/integration/delivery/RetryDeliveryIT.java`<br>`tests/unit/delivery/BackoffPolicyTest.java` | `the_planned_delays_increase_monotonically()`<br>`no_planned_time_point_lies_in_the_past()`<br>`the_first_five_delays_increase_monotonically()` | die geplanten Wartezeiten steigen monoton und liegen nicht in der Vergangenheit; kein geplanter Zeitpunkt liegt in der Vergangenheit; die Wartezeiten der ersten fuenf Versuche steigen monoton | FR-022 |

### US-012

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-012-1 | Integration, Unit | `tests/integration/delivery/DeadLetterIT.java`<br>`tests/unit/delivery/AttemptLimitTest.java` | `the_abandoned_delivery_appears_in_the_dead_letter_list()`<br>`the_delivery_is_abandoned_after_the_attempt_limit()`<br>`the_abandoned_state_is_terminal()`<br>`the_default_limit_is_reached_after_eight_attempts()` | die aufgegebene Zustellung erscheint in der Totenliste; nach der Versuchsgrenze wird die Zustellung aufgegeben; die aufgegebene Zustellung ist ein endgueltiger Zustand; mit der Vorgabe von 8 Versuchen ist die Grenze nach dem 8. Versuch erreicht | FR-023 |
| AC-012-2 | Integration | `tests/integration/delivery/DeadLetterIT.java` | `the_protocol_names_attempts_last_attempt_and_response_code()` | das Protokoll nennt Versuche, letzten Versuch und Antwortcode | FR-023 |
| AC-012-3 | Integration, Unit | `tests/integration/delivery/DeadLetterIT.java`<br>`tests/unit/delivery/AttemptLimitTest.java` | `there_is_no_automatic_retry_after_abandonment()`<br>`an_abandoned_run_has_no_next_attempt()` | es findet kein automatischer Neuversuch statt; eine aufgegebene Zustellung erhaelt keinen weiteren Versuchszeitpunkt | FR-023 |
| AC-012-4 | Integration, Unit | `tests/integration/delivery/AttemptLimitIT.java`<br>`tests/integration/delivery/DeadLetterIT.java`<br>`tests/unit/delivery/AttemptLimitTest.java` | `the_delivery_ends_after_exactly_one_attempt()`<br>`the_limit_is_frozen_when_the_run_is_created()`<br>`the_configured_limit_is_neither_undershot_nor_overshot()`<br>`a_configured_limit_of_one_is_not_undershot()` | mit der Grenze 1 endet die Zustellung nach genau einem Versuch; die Versuchsgrenze wird beim Anlegen des Laufs festgeschrieben; die konfigurierte Versuchsgrenze wird nicht unterlaufen und nicht ueberschritten; mit der konfigurierten Grenze 1 endet die Zustellung nach genau einem Versuch | FR-023 |

### US-013

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-013-1 | Integration | `tests/integration/delivery/RedeliveryIT.java`<br>`tests/integration/security/ForeignResourceIsolationIT.java` | `a_second_replay_yields_a_state_conflict()`<br>`both_runs_are_visible_separately_in_the_protocol()`<br>`the_replay_starts_a_new_run()`<br>`replaying_an_own_run_with_the_permitted_role_is_accepted()` | ein zweiter Replay desselben Laufs ergibt 409 delivery_state_conflict; beide Laeufe sind im Protokoll getrennt sichtbar; der Replay startet einen neuen Lauf mit der Versuchsanzahl 1; der Replay im eigenen Mandanten mit der berechtigten Rolle wird angenommen | FR-024 |
| AC-013-2 | Integration | `tests/integration/delivery/RedeliveryIT.java` | `the_original_record_stays_unchanged()` | der urspruengliche Datensatz bleibt unveraendert | FR-024 |
| AC-013-3 | Integration | `tests/integration/delivery/RedeliveryIT.java`<br>`tests/integration/security/ForeignResourceIsolationIT.java`<br>`tests/integration/security/RoleCapabilityMatrixIT.java` | `an_unauthorized_role_is_forbidden()`<br>`replaying_a_foreign_run_yields_not_found_for_a_developer()`<br>`replaying_a_foreign_run_yields_not_found_for_a_service_admin()`<br>`replaying_an_own_run_with_the_wrong_role_yields_forbidden()`<br>`replaying_an_own_run_with_the_wrong_role_yields_forbidden_for_a_service_admin()`<br>`every_operation_has_a_role_and_a_status_code()` | eine unberechtigte Rolle erhaelt 403 und es startet kein Lauf; der Replay eines fremden Zustelllaufs ergibt 404 — auch fuer den Entwickler; der Replay eines fremden Zustelllaufs ergibt 404 — auch fuer den Service-Admin; der Replay im eigenen Mandanten mit der falschen Rolle ergibt 403; der Replay im eigenen Mandanten mit der falschen Rolle ergibt 403 — auch fuer den Service-Admin; je Operation ist die Rolle und der Statuscode bei fehlender Berechtigung festgeschrieben | FR-024 |
| AC-013-4 | Integration | `tests/integration/delivery/RedeliveryIT.java` | `the_replay_of_a_paused_endpoint_starts_no_attempt()` | der Replay eines pausierten Endpunkts startet keinen Versuch | FR-024 |
| AC-013-5 | Integration | `tests/integration/delivery/RedeliveryIT.java` | `the_new_run_has_a_new_id_and_the_same_event_id()` | der neue Lauf traegt eine neue Zustelllauf-ID und dieselbe Ereignis-ID | FR-024 |

### US-014

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-014-1 | Integration, Unit | `tests/integration/delivery/RejectionDeliveryIT.java`<br>`tests/unit/delivery/PermanentRejectionTest.java` | `the_protocol_shows_the_rejection_with_the_response_code()`<br>`the_rejection_does_not_appear_in_the_dead_letter_list()`<br>`the_rejection_signal_ends_the_delivery_immediately()`<br>`the_rejection_signal_is_a_permanent_rejection()` | das Protokoll zeigt den Status 'abgelehnt' mit dem Antwortcode; die Ablehnung erscheint nicht in der Totenliste; das Ablehnungssignal beendet die Zustellung sofort; 410 mit dem Ablehnungssignal ist eine endgueltige Ablehnung | FR-025 |
| AC-014-2 | Vertrag | `tests/contract/RejectionContractTest.java` | `the_contract_describes_the_rejection_body_schema()`<br>`the_contract_describes_the_rejection_status_code()`<br>`the_contract_names_the_mandatory_rejection_header()`<br>`the_implementation_knows_the_rejection_header()`<br>`the_rejection_knows_exactly_one_value()`<br>`the_rejection_schema_requires_rejection_and_reason()` | der Vertrag beschreibt das Koerperformat des Ablehnungssignals; der Vertrag beschreibt das Ablehnungssignal mit dem Statuscode 410; der Vertrag nennt die Pflicht-Kopfzeile des Ablehnungssignals; die Implementierung kennt den Ablehnungs-Header des Vertrags; die Ablehnung kennt genau den einen Wert 'permanent'; das Schema DeliveryRejection verlangt die Felder rejection und reason | FR-025 |
| AC-014-3 | Integration, Unit | `tests/integration/delivery/RejectionDeliveryIT.java`<br>`tests/unit/delivery/PermanentRejectionTest.java` | `a_network_error_is_not_a_permanent_rejection()`<br>`a_server_error_is_not_a_permanent_rejection()`<br>`a_network_error_is_retried_like_a_server_error()`<br>`server_errors_are_retryable()` | ein Netzwerkfehler wird nicht als dauerhafte Ablehnung behandelt; ein 5xx wird nicht als dauerhafte Ablehnung behandelt; ein Netzwerkfehler wird wie ein 5xx behandelt; 5xx, 408 und 429 sind wiederholbare Fehler | FR-025 |
| AC-014-4 | Integration, Unit | `tests/integration/delivery/RejectionDeliveryIT.java`<br>`tests/unit/delivery/PermanentRejectionTest.java` | `a_4xx_without_the_signal_ends_without_further_attempts()`<br>`a_410_without_the_signal_is_a_client_error()`<br>`a_redirect_is_never_followed_and_ends_the_run()`<br>`every_other_4xx_is_final()` | eine 4xx ohne das Ablehnungssignal endet ohne weitere Versuche; 410 ohne das Ablehnungssignal ist eine gewöhnliche Client-Ablehnung; eine Umleitung (3xx) wird nicht gefolgt und beendet den Lauf; jede uebrige 4xx wird ohne weitere Versuche aufgegeben | FR-025 |

### US-015

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-015-1 | Integration | `tests/integration/protocol/DeliveryProtocolIT.java`<br>`tests/integration/security/AlwaysPresentFieldsIT.java` | `the_entry_shows_all_required_fields()`<br>`the_single_run_shows_all_attempts()`<br>`organisation_id_is_always_filled_in_the_delivery_protocol()` | der Eintrag zeigt Zustand, Versuche, letzten Versuch, Antwortcode und naechsten Versuch; der Einzelabruf eines Laufs zeigt alle Versuche; organisation_id ist im Zustellprotokoll immer gefuellt | FR-030 |
| AC-015-2 | Integration, Vertrag | `tests/integration/protocol/DeliveryProtocolIT.java`<br>`tests/contract/AlwaysPresentFieldsContractTest.java` | `a_terminal_state_has_no_next_attempt()`<br>`next_attempt_at_may_be_empty_but_is_not_forbidden()` | bei einem endgueltigen Zustand ist der naechste Versuchszeitpunkt leer; next_attempt_at ist in endgueltigen Zustaenden leer, aber nicht verboten | FR-030 |
| AC-015-3 | Integration | `tests/integration/protocol/DeliveryProtocolIT.java` | `reading_a_foreign_event_yields_not_found()` | der Einzelabruf eines fremden Ereignisses ergibt 404 | FR-030 |
| AC-015-4 | Integration | `tests/integration/protocol/DeliveryProtocolIT.java` | `an_event_without_subscriber_is_visible()`<br>`an_invalid_cursor_is_rejected()`<br>`the_filters_are_combinable_and_tenant_scoped()` | ein Ereignis ohne Abnehmer erscheint mit dem Zustand 'no_subscriber'; ein ungueltiger Cursor wird mit 422 abgelehnt; die Filter sind kombinierbar und mandantengefiltert | FR-030 |

### US-016

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-016-1 | Integration, Vertrag | `tests/integration/protocol/DeadDeliveryListIT.java`<br>`tests/integration/security/AlwaysPresentFieldsIT.java`<br>`tests/contract/AlwaysPresentFieldsContractTest.java` | `the_entry_names_all_required_fields()`<br>`the_service_admin_sees_the_dead_deliveries_of_all_tenants()`<br>`the_dead_delivery_always_names_its_owner_and_its_abandonment()`<br>`abandoned_at_is_required_in_a_dead_delivery()` | der Eintrag nennt Ereignis-ID, Endpunkt, Ereignistyp, Versuche, Antwortcode und Aufgabezeitpunkt; der Service-Admin erhaelt die toten Zustellungen aller Mandanten; abandoned_at und organisation_id sind in der Totenliste immer gefuellt; abandoned_at ist in der toten Zustellung pflicht | FR-031 |
| AC-016-2 | Integration | `tests/integration/protocol/DeadDeliveryListIT.java`<br>`tests/integration/security/RoleCapabilityMatrixIT.java` | `a_receiver_admin_is_forbidden()`<br>`the_developer_does_not_read_the_dead_letter_list()` | ein Empfaenger-Admin erhaelt 403; der Entwickler liest die Totenliste nicht | FR-031 |
| AC-016-3 | Integration | `tests/integration/protocol/DeadDeliveryListIT.java` | `an_empty_result_is_a_200_with_an_empty_array()` | eine leere Trefferliste ist HTTP 200 mit leerem items-Array | FR-031 |
| AC-016-4 | Integration | `tests/integration/protocol/DeadDeliveryListIT.java` | `only_abandoned_runs_are_listed()`<br>`the_filters_are_combinable_and_the_list_is_paged()` | nur aufgegebene Zustellungen stehen in der Totenliste; die Filter sind kombinierbar und die Liste ist seitenweise abrufbar | FR-031 |
| AC-016-5 | Integration | `tests/integration/protocol/RetentionEnforcementIT.java`<br>`tests/integration/security/RoleCapabilityMatrixIT.java` | `a_dry_run_changes_nothing()`<br>`a_receiver_admin_may_not_enforce_retention()`<br>`old_records_are_removed_and_recent_ones_are_kept()`<br>`the_anchor_is_the_last_attempt()`<br>`the_retention_period_is_configurable_per_tenant()`<br>`the_developer_does_not_enforce_retention()`<br>`the_receiver_admin_does_not_enforce_retention()` | mit dry_run wird nichts geaendert, die Zaehler weisen die Mengen aus; ein Empfaenger-Admin darf die Aufbewahrung nicht erzwingen (403); alte Ereignisse, Laeufe und Versuche werden geloescht, juengere bleiben erhalten; der Fristbeginn ist der letzte Versuch, nicht der Anlegezeitpunkt; die Frist ist konfigurierbar — eine Mandantenfrist ueberschreibt die Vorgabe; der Entwickler erzwingt die Aufbewahrung nicht; der Empfaenger-Admin erzwingt die Aufbewahrung nicht | FR-031 |

### US-017

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-017-1 | Integration | `tests/integration/protocol/EndpointMetricsIT.java` | `the_metrics_name_success_rate_and_latency()` | die Kennzahlen nennen Erfolgsquote und p95-Latenz | FR-032 |
| AC-017-2 | Integration | `tests/integration/protocol/EndpointMetricsIT.java` | `the_metrics_are_available_over_a_documented_interface()` | die Kennzahlen sind ueber eine dokumentierte Schnittstelle abrufbar | FR-032 |
| AC-017-3 | Integration, Vertrag | `tests/integration/protocol/EndpointMetricsIT.java`<br>`tests/contract/AlwaysPresentFieldsContractTest.java` | `without_data_the_metrics_report_no_data()`<br>`data_available_is_required_in_the_metrics()` | ohne Daten im Fenster wird 'keine Daten' ausgewiesen; data_available ist in EndpointMetrics pflicht — 'keine Daten' ist ein eigener Wert | FR-032 |
| AC-017-4 | Integration | `tests/integration/protocol/EndpointMetricsIT.java` | `the_metrics_of_a_foreign_endpoint_yield_not_found()` | die Kennzahlen eines fremden Endpunkts ergeben 404 | FR-032 |

### US-018

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-018-1 | Integration, Unit | `tests/integration/delivery/RateLimitedDeliveryIT.java`<br>`tests/unit/delivery/RateLimiterTest.java` | `the_delayed_attempt_is_made_after_the_window()`<br>`the_exceeding_attempt_is_delayed_and_not_dropped()`<br>`attempts_below_the_limit_are_permitted()`<br>`the_block_strategy_defers_the_attempt()`<br>`the_counter_is_free_again_after_the_window()`<br>`the_exceeding_attempt_is_delayed_and_not_dropped()` | nach Ablauf des Fensters wird der verschobene Versuch nachgeholt; der Versuch ueber dem Grenzwert wird verschoben, nicht verworfen; Versuche bis zum Grenzwert werden durchgelassen; die Strategie 'block' stellt den Versuch zurueck; nach Ablauf des Fensters ist der Zaehler wieder frei; der ueberschrittene Versuch wird verschoben, nicht verworfen | FR-033 |
| AC-018-2 | Integration, Unit | `tests/integration/delivery/RateLimitedDeliveryIT.java`<br>`tests/unit/delivery/RateLimiterTest.java` | `the_delay_is_visible_in_the_protocol()`<br>`the_delay_names_a_point_in_the_future()` | die Verschiebung ist im Protokoll erkennbar; die Verschiebung nennt einen Zeitpunkt nach dem jetzt | FR-033 |
| AC-018-3 | Integration, Unit | `tests/integration/delivery/RateLimitedDeliveryIT.java`<br>`tests/unit/delivery/RateLimiterTest.java` | `the_limit_applies_per_endpoint()`<br>`the_limit_applies_per_endpoint()` | die Begrenzung wirkt je Endpunkt — der andere Endpunkt wird nicht verzoegert; die Begrenzung wirkt je Endpunkt | FR-033 |
| AC-018-4 | Integration, Vertrag | `tests/integration/delivery/RateLimitedDeliveryIT.java`<br>`tests/integration/protocol/EndpointMetricsIT.java`<br>`tests/integration/security/AlwaysPresentFieldsIT.java`<br>`tests/contract/AlwaysPresentFieldsContractTest.java` | `the_counter_runs_independently_of_the_limit()`<br>`the_rate_limit_section_is_always_part_of_the_answer()`<br>`the_rate_limit_section_is_always_part_of_the_metrics()`<br>`rate_limit_is_required_in_the_metrics()`<br>`the_effective_rate_limit_is_never_null()` | die Zaehlung laeuft unabhaengig vom Grenzwert und ist abrufbar; der Abschnitt rate_limit ist immer Teil der Antwort; der Abschnitt rate_limit ist in den Kennzahlen immer Teil der Antwort; der Abschnitt rate_limit ist in EndpointMetrics pflicht; rate_limit.limit ist nie null | FR-033 |

### US-019

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-019-1 | E2E, Unit | `tests/e2e/endpoints.spec.ts`<br>`tests/unit/frontend/EndpointsPage.test.tsx` | `AC-019-1`<br>`AC-019-1`<br>`AC-019-1` | die Seite zeigt Status, Ziel-URL ohne Secret und die Ereignistypen; zeigt Status, Ziel-URL ohne Secret und die Ereignistypen; das Secret wird nirgends angezeigt | FR-001, FR-002, FR-003 |
| AC-019-2 | E2E, Unit | `tests/e2e/endpoints.spec.ts`<br>`tests/unit/frontend/EndpointsPage.test.tsx` | `AC-019-2`<br>`AC-019-2` | eine ungueltige Ziel-URL ergibt eine Fehlermeldung am Feld; eine ungueltige Ziel-URL ergibt eine Fehlermeldung am Feld und legt keinen Endpunkt an | FR-001, FR-002, FR-003 |
| AC-019-3 | E2E, Unit | `tests/e2e/endpoints.spec.ts`<br>`tests/unit/frontend/EndpointsPage.test.tsx` | `AC-019-3`<br>`AC-019-3` | nach dem Pausieren wechselt der Status sofort auf; beim Pausieren wechselt der Status sofort auf | FR-001, FR-002, FR-003 |
| AC-019-4 | E2E, Unit | `tests/e2e/endpoints.spec.ts`<br>`tests/unit/frontend/EndpointsPage.test.tsx` | `AC-019-4`<br>`AC-019-4` | die Rotation wird bestaetigt, ohne das Secret anzuzeigen; die Secret-Rotation wird bestaetigt, ohne das Secret anzuzeigen | FR-001, FR-002, FR-003 |

### US-020

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-020-1 | E2E, Unit | `tests/e2e/protocol.spec.ts`<br>`tests/unit/frontend/DeliveryProtocolPage.test.tsx` | `AC-020-1`<br>`AC-020-1`<br>`AC-020-1`<br>`AC-020-1` | die Seite zeigt je Vorgang Zustand, Versuche, letzten Versuch, Antwortcode und naechsten Versuch; die Filter nach Endpunkt, Status und Zeitraum sind bedienbar; zeigt je Vorgang Zustand, Versuche, letzten Versuch, Antwortcode und naechsten Versuch; die Filter nach Endpunkt, Status und Zeitraum sind kombinierbar | FR-030, FR-031 |
| AC-020-2 | E2E, Unit | `tests/e2e/dead-deliveries.spec.ts`<br>`tests/unit/frontend/DeadDeliveryPage.test.tsx` | `AC-020-2`<br>`AC-020-2` | die Seite zeigt die toten Zustellungen mit den geforderten Feldern; zeigt die toten Zustellungen mit Ereignis-ID, Endpunkt, Ereignistyp, Versuchen und Aufgabezeitpunkt | FR-030, FR-031 |
| AC-020-3 | E2E, Unit | `tests/e2e/dead-deliveries.spec.ts`<br>`tests/e2e/protocol.spec.ts`<br>`tests/unit/frontend/DeadDeliveryPage.test.tsx`<br>`tests/unit/frontend/DeliveryProtocolPage.test.tsx` | `AC-020-3`<br>`AC-020-3`<br>`AC-020-3`<br>`AC-020-3` | ohne Treffer erscheint ein verstaendlicher Leerzustand; ohne Treffer erscheint ein verstaendlicher Leerzustand statt einer leeren Tabelle | FR-030, FR-031 |
| AC-020-4 | E2E | `tests/e2e/protocol.spec.ts` | `AC-020-4` | die URL eines fremden Mandanten zeigt keine fremden Daten | FR-030, FR-031 |

### US-021

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-021-1 | E2E | `tests/e2e/a11y.spec.ts` | `AC-021-1` | alle Funktionen sind mit der Tastatur erreichbar und ausloesbar | NFR-005 |
| AC-021-2 | E2E, Unit | `tests/e2e/a11y.spec.ts`<br>`tests/unit/frontend/a11y.test.tsx` | `AC-021-2`<br>`AC-021-2`<br>`AC-021-2`<br>`AC-021-2` | der WCAG-2.1-AA-Scan meldet keine kritischen oder schweren Verstoesse; die Endpunktseite hat keine schweren axe-Verstoesse; die Protokollseite hat keine schweren axe-Verstoesse; die Seite der toten Zustellungen hat keine schweren axe-Verstoesse | NFR-005 |
| AC-021-3 | E2E | `tests/e2e/a11y.spec.ts` | `AC-021-3` | der Kontrast erreicht 4,5:1 fuer Text und 3:1 fuer Bedienelemente | NFR-005 |
| AC-021-4 | E2E, Unit | `tests/e2e/a11y.spec.ts`<br>`tests/unit/frontend/a11y.test.tsx` | `AC-021-4`<br>`AC-021-4` | Liste, Zeilen und Statusmeldungen tragen Rolle und Inhalt; Liste, Zeileninhalte und Statusmeldungen tragen Rolle und Inhalt | NFR-005 |

### US-022

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-022-1 | Integration | `tests/integration/schema/SchemaMirrorIT.java`<br>`tests/integration/tenant/RowLevelSecurityIT.java`<br>`tests/integration/tenant/TenantIsolationIT.java` | `the_migration_carries_the_canonical_schema()`<br>`the_mirror_is_byte_identical()`<br>`every_tenant_table_enforces_row_level_security()`<br>`every_tenant_table_carries_the_tenant_column()`<br>`every_tenant_table_enforces_row_level_security()` | die Migration des Dienstes legt das kanonische Schema an; der Spiegel in docs/architecture ist byte-identisch mit dem kanonischen Schema; jede mandantenfuehrende Tabelle erzwingt die Mandantentrennung; jede mandantenfuehrende Tabelle traegt organisation_id; jede mandantenfuehrende Tabelle steht unter Row-Level-Security | NFR-003 |
| AC-022-2 | Integration | `tests/integration/tenant/RowLevelSecurityIT.java`<br>`tests/integration/tenant/TenantIsolationIT.java` | `a_query_without_tenant_filter_shows_only_the_tenants_rows()`<br>`a_tenant_never_sees_the_rows_of_another_tenant()`<br>`without_a_tenant_the_query_returns_no_rows()`<br>`the_application_shows_only_the_own_tenants_data()` | eine Abfrage ohne Mandantenfilter zeigt nur die Zeilen des gesetzten Mandanten; ein fremder Mandant sieht die Zeilen des eigenen Mandanten nicht; ohne gesetzten Mandanten liefert die Abfrage keine Zeile (fail-closed); die Anwendung zeigt nur Daten des eigenen Mandanten | NFR-003 |
| AC-022-3 | Integration | `tests/integration/tenant/TenantIsolationIT.java` | `the_tenant_comes_from_the_identity_and_not_from_the_body()` | der Mandant stammt aus der Identitaet — ein Parameter aus dem Body wird nicht uebernommen | NFR-003 |
| AC-022-4 | Integration | `tests/integration/security/ForeignResourceIsolationIT.java`<br>`tests/integration/security/RoleCapabilityMatrixIT.java`<br>`tests/integration/tenant/TenantIsolationIT.java` | `the_existence_of_a_foreign_resource_is_not_revealed()`<br>`every_operation_has_a_role_and_a_status_code()`<br>`the_tenant_is_checked_before_the_role_for_every_role()`<br>`a_foreign_resource_yields_not_found()`<br>`even_the_service_admin_gets_not_found_for_a_foreign_resource()` | die Existenz einer fremden Ressource wird nicht preisgegeben; je Operation ist die Rolle und der Statuscode bei fehlender Berechtigung festgeschrieben; der Mandant kommt vor der Rolle — alle Rollen ergeben bei einem fremden Mandanten 404; eine fremde Ressource per ID ergibt 404; auch der Service-Admin erhaelt 404 beim Einzelabruf einer fremden Ressource | NFR-003 |

### US-023

| AC-ID | Ebene | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Anforderung |
|---|---|---|---|---|---|
| AC-023-1 | Integration, E2E | `tests/integration/deployment/ContainerStartupIT.java`<br>`tests/e2e/health.spec.ts` | `the_health_endpoint_answers_without_authentication()`<br>`the_image_runs_as_non_root_and_contains_no_secrets()`<br>`the_project_contains_no_hardcoded_secret()`<br>`the_readiness_endpoint_reports_readiness()`<br>`AC-023-1`<br>`AC-023-1` | der Gesundheitsendpunkt antwortet ohne Authentifizierung; das Image laeuft als nicht privilegierter Benutzer und enthaelt keine Secrets; im Projekt ist kein fest verdrahtetes Secret hinterlegt; der Readiness-Endpunkt meldet die Bereitschaft; der Liveness-Endpunkt antwortet ohne Token | NFR-006 |
| AC-023-2 | Integration | `tests/integration/deployment/TenantInstanceIT.java` | `the_bound_instance_does_not_serve_another_tenant()`<br>`the_bound_instance_serves_its_own_tenant()` | die an m-1 gebundene Instanz bedient m-2 nicht; die an m-1 gebundene Instanz bedient m-1 | NFR-006 |
| AC-023-3 | Integration | `tests/integration/deployment/HorizontalScalingIT.java` | `exactly_one_instance_performs_the_attempt()`<br>`the_claimed_run_names_the_claiming_instance()` | von zwei Instanzen fuehrt genau eine den Versuch aus; der beanspruchte Lauf traegt den Namen der beanspruchenden Instanz | NFR-006 |
| AC-023-4 | Integration | `tests/integration/deployment/HorizontalScalingIT.java` | `a_crashed_worker_releases_the_run_after_the_lease_expires()`<br>`the_attempt_count_does_not_jump()` | ein abgestuerzter Worker gibt den Lauf nach dem Lease-Ablauf frei; die Versuchsanzahl steigt nicht sprunghaft | NFR-006 |
| AC-023-5 | Integration | `tests/integration/deployment/EnvironmentSeparationIT.java` | `no_configuration_contains_a_hardcoded_secret()`<br>`staging_has_its_own_configuration()`<br>`the_deployment_provides_staging_and_production_separately()` | keine Konfiguration enthaelt ein fest verdrahtetes Secret; Staging hat eine eigene Konfiguration; die Bereitstellung stellt Staging und Produktion getrennt bereit | NFR-006 |

**101 Zeilen** (101 Akzeptanzkriterien), **305 Testfaelle** in 58 Testdateien
(70 Dateien in `tests/` insgesamt).

## 2 — Anforderungen und die Tests, die sie tragen

| Anforderung | Titel | Stories | AKs | Testdateien | Testfaelle |
|---|---|---|---|---|---|
| `FR-001` | Endpunkt mit Ziel-URL, Secret und Ereignistypen registrieren; URL muss https sein | US-001, US-019 | 9 | 6 | 27 |
| `FR-002` | Endpunkt pausieren und reaktivieren; kein Zwischenspeichern, kein Nachliefern | US-002, US-019 | 9 | 5 | 23 |
| `FR-003` | Secret rotieren; nach der Rotation gilt nur das neue Secret | US-003, US-019 | 8 | 6 | 22 |
| `FR-004` | Service-Admin sieht alle Endpunkte, Empfänger-Admin nur die eigenen | US-004 | 4 | 5 | 17 |
| `FR-010` | Entwickler liefert Ereignis ein: Typ, Nutzlast, Idempotenzschlüssel | US-005 | 6 | 5 | 19 |
| `FR-011` | Gleicher Idempotenzschlüssel erzeugt kein zweites Ereignis; ID des ersten wird zurückgeliefert | US-006 | 4 | 2 | 12 |
| `FR-012` | Nutzlast wird verschlüsselt gespeichert, nur zur Zustellung entschlüsselt | US-007 | 5 | 3 | 14 |
| `FR-013` | Ereignisse können bereits verschlüsselt eingeliefert werden | US-008 | 3 | 2 | 9 |
| `FR-020` | Signatur über Zeitstempel und Nutzlast; Verfahren und Header-Name sind Teil des Vertrags | US-009 | 5 | 4 | 21 |
| `FR-021` | Zustellung ist at-least-once; jede Zustellung trägt eine eigene Zustell-Kennung | US-010 | 4 | 3 | 10 |
| `FR-022` | Fehlgeschlagene Zustellung wird mit wachsendem Abstand erneut versucht, mit Streuung | US-011 | 5 | 3 | 17 |
| `FR-023` | Nach fester Anzahl erfolgloser Versuche wird die Zustellung aufgegeben (tote Zustellung) | US-012 | 4 | 3 | 11 |
| `FR-024` | Empfänger-Admin kann aufgegebenes Ereignis erneut auslösen; Versuchszahl beginnt neu | US-013 | 5 | 3 | 13 |
| `FR-025` | Empfänger kann dauerhafte Ablehnung angeben; Zustellung sofort aufgeben | US-014 | 4 | 3 | 18 |
| `FR-030` | Zustellprotokoll zeigt Zustand, Versuche, letzter Versuch, Antwortcode, nächster Versuch | US-015, US-020 | 8 | 7 | 18 |
| `FR-031` | Service-Admin kann tote Zustellungen als Liste abholen | US-016, US-020 | 9 | 9 | 25 |
| `FR-032` | Je Endpunkt werden Erfolgsquote und Zustelllatenz ausgewiesen | US-017 | 4 | 2 | 5 |
| `FR-033` | Endpunkt ist gegen zu viele Zustellungen in kurzer Zeit geschützt (60 Versuche je Endpunkt und Minute, Strategie delay — F-01) | US-018 | 4 | 5 | 15 |
| `NFR-001` | Nutzlast eines Ereignisses ist auf 1 MB begrenzt | US-005 | 6 | 5 | 19 |
| `NFR-002` | Einliefern antwortet in unter 200 ms (p95); Zustellung ist asynchron | US-005 | 6 | 5 | 19 |
| `NFR-003` | Mandantentrennung wird auf Datenbankebene erzwiesen | US-022 | 4 | 5 | 15 |
| `NFR-004` | Verschlüsselung im Ruhezustand; Schlüssel liegt außerhalb der Datenbank | US-007 | 5 | 3 | 14 |
| `NFR-005` | Oberfläche ohne Maus bedienbar, mit Screenreader nutzbar, WCAG 2.1 AA | US-021 | 4 | 2 | 6 |
| `NFR-006` | Dienst läuft in Containern, auf eine Instanz je Mandant vorbereitet, skaliert horizontal | US-023 | 5 | 5 | 14 |

Die Zuordnung Anforderung → Story → Feature → Test steht in
`docs/requirements/traceability_matrix.json` (Spalten `requirement_id`, `story_ids`,
`acceptance_criteria`, `tests`; die Code-Spalten bleiben bis Schritt 6 leer).

## 3 — Nicht abgedeckte Akzeptanzkriterien

**Keine.** Alle 101 Akzeptanzkriterien haben mindestens einen Testfall.

## 4 — Die sechs offenen Befunde: Testzuordnung

Die Befunde aus dem Briefing sind keine Textlücken mehr, sondern **Testfälle**.
Jeder Befund ist einem Testfall zugewiesen; die Zuordnung ist unten belegt.

| Befund | Thema | Testdatei | Testfall / Methode | Erwartetes Ergebnis | Status |
|---|---|---|---|---|---|
| **V-45** | Replay: fremder Mandant ⇒ 404, eigener Mandant + falsche Rolle ⇒ 403 | `tests/integration/security/ForeignResourceIsolationIT.java` | `replaying_a_foreign_run_yields_not_found_for_a_developer()`, `replaying_a_foreign_run_yields_not_found_for_a_service_admin()`, `replaying_an_own_run_with_the_wrong_role_yields_forbidden()`, `replaying_an_own_run_with_the_wrong_role_yields_forbidden_for_a_service_admin()`, `replaying_an_own_run_with_the_permitted_role_is_accepted()` | fremder Mandant ⇒ **404** (auch für `developer` und `service_admin`); eigener Mandant + falsche Rolle ⇒ **403**; eigener Mandant + `receiver_admin` ⇒ 202 | geschlossen |
| **V-46** | Die als „immer vorhanden" zugesagten Antwortfelder | `tests/contract/AlwaysPresentFieldsContractTest.java` (Vertragszusage)<br>`tests/integration/security/AlwaysPresentFieldsIT.java` (Laufzeitzusage) | `organisation_id_is_required_wherever_it_is_promised()`, `organisation_id_is_described_as_always_filled()`, `rate_limit_is_required_in_the_metrics()`, `the_effective_rate_limit_is_never_null()`, `abandoned_at_is_required_in_a_dead_delivery()`, `data_available_is_required_in_the_metrics()`, `next_attempt_at_may_be_empty_but_is_not_forbidden()`<br>`organisation_id_is_always_filled_in_the_endpoint_list()`, `organisation_id_is_always_filled_in_the_single_endpoint()`, `organisation_id_names_the_owner_in_the_cross_tenant_view()`, `organisation_id_is_always_filled_in_the_delivery_protocol()`, `the_dead_delivery_always_names_its_owner_and_its_abandonment()`, `the_rate_limit_section_is_always_part_of_the_metrics()` | `organisation_id` ist in `Endpoint`, `DeliveryRun`, `DeadDelivery` pflicht und **immer** gefüllt; `rate_limit` ist immer Teil von `EndpointMetrics`; `rate_limit.limit` ist nie `null`; `abandoned_at` ist immer gefüllt; `data_available` ist immer vorhanden | geschlossen |
| **V-47** | Je Endpunkt-Operation ein Test „fremde Ressource ⇒ 404" | `tests/integration/security/ForeignResourceIsolationIT.java` | `reading_a_foreign_endpoint_yields_not_found()`, `updating_a_foreign_endpoint_yields_not_found()`, `pausing_a_foreign_endpoint_yields_not_found()`, `resuming_a_foreign_endpoint_yields_not_found()`, `rotating_a_foreign_endpoint_yields_not_found()` | **404** bei Lesen, Aktualisieren, Pausieren, Reaktivieren und Secret-Rotation eines fremden Endpunkts | geschlossen (5 Operationen — eine mehr als die vier im Briefing genannten) |
| **V-50** | Rollenmatrix: je (Rolle, Fähigkeit) ein Testfall, auch `service_admin` im eigenen Mandanten | `tests/integration/security/RoleCapabilityMatrixIT.java` | `the_capability_matrix_is_enforced(String role, String capability, boolean allowed)` — **24 Fälle** (3 Rollen × 8 Fähigkeiten) | erlaubt ⇒ Statuscode weder 401 noch 403; verboten ⇒ **403**. Enthält `service_admin` → Endpunkte verwalten im eigenen Mandant ⇒ 201 | geschlossen |
| **V-51** | Je der 17 Operationen: Rolle und Statuscode bei fehlender Berechtigung bzw. fremdem Mandanten | `tests/integration/security/RoleCapabilityMatrixIT.java`<br>`tests/integration/security/Operation.java` (Operationstabelle) | `every_operation_has_a_role_and_a_status_code(Operation operation, String role)` — **51 Fälle** (17 Operationen × 3 Rollen)<br>`the_tenant_is_checked_before_the_role_for_every_role()` | erlaubte Rolle ⇒ kein 401/403; nicht erlaubte Rolle ⇒ **403**; ressourcenbezogene Operation ⇒ bei fremdem Mandant **404** für **alle** Rollen | geschlossen |
| **V-52** | Rollenrechte ohne eigenes Akzeptanzkriterium | `tests/integration/security/RoleCapabilityMatrixIT.java` | `the_service_admin_manages_endpoints_in_the_own_tenant()`, `the_service_admin_does_not_submit_events()`, `the_developer_does_not_manage_endpoints()`, `the_developer_does_not_read_the_dead_letter_list()`, `the_receiver_admin_does_not_enforce_retention()`, `the_developer_does_not_enforce_retention()`, `the_service_admin_does_not_rotate_secrets()`, `the_developer_does_not_rotate_secrets()` | acht Rollenrechte, die in den 101 AKs kein eigenes Kriterium haben, sind je einem Testfall zugewiesen. **Anker:** die Rollenmatrix des eingefrorenen Vertrags (`info.description` → „Rollen", normativ ADR-015 §1). | geschlossen (mit AC-Nachpflege in Schritt 1 empfohlen — siehe Abschnitt 7, B-02) |

### 4.1 V-52 — die acht Rollenrechte ohne eigenes Akzeptanzkriterium

Die Rechte stammen aus der **Rollenmatrix des eingefrorenen Vertrags**, nicht aus
einer erfundenen Anforderung. Sie sind hier testbar zugewiesen; die
Akzeptanzkriterien-Lücke selbst bleibt als **Nachpflege-Auftrag an Schritt 1**
im Briefing (kein Blocker für Schritt 6 — der Vertrag ist normativ).

| Rolle | Recht ohne eigenes AK | Testfall | Erwartung |
|---|---|---|---|
| `service_admin` | Endpunkte verwalten **im eigenen Mandant** | `the_service_admin_manages_endpoints_in_the_own_tenant()` | 201 |
| `service_admin` | Ereignisse einliefern ⇒ nein (FR-010) | `the_service_admin_does_not_submit_events()` | 403 |
| `service_admin` | Secret rotieren ⇒ nein | `the_service_admin_does_not_rotate_secrets()` | 403 |
| `developer` | Endpunkte verwalten ⇒ nein | `the_developer_does_not_manage_endpoints()` | 403 |
| `developer` | Totenliste mandantübergreifend ⇒ nein | `the_developer_does_not_read_the_dead_letter_list()` | 403 |
| `developer` | Aufbewahrung erzwingen ⇒ nein | `the_developer_does_not_enforce_retention()` | 403 |
| `receiver_admin` | Aufbewahrung erzwingen ⇒ nein | `the_receiver_admin_does_not_enforce_retention()` | 403 |
| `developer` | Secret rotieren ⇒ nein | `the_developer_does_not_rotate_secrets()` | 403 |

## 5 — Pflichtfälle je Story (Workflow 05)

Der Workflow verlangt je Story: **Erfolg**, **doppelter Aufruf (Idempotenz)**,
**ungültige Eingabe**, **fehlende Berechtigung**, **Abhängigkeit antwortet
langsam oder mit Fehler**. Die Spalten nennen je Story den konkreten Testfall.

| Story | Erfolg | Doppelter Aufruf | Ungültige Eingabe | Fehlende Berechtigung | Abhängigkeit langsam / Fehler |
|---|---|---|---|---|---|
| US-001 | `registration_creates_an_active_endpoint()` | — (die Registrierung ist nicht idempotent) | `a_non_https_target_url_is_rejected()`, `embedded_credentials_are_rejected()` | `a_developer_may_not_register_an_endpoint()` (403) | — (keine Abhängigkeit) |
| US-002 | `after_pause_no_attempt_is_made()` | `the_attempt_count_stays_unchanged()` | `a_foreign_endpoint_yields_not_found()` (404 bei ungültiger ID) | `a_developer_may_not_pause_an_endpoint()` (403) | — |
| US-003 | `rotation_increases_the_secret_version()` | `a_second_rotation_creates_a_second_history_entry()` | `a_too_short_secret_is_rejected()` (422) | `a_developer_may_not_rotate()` (403) | — |
| US-004 | `the_list_of_a_receiver_admin_is_tenant_scoped()` | — (nur Lesevorgang) | `a_tenant_parameter_in_the_query_is_rejected()` (422) | `reading_a_foreign_endpoint_yields_not_found()` (404) | — |
| US-005 | `the_event_is_accepted_with_202()` | — (Idempotenz ist US-006) | `an_event_without_type_is_rejected()`, `an_event_without_idempotency_key_is_rejected()` (422) | `a_receiver_admin_may_not_submit_events()` (403) | `a_slow_receiver_does_not_block_the_intake_request()` (EventIntakePerformanceIT) |
| US-006 | `the_replay_returns_the_id_of_the_first_event()` | `the_replay_returns_the_id_of_the_first_event()`, `no_second_event_is_created()` | `a_differing_payload_yields_a_conflict()` (409) | `a_receiver_admin_may_not_submit_events()` (403, EventIntakeIT) | — |
| US-007 | `the_payload_is_stored_as_ciphertext()` | — | — | `reading_a_foreign_event_yields_not_found()` (404, DeliveryProtocolIT) | `the_plaintext_marker_is_nowhere_in_the_application_log()` |
| US-008 | `the_bytes_are_stored_unchanged()` | — | `a_pre_encrypted_submission_with_plaintext_is_rejected()` (422) | `a_receiver_admin_may_not_submit_events()` (403) | — |
| US-009 | `the_delivery_carries_a_verifiable_signature()` | — | `a_signature_over_a_tampered_payload_fails()` | — | `a_signature_over_a_tampered_payload_fails()`, `the_real_http_request_carries_the_frozen_headers()` |
| US-010 | `every_attempt_carries_the_three_identifiers()` | `the_timeout_duplicate_shares_the_run_id_but_not_the_attempt_id()` | — | — | `the_timeout_duplicate_shares_the_run_id_but_not_the_attempt_id()` (Timeout) |
| US-011 | `the_next_attempt_is_planned_with_double_the_distance()` | — | — | — | `a_network_error_is_retried_like_a_server_error()` |
| US-012 | `the_delivery_is_abandoned_after_the_attempt_limit()` | — | — | — | `the_delivery_is_abandoned_after_the_attempt_limit()` (5xx) |
| US-013 | `the_replay_starts_a_new_run()` | `a_second_replay_yields_a_state_conflict()` (409) | `a_second_replay_yields_a_state_conflict()` | `an_unauthorized_role_is_forbidden()` (403) | — |
| US-014 | `the_rejection_signal_ends_the_delivery_immediately()` | — | — | — | `a_network_error_is_not_a_permanent_rejection()`, `a_server_error_is_not_a_permanent_rejection()` |
| US-015 | `the_entry_shows_all_required_fields()` | — | `an_invalid_cursor_is_rejected()` (422) | `reading_a_foreign_event_yields_not_found()` (404) | — |
| US-016 | `the_service_admin_sees_the_dead_deliveries_of_all_tenants()` | — | `an_invalid_cursor_is_rejected()` (422) | `a_receiver_admin_is_forbidden()` (403) | — |
| US-017 | `the_metrics_name_success_rate_and_latency()` | — | — | `the_metrics_of_a_foreign_endpoint_yield_not_found()` (404) | — |
| US-018 | `the_exceeding_attempt_is_delayed_and_not_dropped()` | `the_delayed_attempt_is_made_after_the_window()` (Nachholen) | — | — | `the_counter_runs_independently_of_the_limit()` |
| US-019 | `AC-019-1` (e2e + Komponente) | — (die UI-Stories beschreiben Zustände, keine Idempotenz; die Doppelaufruf-Pflicht liegt bei US-003/US-006/US-013) | `AC-019-2` (ungültige Ziel-URL ⇒ Fehler am Feld) | — (die Oberfläche zeigt nur, was die Rolle darf) | — |
| US-020 | `AC-020-1` (e2e + Komponente) | — | — | `AC-020-4` (fremde Mandanten-URL ⇒ kein fremder Inhalt) | — |
| US-021 | `AC-021-2` (axe-Scan) | — | — | — | — |
| US-022 | `every_tenant_table_enforces_row_level_security()` | — | — | `a_foreign_resource_yields_not_found()` (404) | `without_a_tenant_the_query_returns_no_rows()` (fail-closed) |
| US-023 | `the_health_endpoint_answers_without_authentication()` | — | `the_project_contains_no_hardcoded_secret()` | — | `a_crashed_worker_releases_the_run_after_the_lease_expires()` |

> Wo ein Pflichtfall in der Story fachlich nicht existiert (keine Idempotenz im
> Endpunktmodul, keine Abhängigkeit bei der Registrierung, keine Rollengrenze
> beim Lesezugriff), ist die Zelle mit „—" markiert. Das ist eine **Aussage**,
> kein Versehen: die Story hat keinen doppelten Aufruf und keine externe
> Abhängigkeit. Erfundene Testfälle wären hier schlimmer als eine Lücke.

## 6 — Verteilung nach Ebene und Verzeichnis

| Ebene | Verzeichnis | Testdateien | Testfälle | Wofür |
|---|---|---|---|---|
| Unit | `tests/unit/` | 18 | 85 | reine Regeln: URL-Prüfung, Backoff, Streuung, Signatur, Chiffre, Zustandsautomat, Rate-Limit, Größengrenze, Idempotenzentscheidung |
| Integration | `tests/integration/` | 31 | 185 | HTTP-Vertrag gegen die echte Anwendung, PostgreSQL 16 aus Testcontainers, Mandantentrennung, Verschlüsselungsnachweis, Rollenmatrix, Parallelbetrieb |
| Vertrag | `tests/contract/` | 3 | 19 | der eingefrorene Vertrag `docs/api/openapi.yaml` als Testorakel (Signatur, Ablehnungssignal, „immer vorhanden"-Felder) |
| E2E | `tests/e2e/` | 5 | 14 | die drei Seiten im echten Browser: Bedienung, Tastatur, Kontrast, axe-Scan, Screenreader-Semantik |
| Last | `tests/performance/` | 1 | 2 | NFR-002: p95 < 200 ms über **1.000** Einlieferungen |
| **Summe** | | **58** | **305** | 101 Akzeptanzkriterien, 24 Anforderungen |

Zusatzdateien ohne eigene Testfälle: `tests/integration/support/` (Container,
Testuhr, Protokollaufzeichnung, Klartextsuche, Authentifizierung, Recording-Port)
und `tests/integration/security/Operation.java` (die 17 Operationen des Vertrags
als Tabelle). Zwei maschinenlesbare Vertragsspezifikationen liegen in
`tests/contract/*.yaml`.

## 7 — Befunde aus diesem Schritt

| ID | Befund | Auswirkung | Empfehlung |
|---|---|---|---|
| **B-01** | **AC-011-1** sagt „mindestens doppelt so hoch", der Vertrag (F-04) sagt zugleich **± 20 % Streuung**. Bei aktiver Streuung kann der tatsächliche Abstand 1,6-fach (statt 2-fach) sein. | Die beiden Aussagen widersprechen sich für die *realisierte* Wartezeit. | Der Test prüft die **Nennwartezeit** (mit `jitter = 0` exakt doppelt) und die **Streuung** getrennt (`BackoffPolicyTest`, `JitterTest`). Die entschiedene Semantik aus dem Vertrag gilt. **Kein Vertragsdefekt** — der Vertrag wird nicht geändert; die Story-Formulierung ist zu schärfen (Nachpflege Schritt 1, kein Blocker). |
| **B-02** | **V-52** — acht Rollenrechte der Vertragsrollenmatrix haben in den 101 AKs kein eigenes Akzeptanzkriterium. | Die Rechte sind normativ (ADR-015 §1), aber nicht akzeptanzkriteriell abgesichert. | Die Rechte sind je einem Testfall zugewiesen (Abschnitt 4.1). Empfehlung: Schritt 1 ergänzt die fehlenden AKs — **kein Blocker**, der Vertrag ist die Quelle. |
| **B-03** | Die kanonische Schema-Datei (`schema.sql`) enthält ein eigenes `BEGIN; … COMMIT;`. Flyway läuft die Migration in einer eigenen Transaktion. | Flyway kann die Datei nicht unverändert in einer Transaktion ausführen; die Transaktionssteuerung muss mit dem Schema harmonieren. | **Hinweis für Schritt 6:** die Migration muss mit `execute-in-transaction=false` laufen oder das Schema muss transaktionsneutral ausgeführt werden. Sonst startet die Anwendung nicht und **alle** Integrationstests bleiben rot — aus dem falschen Grund. |
| **B-04** | Der Lasttest misst die Server-Verarbeitungszeit über MockMvc, nicht die HTTP-Transportzeit. | Die p95-Aussage gilt für die Verarbeitung, nicht für das Netz. | Der Lasttest ist in Schritt 6 zusätzlich gegen die laufende Instanz (RANDOM_PORT) zu bestätigen. |
| **B-05** | Der WAL des Testcontainers ist von außen nicht lesbar. | Der Nachweis „Journal" (AC-007-1) stützt sich auf `pg_dump` (alle Zeilen in INSERT-Form) und das Server-Protokoll, nicht auf eine WAL-Suche. | Dokumentierte Grenze. Der Nutzlastinhalt kann nur verschlüsselt in die WAL gelangen, weil die Nutzlast ausschließlich als Chiffre geschrieben wird (`events.payload_ciphertext`). |
| **B-06** | Der Kontrast-Test (AC-021-3) läuft nur im echten Browser. jsdom kennt keine Farben. | Im Komponententest ist die Kontrastprüfung nicht möglich. | Die Kontrastprüfung liegt im Playwright-Test `tests/e2e/a11y.spec.ts`; der Komponententest prüft die übrigen axe-Regeln. |

## 8 — Maschinelle Nachweise

| Prüfung | Befehl | Ergebnis |
|---|---|---|
| Alle 101 ACs abgedeckt | eigene Abfrage über `@DisplayName` / `it('AC-…')` und `docs/requirements/stories/*.json` | **101/101 abgedeckt**, 0 unbekannte AC-IDs in Tests, 0 Testfälle ohne AC-ID |
| Traceability-Kette | `python3 .agent/tools/traceability_check.py` | grün (23 Stories / 101 AKs / 24 Anforderungen) |
| Schema-Spiegel | `diff .agent/knowledge/schema.sql docs/architecture/schema.sql` | leer (byte-identisch) |
| Java-Quellen syntaktisch gültig | `javac` gegen die Testquellen mit Stubs der noch fehlenden Produktionsklassen | **kompiliert** — die einzigen fehlenden Symbole sind die noch nicht existierenden Produktionsklassen |
| TypeScript-Quellen syntaktisch gültig | `tsc --noEmit --strict` mit Stubs der noch fehlenden Seiten | **kompiliert** — keine Syntax- oder Typfehler |
| Roter Zustand als TDD-Ziel | `mvn test` / `npm test` in Schritt 6 | **rot**, weil `src/` nicht existiert |
