# language: en
@US-024 @E5 @FR-040 @NFR-003 @FR-042
Feature: Live-Aktualisierung des Prüfstatus in der Oberfläche

  As developer
  I want the interface to show the status change without a reload
  So that I know immediately whether my prompt is approved.

  @happy-path
  Scenario: The status change appears without reloading
    Given a prompt version is in status SCANNING and the user has the list open
    When the scan finishes and the status changes to APPROVED
    Then the interface shows APPROVED within two seconds without a manual reload

  @rejection-path
  Scenario: The live channel rejects unauthenticated clients
    Given no valid token is presented
    When a client opens the live status channel
    Then the server refuses the connection and no status data is streamed

  @edge-case
  Scenario: After a lost connection the current state is restored
    Given the live channel drops while a scan is running
    When the client reconnects
    Then the client receives the current status of the prompt and continues to receive updates
    And no intermediate update is delivered twice with a different state
