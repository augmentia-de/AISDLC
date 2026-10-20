# language: en
@US-005 @E1 @FR-009 @FR-023 @SEC-002 @SEC-003
Feature: Striktes Mandantenscoping auf jeder Route

  As security admin
  I want a hard separation between organisations
  So that no developer can ever see the prompts of another department.

  @happy-path
  Scenario: A listing returns only the prompts of the own organisation
    Given the acting user is a developer of organisation A which owns three prompts
    When the user lists the prompts
    Then the API responds with exactly the three prompts of organisation A
    And no field of any other organisation is present in the response

  @rejection-path
  Scenario: Accessing a foreign prompt by id does not reveal its existence
    Given a prompt with a known id exists in organisation B
    When a developer of organisation A requests that prompt by id
    Then the API responds with 404 and an empty body
    And neither the title nor the owner nor the scan status leaks

  @edge-case
  Scenario: A search never reveals foreign hits through counts or previews
    Given a distinctive term occurs only in prompts of organisation B
    When a developer of organisation A searches for that term
    Then the API responds with an empty result list and total count 0
    And the response contains no suggestion, no facet and no hint that a foreign match exists
