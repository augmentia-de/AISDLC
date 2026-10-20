# language: en
@US-027 @E6 @FR-052 @FR-070 @NFR-007
Feature: Zustellungswiederholung und Totmannschaltung

  As operator
  I want failed alert deliveries to be retried and finally surfaced
  So that no warning is silently lost.

  @rejection-path
  Scenario: A failing mail server leads to repeated attempts
    Given the mail server is temporarily unreachable and a version becomes FLAGGED
    When the notification worker retries the delivery
    Then the delivery is retried with increasing backoff and the attempt count is recorded

  @edge-case
  Scenario: After exhausting the attempts the delivery ends in the dead letter queue
    Given the mail server stays unreachable and all configured attempts are used up
    When the notification worker gives up
    Then the delivery is moved to the dead letter queue and is visible in the interface
    And the dead letter entry can be retried manually

  @happy-path
  Scenario: Mail problems never change the scan status
    Given the mail server is unreachable while a scan completes
    When the scan finishes
    Then the version reaches APPROVED or FLAGGED as the scan determined
    And the delivery failure is recorded separately and does not block the scan
