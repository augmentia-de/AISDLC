# language: en
@US-026 @E6 @FR-050 @FR-051
Feature: Warn-E-Mail an den Security-Admin

  As security admin
  I want to be alerted by e-mail when a prompt is flagged
  So that I can react without watching the interface.

  @happy-path
  Scenario: A flagged version triggers an alert to all configured recipients
    Given an organisation has two security admin recipients configured
    When a version of that organisation becomes FLAGGED
    Then one alert e-mail is delivered to each of the two recipients
    And the delivery attempt is recorded with timestamp and recipients

  @rejection-path
  Scenario: The alert contains no prompt content
    Given a version containing a secret was flagged
    When the alert e-mail is inspected
    Then the body contains only organisation, prompt id and title, version, finding categories and severities, timestamp and a deep link
    And neither the prompt text nor the secret nor the finding excerpts appear in the mail body

  @edge-case
  Scenario: The alert is sent even without configured recipients
    Given an organisation has no security admin recipient configured
    When a version of that organisation becomes FLAGGED
    Then the alert is delivered to the organisation admins and the fallback is recorded
