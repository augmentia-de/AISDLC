# language: en
@US-011 @E2 @FR-015
Feature: Tags vergeben

  As developer
  I want to label prompts with free tags
  So that I can find them later.

  @happy-path
  Scenario: Tags are normalised and stored once per organisation
    Given an authenticated developer is present
    When the developer assigns the tags Support, deutsch and support
    Then the API responds with 200 and stores the two distinct normalised tags Support and deutsch

  @rejection-path
  Scenario: Too many tags are rejected
    Given an authenticated developer is present
    When the developer assigns eleven tags to one prompt
    Then the API responds with 422 and names the limit
    And the prompt keeps its previous tag set

  @edge-case
  Scenario: The organisation tag limit is enforced
    Given the organisation already uses the maximum number of distinct tags
    When the developer assigns a completely new tag
    Then the API responds with 422 and the tag limit is reported
    And an already existing tag can still be assigned
