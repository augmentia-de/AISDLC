# language: en
@US-015 @E3 @FR-022 @NFR-008
Feature: Sortierung und Pagination

  As developer
  I want to sort and page through the registry
  So that large lists stay usable.

  @happy-path
  Scenario: Results can be sorted by update time
    Given the own organisation owns prompts with different update timestamps
    When the developer sorts by updated_at descending
    Then the API responds with 200 and the prompts are ordered from newest to oldest

  @rejection-path
  Scenario: A page beyond the result range is empty
    Given the filtered result set contains three pages
    When the developer requests page 99
    Then the API responds with 200 and an empty page with the correct total count

  @edge-case
  Scenario: An oversized page size is clamped or rejected
    Given an authenticated developer is present
    When the developer requests a page size of 5000
    Then the API responds with 400 and states the maximum page size
    And the response contains at most the maximum number of items
