# language: en
@US-004 @E1 @FR-003 @FR-004 @SEC-004
Feature: Rollen und Berechtigungen

  As organisation admin
  I want three distinct roles
  So that only the right people can administer members, categories and flagged prompts.

  @happy-path
  Scenario: An organisation admin manages categories
    Given the acting user has the role ORG_ADMIN
    When the user creates a new category
    Then the API responds with 201 and the category belongs to the acting organisation

  @rejection-path
  Scenario: A developer is not allowed to administer categories
    Given the acting user has the role DEVELOPER
    When the user attempts to create a category
    Then the API responds with 403 and no category is created

  @edge-case
  Scenario: Only the security admin may release a flagged prompt
    Given a prompt version is flagged with severity HIGH
    When a developer attempts to release it while the security admin succeeds
    Then the developer receives 403 and the security admin receives 200 with status WAIVED
    And both attempts are recorded in the audit trail
