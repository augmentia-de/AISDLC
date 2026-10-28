# Story: US-011 — Health-Check und Betriebsbereitschaft
# Anforderungen: FR-021, NFR-005, SEC-004, CON-001, CON-002, CON-003

Feature: Health-Check und Betriebsbereitschaft

  Scenario: Health-Endpoint  # AC-011-1
    Given der Server laeuft
    When GET /health (oder /api/v1/health)
    Then Antwort 200 OK mit Body {status: 'ok'}