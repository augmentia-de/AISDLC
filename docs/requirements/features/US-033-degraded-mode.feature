# language: en
@US-033 @E8 @FR-070 @FR-071 @NFR-006 @NFR-007
Feature: Degraded Mode und manuelle Wiederholung

  As developer
  I want the platform to keep working while the AI service is down and to let me retry later
  So that an outage does not stop my team.

  @rejection-path
  Scenario: An AI outage does not stop the write path
    Given the AI service is down and the queue fills up
    When the developer creates and reads prompts
    Then the API responds normally and the queued scans wait
    And no request fails with an AI related error and no data is lost

  @edge-case
  Scenario: Queued jobs are processed in order after recovery
    Given several scan jobs are queued while the AI service is down
    When the AI service becomes available again
    Then the jobs are processed in their original order
    And each version ends in a final status

  @happy-path
  Scenario: A failed scan can be retried manually
    Given a version is in status FAILED
    When the author triggers a retry
    Then a new scan job is created and the version moves to PENDING and later to a final status
    And the manual retry is recorded in the audit trail
