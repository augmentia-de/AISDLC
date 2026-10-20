# language: en
@US-013 @E3 @FR-020 @NFR-004
Feature: Volltextsuche

  As developer
  I want to search prompts by title and content
  So that I find an existing prompt instead of writing a new one.

  @happy-path
  Scenario: A term inside the content finds the prompt
    Given the own organisation owns three prompts, one of them containing the term Refundierungsrichtlinie
    When the developer searches for Refundierungsrichtlinie
    Then the API responds with 200 and returns exactly that prompt with a highlighted match

  @rejection-path
  Scenario: A term without match returns an empty list
    Given no prompt of the own organisation contains the search term
    When the developer searches for a term that only exists in another organisation
    Then the API responds with 200 and an empty result list with total count 0

  @edge-case
  Scenario: Search input is treated as data, not as an instruction
    Given an authenticated developer is present
    When the developer searches for a term containing SQL metacharacters and a prompt injection phrase
    Then the API responds with 200 or 422 and never returns database errors or prompt contents
    And no injected statement is executed
