# language: en
@US-016 @E4 @FR-030 @FR-031 @NFR-001 @NFR-002 @NFR-007 @CON-009
Feature: Asynchroner Prüflauf

  As developer
  I want my prompt to be saved immediately while the check runs in the background
  So that the interface never blocks.

  @happy-path
  Scenario: Saving returns immediately and the scan follows
    Given an authenticated developer creates a new prompt
    When the developer submits the prompt and the scan worker picks the job up
    Then the API responds with 201 within the latency budget and the version later reaches status APPROVED
    And the status change is recorded in the history

  @rejection-path
  Scenario: An unavailable AI service does not break the write path
    Given the AI service is unreachable
    When the developer submits a new prompt
    Then the API responds with 201 and the prompt is stored
    And the version stays PENDING or moves to FAILED and the developer sees a retry option

  @edge-case
  Scenario: A worker restart does not lose queued jobs
    Given several scan jobs are queued and the worker is restarted
    When the worker comes back up
    Then all queued jobs are processed in their original order
    And no job is lost and no version stays in SCANNING forever
