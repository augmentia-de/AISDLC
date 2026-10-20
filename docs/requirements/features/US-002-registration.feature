# language: en
@US-002 @E1 @FR-002
Feature: Registrierung erzeugt eine eigene Organisation

  As new user
  I want to register and create my own organisation
  So that my team and I can start managing prompts.

  @happy-path
  Scenario: Registration creates an organisation and makes the user its admin
    Given no account exists for the given e-mail address
    When the user registers with e-mail and password
    Then the API responds with 201, creates a new organisation and assigns the role ORG_ADMIN to the user
    And the access token contains the id of the newly created organisation

  @rejection-path
  Scenario: Registration with an existing e-mail is rejected
    Given an account already exists for the given e-mail address
    When the user tries to register again
    Then the API responds with 409 and no organisation is created

  @edge-case
  Scenario: Registration cannot join an existing organisation
    Given an existing organisation with developers already exists
    When a new user registers and tries to select that organisation
    Then the API ignores the attempted organisation selection
    And the new user is a member of a separate new organisation only
