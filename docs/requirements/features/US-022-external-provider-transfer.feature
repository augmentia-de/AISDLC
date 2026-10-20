# language: en
@US-022 @E4 @FR-038 @FR-074 @DP-002 @DP-005 @SEC-006
Feature: Übermittlung an den KI-Dienst unter festen Auflagen

  As data protection officer
  I want every transfer to the AI service to run only against a verified provider configuration
  So that the data transfer is provably compliant.

  @happy-path
  Scenario: The prompt content is transmitted unchanged
    Given the provider configuration has been verified and a prompt is ready to be classified
    When the scanner calls the AI service
    Then the provider receives the original prompt text without redaction
    And the transmission is recorded in the audit trail as metadata without content

  @rejection-path
  Scenario: A provider configuration that violates the terms aborts the scan
    Given the configured provider does not guarantee retention free processing in the EU
    When the scanner would call the AI service
    Then the call is not executed and the version moves to FAILED
    And no prompt content leaves the system

  @edge-case
  Scenario: A failing provider call leaves the version in a recoverable state
    Given the provider is reachable but the call fails with a timeout
    When the scan runs
    Then the version moves to FAILED and can be retried manually
    And no partial scan result is stored
