# language: en
@US-029 @E7 @FR-063 @DP-004 @DP-007 @DP-008
Feature: Recht auf Löschung

  As user
  I want my account and personal references to be deletable
  So that my personal data is removed when I request it.

  @happy-path
  Scenario: Erasing a user removes the account and anonymises authorship
    Given a user authored three prompts and holds an active session
    When the organisation admin triggers the erasure
    Then the account, its tokens and its sessions are deleted and the authorship in the three prompts is anonymised
    And the prompts themselves remain and are queued for a new scan

  @rejection-path
  Scenario: A user of another organisation cannot be erased
    Given a user belongs to organisation B
    When an admin of organisation A attempts the erasure
    Then the API responds with 403 and nothing is deleted

  @edge-case
  Scenario: Erasure does not destroy the audit trail
    Given the erased user performed security relevant actions
    When the erasure is executed
    Then the audit entries remain with actor and timestamp but without the personal reference and without content
