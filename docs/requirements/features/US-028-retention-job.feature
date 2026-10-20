# language: en
@US-028 @E7 @FR-060 @FR-061 @DP-003 @NFR-013
Feature: Retention-Löschung nach 90 Tagen

  As data protection officer
  I want old telemetry deleted automatically
  So that the platform keeps only what it may keep.

  @happy-path
  Scenario: Telemetry older than 90 days is deleted, prompts are kept
    Given log entries, scan raw data and notification records older than 90 days exist and prompts of any age exist
    When the retention job runs
    Then the telemetry is deleted and the prompts and versions remain
    And the job writes a summary of the deleted volume

  @edge-case
  Scenario: Running the job twice changes nothing
    Given the retention job has already run successfully
    When the job is triggered again
    Then the second run deletes nothing and reports zero deleted records without an error

  @edge-case
  Scenario: A failing retention job is retried and reported
    Given the database rejects the deletion statement
    When the retention job runs
    Then the job ends with an error, is retried and raises an alert
    And no partial or corrupted state remains
