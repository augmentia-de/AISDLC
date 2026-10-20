# language: en
@US-031 @E8 @NFR-009 @NFR-010 @FR-073 @CON-008
Feature: Testabdeckung und Qualitäts-Gate

  As reviewer
  I want the build to fail below 80 percent coverage
  So that quality is enforced and not just intended.

  @happy-path
  Scenario: The pipeline runs tests and measures coverage
    Given a change is pushed to a branch
    When the pipeline runs
    Then the pipeline executes unit, integration and end to end tests and reports the line coverage of backend and frontend

  @rejection-path
  Scenario: Coverage below the threshold fails the build
    Given the measured line coverage is 74 percent
    When the pipeline evaluates the coverage gate
    Then the pipeline fails with the missing percentage and the change cannot be merged

  @edge-case
  Scenario: The tenant isolation test is part of the mandatory set
    Given a developer changes a repository query
    When the pipeline runs the integration tests
    Then the cross tenant test runs against a real database and fails when a query loses its organisation filter
