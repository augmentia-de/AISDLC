# language: en
@US-007 @E2 @FR-010 @FR-011 @FR-030
Feature: Prompt anlegen

  As developer
  I want to create a prompt with title, content, category and tags
  So that it becomes part of the central registry.

  @happy-path
  Scenario: Creating a prompt returns immediately with status pending
    Given an authenticated developer belongs to an organisation with a default category list
    When the developer submits a valid prompt with title, content, category and two tags
    Then the API responds with 201 within the latency budget and creates version 1 with status PENDING
    And the response contains no scan result yet

  @rejection-path
  Scenario: Invalid input is rejected with a field-level error
    Given an authenticated developer is present
    When the developer submits a prompt without a title
    Then the API responds with 422 and names the offending field
    And no prompt and no version is created

  @edge-case
  Scenario: Oversized content is rejected without storing a partial prompt
    Given an authenticated developer is present
    When the developer submits a prompt whose content exceeds the configured maximum length
    Then the API responds with 422 and states the limit
    And neither a prompt nor a partial version is persisted
