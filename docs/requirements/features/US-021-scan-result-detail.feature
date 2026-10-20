# language: en
@US-021 @E4 @FR-036 @FR-037
Feature: Scan-Ergebnis mit Fundstellen

  As developer
  I want to understand why my prompt was flagged
  So that I can fix it instead of guessing.

  @happy-path
  Scenario: The result lists every finding with category, severity and position
    Given a scan produced three findings
    When the author opens the scan result
    Then the API responds with 200 and lists category, severity, redacted excerpt, position and reasoning per finding
    And the raw prompt content is not reproduced beyond the redacted excerpts

  @rejection-path
  Scenario: The result of a foreign prompt is not readable
    Given a scan result belongs to a prompt of organisation B
    When a developer of organisation A requests it
    Then the API responds with 404 and no finding data is returned

  @edge-case
  Scenario: An approved scan shows the applied thresholds
    Given a scan completed without any finding
    When the author opens the scan result
    Then the API responds with 200 and shows that no finding was raised and which thresholds were applied
