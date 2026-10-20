# language: en
@US-017 @E4 @FR-032
Feature: Duplikatwarnung innerhalb der eigenen Organisation

  As developer
  I want to be warned when a similar prompt already exists in my organisation
  So that I reuse instead of duplicating work.

  @happy-path
  Scenario: A near-duplicate produces a warning with matches
    Given the own organisation already owns a prompt with almost identical content
    When the developer creates a prompt with very similar content
    Then the scan result contains a duplicate warning listing up to five similar prompts with title and similarity score
    And the creation itself is not blocked

  @rejection-path
  Scenario: Identical content in another organisation produces no warning
    Given an identical prompt exists in another organisation only
    When the developer creates the prompt in their own organisation
    Then the scan result contains no duplicate warning and no hint about the foreign prompt

  @edge-case
  Scenario: Similarity below the configured threshold produces no warning
    Given a prompt of the own organisation is only loosely related
    When the developer creates the prompt
    Then the scan result contains no duplicate warning
    And the threshold is configurable per organisation
