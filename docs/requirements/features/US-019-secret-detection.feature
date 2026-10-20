# language: en
@US-019 @E4 @FR-034 @FR-038
Feature: Erkennung von Secrets

  As security admin
  I want credentials that slipped into a prompt to be found
  So that no API key ends up in the registry.

  @happy-path
  Scenario: An API key leads to a flagged prompt
    Given a developer creates a prompt that contains a live-looking API key
    When the scan runs
    Then the version reaches status FLAGGED with a finding of category SECRET
    And the excerpt shows only a masked fragment of the key

  @edge-case
  Scenario: A placeholder is not treated as a secret
    Given a developer creates a prompt that contains the placeholder YOUR_API_KEY
    When the scan runs
    Then no SECRET finding is raised and the version is not flagged for that reason

  @edge-case
  Scenario: A detected secret is reported and the transmission is recorded
    Given a prompt contains a secret and the scan runs
    When the provider is called for the classification
    Then the secret is reported as a finding and the version is FLAGGED
    And the content is transmitted unchanged to the AI service - this exposure is accepted per decision F1 and is recorded in the audit trail as metadata without content
