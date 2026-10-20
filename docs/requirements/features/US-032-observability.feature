# language: en
@US-032 @E8 @FR-072 @NFR-014 @FR-062
Feature: Observability: Health, Metriken und inhaltsfreie Logs

  As operator
  I want health, metrics and content free logs
  So that I can operate the platform without reading user data.

  @happy-path
  Scenario: Health, metrics and structured logs are available
    Given the application is running
    When the operator queries the health endpoint and the metrics endpoint
    Then both respond with 200 and report queue depth, scan duration, error rate, delivery rate and status distribution
    And every log line carries a correlation id

  @rejection-path
  Scenario: Log lines never contain prompt content
    Given a prompt with a secret and a customer name is created and later flagged
    When all log lines of that request are inspected
    Then no log line contains the prompt text, the secret, the customer name or the finding excerpts

  @edge-case
  Scenario: Internal errors are not leaked to the client
    Given an unexpected internal error occurs in a request handler
    When the client receives the error response
    Then the response contains a generic error and a correlation id and no stack trace or internal detail
