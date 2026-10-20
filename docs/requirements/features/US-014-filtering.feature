# language: en
@US-014 @E3 @FR-021
Feature: Filtern nach Kategorie, Tag und Prüfstatus

  As developer
  I want to combine filters
  So that I can narrow down a large registry.

  @happy-path
  Scenario: Combined filters narrow the result set
    Given the own organisation owns prompts in several categories, tags and scan statuses
    When the developer filters by category Code Gen, tag python and status FLAGGED
    Then the API responds with 200 and only prompts matching all three criteria

  @rejection-path
  Scenario: A foreign category id yields no results
    Given a category belongs to another organisation
    When the developer filters by that category id
    Then the API responds with 200 and an empty list without any hint that the category exists

  @edge-case
  Scenario: Pagination stays stable while filters are active
    Given the filtered result set spans several pages
    When the developer requests the second page with the same filters
    Then the API responds with 200 and returns the next page of the same filtered set
    And the total count matches the filtered set, not the unfiltered one
