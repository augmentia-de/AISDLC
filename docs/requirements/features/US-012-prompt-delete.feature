# language: en
@US-012 @E2 @FR-016 @DP-004
Feature: Prompt löschen

  As organisation admin
  I want to delete obsolete prompts including their history
  So that the registry only contains relevant material.

  @happy-path
  Scenario: Deleting a prompt removes its versions and findings
    Given a prompt has two versions with scans and findings
    When the author deletes the prompt
    Then the API responds with 204 and prompt, versions, scans and findings no longer exist
    And an audit entry without prompt content remains

  @rejection-path
  Scenario: A prompt of another organisation cannot be deleted
    Given a prompt exists in organisation B
    When a developer of organisation A attempts to delete it
    Then the API responds with 404 and the prompt is untouched

  @edge-case
  Scenario: Deleting a prompt of a colleague is recorded in the audit trail
    Given a prompt was created by developer X and is being deleted by the organisation admin
    When the admin deletes the prompt
    Then the API responds with 204 and the audit entry names the deleting actor, not only the author
    And the audit entry contains no prompt content
