# language: en
@US-018 @E4 @FR-033 @SEC-006 @SEC-007
Feature: Erkennung von Prompt Injection und Jailbreak

  As security admin
  I want attempts to manipulate the model or the checker to be detected
  So that malicious prompts do not enter the registry.

  @happy-path
  Scenario: An injection attempt is detected and blocks the prompt
    Given a developer creates a prompt whose content tries to override previous instructions
    When the scan runs
    Then the version reaches status FLAGGED with a finding of category PROMPT_INJECTION
    And the finding contains a redacted excerpt, the position and the reasoning

  @happy-path
  Scenario: A benign prompt is approved
    Given a developer creates an ordinary customer support prompt
    When the scan runs
    Then the version reaches status APPROVED and no finding is reported

  @edge-case
  Scenario: The checker is not manipulable by the content it checks
    Given a prompt contains text that instructs the classifier to report APPROVED
    When the scan runs
    Then the version is classified by the content itself and the injected instruction has no effect
    And the checker prompt keeps its system instruction and the finding is recorded
