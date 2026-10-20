# language: en
@US-020 @E4 @FR-035
Feature: Erkennung personenbezogener Daten

  As data protection officer
  I want personal data inside prompts to be detected
  So that no customer data stays in the registry unnoticed.

  @happy-path
  Scenario: Personal data leads to a flagged prompt
    Given a developer creates a prompt that contains a customer e-mail address and a customer name
    When the scan runs
    Then the version reaches status FLAGGED with findings of category PII
    And the excerpts are masked and the positions are reported

  @edge-case
  Scenario: Example data stays below the flagging threshold
    Given a developer creates a prompt that contains the address support@example.com
    When the scan runs
    Then a finding of category PII with severity LOW is reported
    And the version is not flagged because the configured threshold is MEDIUM

  @edge-case
  Scenario: Semantic personal data is detected as well
    Given a developer creates a prompt that mentions a customer with a full name in running text
    When the scan runs
    Then a PII finding is reported with the semantic reasoning of the classifier
