# language: en
@US-006 @E1 @FR-005 @FR-006 @FR-053
Feature: Mitglieder einladen und Alarmempfänger pflegen

  As organisation admin
  I want to invite colleagues and configure who receives security alerts
  So that the right people are notified.

  @happy-path
  Scenario: An invitation adds a member with a chosen role
    Given the acting user has the role ORG_ADMIN
    When the admin invites a colleague with the role DEVELOPER
    Then the API responds with 202 and creates a single-use, time-limited invitation
    And the invitation is bound to the acting organisation and to the role DEVELOPER

  @rejection-path
  Scenario: A developer cannot invite members
    Given the acting user has the role DEVELOPER
    When the user attempts to send an invitation
    Then the API responds with 403 and no invitation is created

  @edge-case
  Scenario: Without a configured security admin the alert goes to the organisation admins
    Given an organisation has no security admin recipient configured
    When a prompt version of that organisation becomes flagged
    Then the alert is delivered to the organisation admins
    And the delivery attempt is recorded with the actual recipients
