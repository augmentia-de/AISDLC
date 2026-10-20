# language: en
@US-030 @E7 @FR-064 @DP-001 @DP-006
Feature: Datenschutzhinweis in Deutsch und Englisch

  As user
  I want a clear privacy notice in my language
  So that I know what happens with my data.

  @happy-path
  Scenario: The notice is reachable in both languages
    Given the application is running
    When a user opens the privacy notice
    Then the API responds with the notice in German and in English including purpose, legal basis, retention periods and rights

  @edge-case
  Scenario: The notice is versioned
    Given the notice text has been changed in a new release
    When a user opens the notice
    Then the API returns the current version and the version identifier is exposed
    And the previous version is archived for proof
