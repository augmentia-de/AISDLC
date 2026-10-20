# language: en
@US-009 @E2 @FR-013 @NFR-005
Feature: Versionshistorie und Versionsvergleich

  As developer
  I want to see how a prompt evolved
  So that I understand what changed and which version is in use.

  @happy-path
  Scenario: The version history lists all versions with metadata
    Given a prompt has three versions created by two different authors
    When the author opens the version history
    Then the API responds with 200 and lists version number, author, timestamp, status and finding count per version

  @rejection-path
  Scenario: Versions of another organisation are not readable
    Given a version belongs to a prompt of organisation B
    When a developer of organisation A requests that version
    Then the API responds with 404 and no version data is returned

  @edge-case
  Scenario: Older versions stay immutable and comparable
    Given a prompt has two versions with different content
    When the author compares version 1 with version 2
    Then the API responds with 200 and returns a diff of the two contents
    And neither version is modified by the comparison
