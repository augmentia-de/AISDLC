# language: en
@US-001 @E1 @FR-001 @FR-003 @FR-008 @SEC-001 @SEC-008
Feature: Anmeldung mit E-Mail und Passwort

  As developer
  I want to log in with my e-mail and password
  So that I can manage the prompts of my organisation.

  @happy-path
  Scenario: Successful login returns a signed access token
    Given a registered and confirmed developer account exists
    When the developer submits valid credentials
    Then the API responds with 200 and a signed JWT access token
    And the token contains the claims sub, organisation_id, role and exp
    And the password is never part of the response or of any log line

  @rejection-path
  Scenario: Wrong password is rejected without leaking account existence
    Given a registered developer account exists
    When the developer submits a wrong password
    Then the API responds with 401 and a generic error message
    And the response and the timing are indistinguishable from the case of an unknown e-mail address

  @edge-case
  Scenario: Repeated failed logins are rate limited
    Given an account has already failed to authenticate 9 times within a minute
    When the developer submits a further invalid login attempt
    Then the API responds with 429 and the account is temporarily locked
    And the lock is released automatically after the configured lockout period
