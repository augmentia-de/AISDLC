# language: en
@US-034 @E8 @FR-065 @FR-074 @NFR-011 @NFR-012 @NFR-017 @SEC-005 @SEC-009 @SEC-011 @SEC-012 @DP-005 @CON-005
Feature: Sicherheits-, Datenschutz- und Betriebsgrundlage

  As reviewer
  I want the security and privacy baseline to be enforced automatically
  So that no release can weaken it.

  @happy-path
  Scenario: The security scan reports no critical or high finding
    Given the application is built and deployed into the review environment
    When the security scan runs
    Then the report lists no finding rated Critical or High
    And the scan covers authentication, authorisation, injection, XSS, secrets, dependencies and the container image

  @happy-path
  Scenario: No secret is committed to the repository or baked into the image
    Given the repository and the built image are present
    When the secret scan runs over both
    Then the scan finds no credential, token or private key
    And the scan is part of the pipeline and fails the build on a hit

  @rejection-path
  Scenario: Malformed input is rejected at the boundary
    Given the application is running
    When a client sends a request with an invalid field type, an unknown enumeration value and an oversized body
    Then the API responds with 400 or 422 and a field level error
    And no unvalidated value reaches the domain logic

  @happy-path
  Scenario: Transport and response headers are hardened
    Given the application is reachable over the public endpoint
    When a client performs a request
    Then the response carries HSTS, X-Content-Type-Options and X-Frame-Options DENY plus a restrictive content security policy
    And plain HTTP is redirected to HTTPS and the certificate is valid

  @happy-path
  Scenario: Secrets are read from the environment only
    Given the application starts with a configured environment
    When the configuration is inspected at runtime
    Then every secret is resolved from an environment variable or a mounted secret
    And no secret value appears in the source tree, the image layers or the effective configuration dump

  @edge-case
  Scenario: Refresh tokens are stored hashed and data stays in the EU
    Given a refresh token has been issued and the platform runs in the target region
    When the token storage and the deployment configuration are inspected
    Then the stored value is a hash that cannot be replayed and the deployment region is europe-west3
    And the AI provider is configured without training and without durable storage
