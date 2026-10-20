# language: en
@US-008 @E2 @FR-012
Feature: Bearbeiten erzeugt eine neue Version und setzt die Freigabe zurück

  As security admin
  I want every content change to create a new version that must be scanned again
  So that an approved prompt can never carry unapproved content.

  @happy-path
  Scenario: Editing an approved prompt creates a new pending version
    Given version 1 of a prompt is APPROVED
    When the author changes the content and saves
    Then the API responds with 200 and creates version 2 with status PENDING
    And version 1 remains APPROVED and unchanged

  @rejection-path
  Scenario: Editing a prompt of another organisation is not possible
    Given a prompt exists in organisation B
    When a developer of organisation A submits an edit for that prompt
    Then the API responds with 404 and no version is created

  @edge-case
  Scenario: A save without content change creates no new version
    Given version 1 of a prompt is APPROVED
    When the author saves without changing title, content, category or tags
    Then the API responds with 200 and returns version 1 unchanged
    And the number of versions stays at one and no scan is triggered
