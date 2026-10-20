# language: en
@US-003 @E1 @FR-007 @SEC-001
Feature: Token-Lebenszyklus: Refresh, Rotation und Logout

  As developer
  I want my session to refresh automatically and to end it explicitly
  So that I stay signed in without leaving an open session behind.

  @happy-path
  Scenario: Refresh returns a new token pair and invalidates the old refresh token
    Given a client holds a valid access token and a valid refresh token
    When the client calls the refresh endpoint with the refresh token
    Then the API responds with 200 and a new token pair
    And the previously used refresh token is no longer valid

  @rejection-path
  Scenario: Replaying a rotated refresh token invalidates the whole session chain
    Given a refresh token has already been rotated once
    When an attacker replays the old refresh token
    Then the API responds with 401 and invalidates every token of that session chain

  @edge-case
  Scenario: An expired access token only passes through the refresh endpoint
    Given the access token has expired but the refresh token is still valid
    When the client calls a protected resource with the expired access token
    Then the API responds with 401 and instructs the client to use the refresh endpoint
    And no protected data is returned
