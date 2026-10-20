# language: en
@US-025 @E5 @FR-041 @SEC-010 @FR-060
Feature: Status- und Änderungshistorie

  As security admin
  I want an audit trail of status changes
  So that I can reconstruct what happened with a prompt.

  @happy-path
  Scenario: Every status transition is recorded
    Given a version went from PENDING to SCANNING to APPROVED
    When the author opens the history
    Then the API responds with 200 and lists all three transitions with timestamp and cause

  @rejection-path
  Scenario: The history of another organisation is not readable
    Given a history belongs to a prompt of organisation B
    When a developer of organisation A requests it
    Then the API responds with 404 and no history entry is returned

  @edge-case
  Scenario: Audit entries without content are removed after the retention period
    Given audit entries older than 90 days exist
    When the retention job runs
    Then those entries are deleted and no prompt content is left behind
    And the remaining entries are unaffected
