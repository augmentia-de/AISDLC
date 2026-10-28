# Story: US-006 — Filter nach Status und Prioritaet
# Anforderungen: FR-009, FR-010, FR-011

Feature: Filter nach Status und Prioritaet

  Scenario: Filter nach Status  # AC-006-1
    Given Tickets mit gemischten Status
    When GET /api/v1/tickets?status=open
    Then Antwort 200 OK mit nur offenen Tickets

  Scenario: Filter nach Prioritaet  # AC-006-2
    Given Tickets mit gemischten Prioritaeten
    When GET /api/v1/tickets?priority=high
    Then Antwort 200 OK mit nur high-Prioritaets-Tickets

  Scenario: Kombinierter Filter  # AC-006-3
    Given Tickets mit gemischtem Status und Prioritaet
    When GET /api/v1/tickets?status=open&priority=high
    Then Antwort 200 OK mit der Schnittmenge