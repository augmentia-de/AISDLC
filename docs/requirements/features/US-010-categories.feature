# language: en
@US-010 @E2 @FR-014
Feature: Kategorien verwalten

  As organisation admin
  I want to maintain the category list
  So that prompts are classified consistently.

  @happy-path
  Scenario: A new organisation starts with the seed category list
    Given an organisation has just been created
    When the developer lists the categories
    Then the API responds with 200 and the seed categories Customer Support, Code Gen, Data Analysis, Documentation and Other

  @happy-path
  Scenario: The organisation admin creates and renames categories
    Given the acting user has the role ORG_ADMIN
    When the admin creates the category Support Bot and later renames it
    Then the API responds with 201 and later 200 and the category keeps its id

  @edge-case
  Scenario: A category in use can only be deactivated, not deleted
    Given a category is referenced by at least one prompt
    When the admin attempts to delete it
    Then the API responds with 409 and the category remains usable but inactive
    And existing prompts keep their reference
