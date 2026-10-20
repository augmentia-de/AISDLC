# language: en
@US-035 @E8 @NFR-015 @NFR-016 @NFR-018
Feature: Barrierefreiheit, Typsicherheit und Bedienbarkeit

  As developer
  I want an accessible and strictly typed interface
  So that the platform can be used by everyone and stays maintainable.

  @happy-path
  Scenario: The interface is fully operable with the keyboard
    Given the application is running
    When a user navigates prompt list, detail view, editor and version history using the keyboard only
    Then every interactive element is reachable in a logical order and operable without a mouse
    And focus is visible at all times

  @happy-path
  Scenario: Screen readers announce the relevant elements
    Given the application is running
    When a screen reader user opens the prompt list and a flagged prompt
    Then the scan status is announced with its text label and not only by colour
    And form fields have associated labels and the status change is announced

  @rejection-path
  Scenario: The build fails on type or lint errors
    Given a change with a type error is pushed
    When the pipeline runs
    Then the pipeline fails with the type error and the change cannot be merged
    And no implicit any, no unhandled promise and no unresolved lint warning is tolerated

  @edge-case
  Scenario: The interface is German while the API contract stays English
    Given the application is running
    When a user reads the interface and an API client reads an error response
    Then the interface texts are German and the API error codes and field names are English
    And the language of the interface can be switched and the choice is remembered
