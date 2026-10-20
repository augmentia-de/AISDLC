# language: en
@US-023 @E4 @FR-039 @SEC-010
Feature: Manuelle Ausnahme-Freigabe eines geflaggten Prompts

  As security admin
  I want to release a flagged prompt with a documented reason
  So that false positives do not block my teams.

  @happy-path
  Scenario: The security admin releases a flagged version with a reason
    Given a version is FLAGGED with a finding of severity HIGH
    When the security admin releases it with a reason
    Then the API responds with 200 and the version reaches status WAIVED
    And the audit trail records actor, timestamp and reason

  @rejection-path
  Scenario: A developer cannot release a flagged version
    Given a version is FLAGGED
    When a developer attempts to release it
    Then the API responds with 403 and the status stays FLAGGED

  @edge-case
  Scenario: A release without a reason is rejected
    Given a version is FLAGGED
    When the security admin submits a release without a reason
    Then the API responds with 422 and the status stays FLAGGED
    And no audit entry is written
