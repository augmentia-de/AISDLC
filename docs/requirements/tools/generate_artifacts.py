#!/usr/bin/env python3
"""PromptGuard — Phase-1 generator.

Single source of truth for US-001..US-033. Emits:
  docs/requirements/stories/US-*.json
  docs/requirements/features/US-*.feature
  docs/requirements/traceability_matrix.json

Run:  python3 docs/requirements/tools/generate_artifacts.py
Regenerate after the grill-me decisions (F1-F9) have been answered.
"""
import json
import os
import re
from collections import OrderedDict

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
OUT = os.path.join(ROOT, "docs", "requirements")
STORIES_DIR = os.path.join(OUT, "stories")
FEATURES_DIR = os.path.join(OUT, "features")
GEN_TS = "2026-10-20T00:00:00Z"

E1 = "E1 — Identität & Zugriff"
E2 = "E2 — Prompt-Verwaltung"
E3 = "E3 — Suche & Filterung"
E4 = "E4 — Asynchrone KI-Prüfung"
E5 = "E5 — Status & Live-Aktualisierung"
E6 = "E6 — Alarmierung"
E7 = "E7 — Datenschutz & Aufbewahrung"
E8 = "E8 — Qualität & Betrieb"

S = []


def story(sid, epic, slug, title, description, reqs, priority, scenarios, level="integration",
          constraint_refs=None, notes=None):
    S.append(
        {
            "id": sid,
            "epic": epic,
            "slug": slug,
            "title": title,
            "description": description,
            "requirement_ids": reqs,
            "constraint_refs": constraint_refs or [],
            "priority": priority,
            "status": "todo",
            "scenarios": scenarios,
            "default_level": level,
            "notes": notes or [],
        }
    )


def sc(name, stype, given, when, then, *extra, **kw):
    """sc(name, type, given, when, then, [extra And-lines], level='integration')"""
    d = {
        "name": name,
        "type": stype,
        "given": given,
        "when": when,
        "then": then,
        "test_level": kw.get("level", "integration"),
    }
    if extra:
        d["and"] = list(extra)
    return d


# ================================================================ E1
story(
    "US-001", E1, "login", "Anmeldung mit E-Mail und Passwort",
    "As a developer, I want to log in with my e-mail and password, so that I can manage the prompts of my organisation.",
    ["FR-001", "FR-003", "FR-008", "SEC-001", "SEC-008"], "must-have",
    [
        sc("Successful login returns a signed access token", "happy-path",
           "a registered and confirmed developer account exists",
           "the developer submits valid credentials",
           "the API responds with 200 and a signed JWT access token",
           "the token contains the claims sub, organisation_id, role and exp",
           "the password is never part of the response or of any log line"),
        sc("Wrong password is rejected without leaking account existence", "rejection-path",
           "a registered developer account exists",
           "the developer submits a wrong password",
           "the API responds with 401 and a generic error message",
           "the response and the timing are indistinguishable from the case of an unknown e-mail address"),
        sc("Repeated failed logins are rate limited", "edge-case",
           "an account has already failed to authenticate 9 times within a minute",
           "the developer submits a further invalid login attempt",
           "the API responds with 429 and the account is temporarily locked",
           "the lock is released automatically after the configured lockout period"),
    ],
)

story(
    "US-002", E1, "registration", "Registrierung erzeugt eine eigene Organisation",
    "As a new user, I want to register and create my own organisation, so that my team and I can start managing prompts.",
    ["FR-002"], "must-have",
    [
        sc("Registration creates an organisation and makes the user its admin", "happy-path",
           "no account exists for the given e-mail address",
           "the user registers with e-mail and password",
           "the API responds with 201, creates a new organisation and assigns the role ORG_ADMIN to the user",
           "the access token contains the id of the newly created organisation"),
        sc("Registration with an existing e-mail is rejected", "rejection-path",
           "an account already exists for the given e-mail address",
           "the user tries to register again",
           "the API responds with 409 and no organisation is created"),
        sc("Registration cannot join an existing organisation", "edge-case",
           "an existing organisation with developers already exists",
           "a new user registers and tries to select that organisation",
           "the API ignores the attempted organisation selection",
           "the new user is a member of a separate new organisation only"),
    ],
    constraint_refs=["A-07"],
)

story(
    "US-003", E1, "token-lifecycle", "Token-Lebenszyklus: Refresh, Rotation und Logout",
    "As a developer, I want my session to refresh automatically and to end it explicitly, so that I stay signed in without leaving an open session behind.",
    ["FR-007", "SEC-001"], "must-have",
    [
        sc("Refresh returns a new token pair and invalidates the old refresh token", "happy-path",
           "a client holds a valid access token and a valid refresh token",
           "the client calls the refresh endpoint with the refresh token",
           "the API responds with 200 and a new token pair",
           "the previously used refresh token is no longer valid"),
        sc("Replaying a rotated refresh token invalidates the whole session chain", "rejection-path",
           "a refresh token has already been rotated once",
           "an attacker replays the old refresh token",
           "the API responds with 401 and invalidates every token of that session chain"),
        sc("An expired access token only passes through the refresh endpoint", "edge-case",
           "the access token has expired but the refresh token is still valid",
           "the client calls a protected resource with the expired access token",
           "the API responds with 401 and instructs the client to use the refresh endpoint",
           "no protected data is returned"),
    ],
)

story(
    "US-004", E1, "roles", "Rollen und Berechtigungen",
    "As an organisation admin, I want three distinct roles, so that only the right people can administer members, categories and flagged prompts.",
    ["FR-003", "FR-004", "SEC-004"], "must-have",
    [
        sc("An organisation admin manages categories", "happy-path",
           "the acting user has the role ORG_ADMIN",
           "the user creates a new category",
           "the API responds with 201 and the category belongs to the acting organisation"),
        sc("A developer is not allowed to administer categories", "rejection-path",
           "the acting user has the role DEVELOPER",
           "the user attempts to create a category",
           "the API responds with 403 and no category is created"),
        sc("Only the security admin may release a flagged prompt", "edge-case",
           "a prompt version is flagged with severity HIGH",
           "a developer attempts to release it while the security admin succeeds",
           "the developer receives 403 and the security admin receives 200 with status WAIVED",
           "both attempts are recorded in the audit trail"),
    ],
)

story(
    "US-005", E1, "tenant-isolation", "Striktes Mandantenscoping auf jeder Route",
    "As a security admin, I want a hard separation between organisations, so that no developer can ever see the prompts of another department.",
    ["FR-009", "FR-023", "SEC-002", "SEC-003"], "must-have",
    [
        sc("A listing returns only the prompts of the own organisation", "happy-path",
           "the acting user is a developer of organisation A which owns three prompts",
           "the user lists the prompts",
           "the API responds with exactly the three prompts of organisation A",
           "no field of any other organisation is present in the response"),
        sc("Accessing a foreign prompt by id does not reveal its existence", "rejection-path",
           "a prompt with a known id exists in organisation B",
           "a developer of organisation A requests that prompt by id",
           "the API responds with 404 and an empty body",
           "neither the title nor the owner nor the scan status leaks"),
        sc("A search never reveals foreign hits through counts or previews", "edge-case",
           "a distinctive term occurs only in prompts of organisation B",
           "a developer of organisation A searches for that term",
           "the API responds with an empty result list and total count 0",
           "the response contains no suggestion, no facet and no hint that a foreign match exists"),
    ],
    constraint_refs=["I-2"],
)

story(
    "US-006", E1, "membership-and-alert-recipients", "Mitglieder einladen und Alarmempfänger pflegen",
    "As an organisation admin, I want to invite colleagues and configure who receives security alerts, so that the right people are notified.",
    ["FR-005", "FR-006", "FR-053"], "must-have",
    [
        sc("An invitation adds a member with a chosen role", "happy-path",
           "the acting user has the role ORG_ADMIN",
           "the admin invites a colleague with the role DEVELOPER",
           "the API responds with 202 and creates a single-use, time-limited invitation",
           "the invitation is bound to the acting organisation and to the role DEVELOPER"),
        sc("A developer cannot invite members", "rejection-path",
           "the acting user has the role DEVELOPER",
           "the user attempts to send an invitation",
           "the API responds with 403 and no invitation is created"),
        sc("Without a configured security admin the alert goes to the organisation admins", "edge-case",
           "an organisation has no security admin recipient configured",
           "a prompt version of that organisation becomes flagged",
           "the alert is delivered to the organisation admins",
           "the delivery attempt is recorded with the actual recipients"),
    ],
)

# ================================================================ E2
story(
    "US-007", E2, "prompt-create", "Prompt anlegen",
    "As a developer, I want to create a prompt with title, content, category and tags, so that it becomes part of the central registry.",
    ["FR-010", "FR-011", "FR-030"], "must-have",
    [
        sc("Creating a prompt returns immediately with status pending", "happy-path",
           "an authenticated developer belongs to an organisation with a default category list",
           "the developer submits a valid prompt with title, content, category and two tags",
           "the API responds with 201 within the latency budget and creates version 1 with status PENDING",
           "the response contains no scan result yet"),
        sc("Invalid input is rejected with a field-level error", "rejection-path",
           "an authenticated developer is present",
           "the developer submits a prompt without a title",
           "the API responds with 422 and names the offending field",
           "no prompt and no version is created"),
        sc("Oversized content is rejected without storing a partial prompt", "edge-case",
           "an authenticated developer is present",
           "the developer submits a prompt whose content exceeds the configured maximum length",
           "the API responds with 422 and states the limit",
           "neither a prompt nor a partial version is persisted"),
    ],
)

story(
    "US-008", E2, "prompt-edit-new-version", "Bearbeiten erzeugt eine neue Version und setzt die Freigabe zurück",
    "As a security admin, I want every content change to create a new version that must be scanned again, so that an approved prompt can never carry unapproved content.",
    ["FR-012"], "must-have",
    [
        sc("Editing an approved prompt creates a new pending version", "happy-path",
           "version 1 of a prompt is APPROVED",
           "the author changes the content and saves",
           "the API responds with 200 and creates version 2 with status PENDING",
           "version 1 remains APPROVED and unchanged"),
        sc("Editing a prompt of another organisation is not possible", "rejection-path",
           "a prompt exists in organisation B",
           "a developer of organisation A submits an edit for that prompt",
           "the API responds with 404 and no version is created"),
        sc("A save without content change creates no new version", "edge-case",
           "version 1 of a prompt is APPROVED",
           "the author saves without changing title, content, category or tags",
           "the API responds with 200 and returns version 1 unchanged",
           "the number of versions stays at one and no scan is triggered"),
    ],
    constraint_refs=["I-1"],
)

story(
    "US-009", E2, "version-history", "Versionshistorie und Versionsvergleich",
    "As a developer, I want to see how a prompt evolved, so that I understand what changed and which version is in use.",
    ["FR-013", "NFR-005"], "must-have",
    [
        sc("The version history lists all versions with metadata", "happy-path",
           "a prompt has three versions created by two different authors",
           "the author opens the version history",
           "the API responds with 200 and lists version number, author, timestamp, status and finding count per version"),
        sc("Versions of another organisation are not readable", "rejection-path",
           "a version belongs to a prompt of organisation B",
           "a developer of organisation A requests that version",
           "the API responds with 404 and no version data is returned"),
        sc("Older versions stay immutable and comparable", "edge-case",
           "a prompt has two versions with different content",
           "the author compares version 1 with version 2",
           "the API responds with 200 and returns a diff of the two contents",
           "neither version is modified by the comparison"),
    ],
)

story(
    "US-010", E2, "categories", "Kategorien verwalten",
    "As an organisation admin, I want to maintain the category list, so that prompts are classified consistently.",
    ["FR-014"], "must-have",
    [
        sc("A new organisation starts with the seed category list", "happy-path",
           "an organisation has just been created",
           "the developer lists the categories",
           "the API responds with 200 and the seed categories Customer Support, Code Gen, Data Analysis, Documentation and Other"),
        sc("The organisation admin creates and renames categories", "happy-path",
           "the acting user has the role ORG_ADMIN",
           "the admin creates the category Support Bot and later renames it",
           "the API responds with 201 and later 200 and the category keeps its id"),
        sc("A category in use can only be deactivated, not deleted", "edge-case",
           "a category is referenced by at least one prompt",
           "the admin attempts to delete it",
           "the API responds with 409 and the category remains usable but inactive",
           "existing prompts keep their reference"),
    ],
    constraint_refs=["A-06"],
)

story(
    "US-011", E2, "tags", "Tags vergeben",
    "As a developer, I want to label prompts with free tags, so that I can find them later.",
    ["FR-015"], "must-have",
    [
        sc("Tags are normalised and stored once per organisation", "happy-path",
           "an authenticated developer is present",
           "the developer assigns the tags Support, deutsch and support",
           "the API responds with 200 and stores the two distinct normalised tags Support and deutsch"),
        sc("Too many tags are rejected", "rejection-path",
           "an authenticated developer is present",
           "the developer assigns eleven tags to one prompt",
           "the API responds with 422 and names the limit",
           "the prompt keeps its previous tag set"),
        sc("The organisation tag limit is enforced", "edge-case",
           "the organisation already uses the maximum number of distinct tags",
           "the developer assigns a completely new tag",
           "the API responds with 422 and the tag limit is reported",
           "an already existing tag can still be assigned"),
    ],
)

story(
    "US-012", E2, "prompt-delete", "Prompt löschen",
    "As an organisation admin, I want to delete obsolete prompts including their history, so that the registry only contains relevant material.",
    ["FR-016", "DP-004"], "must-have",
    [
        sc("Deleting a prompt removes its versions and findings", "happy-path",
           "a prompt has two versions with scans and findings",
           "the author deletes the prompt",
           "the API responds with 204 and prompt, versions, scans and findings no longer exist",
           "an audit entry without prompt content remains"),
        sc("A prompt of another organisation cannot be deleted", "rejection-path",
           "a prompt exists in organisation B",
           "a developer of organisation A attempts to delete it",
           "the API responds with 404 and the prompt is untouched"),
        sc("Deleting a prompt of a colleague is recorded in the audit trail", "edge-case",
           "a prompt was created by developer X and is being deleted by the organisation admin",
           "the admin deletes the prompt",
           "the API responds with 204 and the audit entry names the deleting actor, not only the author",
           "the audit entry contains no prompt content"),
    ],
)

# ================================================================ E3
story(
    "US-013", E3, "fulltext-search", "Volltextsuche",
    "As a developer, I want to search prompts by title and content, so that I find an existing prompt instead of writing a new one.",
    ["FR-020", "NFR-004"], "must-have",
    [
        sc("A term inside the content finds the prompt", "happy-path",
           "the own organisation owns three prompts, one of them containing the term Refundierungsrichtlinie",
           "the developer searches for Refundierungsrichtlinie",
           "the API responds with 200 and returns exactly that prompt with a highlighted match"),
        sc("A term without match returns an empty list", "rejection-path",
           "no prompt of the own organisation contains the search term",
           "the developer searches for a term that only exists in another organisation",
           "the API responds with 200 and an empty result list with total count 0"),
        sc("Search input is treated as data, not as an instruction", "edge-case",
           "an authenticated developer is present",
           "the developer searches for a term containing SQL metacharacters and a prompt injection phrase",
           "the API responds with 200 or 422 and never returns database errors or prompt contents",
           "no injected statement is executed"),
    ],
)

story(
    "US-014", E3, "filtering", "Filtern nach Kategorie, Tag und Prüfstatus",
    "As a developer, I want to combine filters, so that I can narrow down a large registry.",
    ["FR-021"], "must-have",
    [
        sc("Combined filters narrow the result set", "happy-path",
           "the own organisation owns prompts in several categories, tags and scan statuses",
           "the developer filters by category Code Gen, tag python and status FLAGGED",
           "the API responds with 200 and only prompts matching all three criteria"),
        sc("A foreign category id yields no results", "rejection-path",
           "a category belongs to another organisation",
           "the developer filters by that category id",
           "the API responds with 200 and an empty list without any hint that the category exists"),
        sc("Pagination stays stable while filters are active", "edge-case",
           "the filtered result set spans several pages",
           "the developer requests the second page with the same filters",
           "the API responds with 200 and returns the next page of the same filtered set",
           "the total count matches the filtered set, not the unfiltered one"),
    ],
)

story(
    "US-015", E3, "sorting-pagination", "Sortierung und Pagination",
    "As a developer, I want to sort and page through the registry, so that large lists stay usable.",
    ["FR-022", "NFR-008"], "must-have",
    [
        sc("Results can be sorted by update time", "happy-path",
           "the own organisation owns prompts with different update timestamps",
           "the developer sorts by updated_at descending",
           "the API responds with 200 and the prompts are ordered from newest to oldest"),
        sc("A page beyond the result range is empty", "rejection-path",
           "the filtered result set contains three pages",
           "the developer requests page 99",
           "the API responds with 200 and an empty page with the correct total count"),
        sc("An oversized page size is clamped or rejected", "edge-case",
           "an authenticated developer is present",
           "the developer requests a page size of 5000",
           "the API responds with 400 and states the maximum page size",
           "the response contains at most the maximum number of items"),
    ],
)

# ================================================================ E4
story(
    "US-016", E4, "async-scan", "Asynchroner Prüflauf",
    "As a developer, I want my prompt to be saved immediately while the check runs in the background, so that the interface never blocks.",
    ["FR-030", "FR-031", "NFR-001", "NFR-002", "NFR-007", "CON-009"], "must-have",
    [
        sc("Saving returns immediately and the scan follows", "happy-path",
           "an authenticated developer creates a new prompt",
           "the developer submits the prompt and the scan worker picks the job up",
           "the API responds with 201 within the latency budget and the version later reaches status APPROVED",
           "the status change is recorded in the history", level="e2e"),
        sc("An unavailable AI service does not break the write path", "rejection-path",
           "the AI service is unreachable",
           "the developer submits a new prompt",
           "the API responds with 201 and the prompt is stored",
           "the version stays PENDING or moves to FAILED and the developer sees a retry option"),
        sc("A worker restart does not lose queued jobs", "edge-case",
           "several scan jobs are queued and the worker is restarted",
           "the worker comes back up",
           "all queued jobs are processed in their original order",
           "no job is lost and no version stays in SCANNING forever", level="unit"),
    ],
)

story(
    "US-017", E4, "duplicate-detection", "Duplikatwarnung innerhalb der eigenen Organisation",
    "As a developer, I want to be warned when a similar prompt already exists in my organisation, so that I reuse instead of duplicating work.",
    ["FR-032"], "must-have",
    [
        sc("A near-duplicate produces a warning with matches", "happy-path",
           "the own organisation already owns a prompt with almost identical content",
           "the developer creates a prompt with very similar content",
           "the scan result contains a duplicate warning listing up to five similar prompts with title and similarity score",
           "the creation itself is not blocked"),
        sc("Identical content in another organisation produces no warning", "rejection-path",
           "an identical prompt exists in another organisation only",
           "the developer creates the prompt in their own organisation",
           "the scan result contains no duplicate warning and no hint about the foreign prompt"),
        sc("Similarity below the configured threshold produces no warning", "edge-case",
           "a prompt of the own organisation is only loosely related",
           "the developer creates the prompt",
           "the scan result contains no duplicate warning",
           "the threshold is configurable per organisation"),
    ],
    constraint_refs=["A-08"],
)

story(
    "US-018", E4, "injection-jailbreak-detection", "Erkennung von Prompt Injection und Jailbreak",
    "As a security admin, I want attempts to manipulate the model or the checker to be detected, so that malicious prompts do not enter the registry.",
    ["FR-033", "SEC-006", "SEC-007"], "must-have",
    [
        sc("An injection attempt is detected and blocks the prompt", "happy-path",
           "a developer creates a prompt whose content tries to override previous instructions",
           "the scan runs",
           "the version reaches status FLAGGED with a finding of category PROMPT_INJECTION",
           "the finding contains a redacted excerpt, the position and the reasoning"),
        sc("A benign prompt is approved", "happy-path",
           "a developer creates an ordinary customer support prompt",
           "the scan runs",
           "the version reaches status APPROVED and no finding is reported"),
        sc("The checker is not manipulable by the content it checks", "edge-case",
           "a prompt contains text that instructs the classifier to report APPROVED",
           "the scan runs",
           "the version is classified by the content itself and the injected instruction has no effect",
           "the checker prompt keeps its system instruction and the finding is recorded"),
    ],
)

story(
    "US-019", E4, "secret-detection", "Erkennung von Secrets",
    "As a security admin, I want credentials that slipped into a prompt to be found, so that no API key ends up in the registry.",
    ["FR-034", "FR-038"], "must-have",
    [
        sc("An API key leads to a flagged prompt", "happy-path",
           "a developer creates a prompt that contains a live-looking API key",
           "the scan runs",
           "the version reaches status FLAGGED with a finding of category SECRET",
           "the excerpt shows only a masked fragment of the key"),
        sc("A placeholder is not treated as a secret", "edge-case",
           "a developer creates a prompt that contains the placeholder YOUR_API_KEY",
           "the scan runs",
           "no SECRET finding is raised and the version is not flagged for that reason", level="unit"),
        sc("A detected secret is reported and the transmission is recorded", "edge-case",
           "a prompt contains a secret and the scan runs",
           "the provider is called for the classification",
           "the secret is reported as a finding and the version is FLAGGED",
           "the content is transmitted unchanged to the AI service - this exposure is accepted per decision F1 and is recorded in the audit trail as metadata without content"),
    ],
)

story(
    "US-020", E4, "pii-detection", "Erkennung personenbezogener Daten",
    "As a data protection officer, I want personal data inside prompts to be detected, so that no customer data stays in the registry unnoticed.",
    ["FR-035"], "must-have",
    [
        sc("Personal data leads to a flagged prompt", "happy-path",
           "a developer creates a prompt that contains a customer e-mail address and a customer name",
           "the scan runs",
           "the version reaches status FLAGGED with findings of category PII",
           "the excerpts are masked and the positions are reported"),
        sc("Example data stays below the flagging threshold", "edge-case",
           "a developer creates a prompt that contains the address support@example.com",
           "the scan runs",
           "a finding of category PII with severity LOW is reported",
           "the version is not flagged because the configured threshold is MEDIUM", level="unit"),
        sc("Semantic personal data is detected as well", "edge-case",
           "a developer creates a prompt that mentions a customer with a full name in running text",
           "the scan runs",
           "a PII finding is reported with the semantic reasoning of the classifier"),
    ],
)

story(
    "US-021", E4, "scan-result-detail", "Scan-Ergebnis mit Fundstellen",
    "As a developer, I want to understand why my prompt was flagged, so that I can fix it instead of guessing.",
    ["FR-036", "FR-037"], "must-have",
    [
        sc("The result lists every finding with category, severity and position", "happy-path",
           "a scan produced three findings",
           "the author opens the scan result",
           "the API responds with 200 and lists category, severity, redacted excerpt, position and reasoning per finding",
           "the raw prompt content is not reproduced beyond the redacted excerpts"),
        sc("The result of a foreign prompt is not readable", "rejection-path",
           "a scan result belongs to a prompt of organisation B",
           "a developer of organisation A requests it",
           "the API responds with 404 and no finding data is returned"),
        sc("An approved scan shows the applied thresholds", "edge-case",
           "a scan completed without any finding",
           "the author opens the scan result",
           "the API responds with 200 and shows that no finding was raised and which thresholds were applied"),
    ],
)

story(
    "US-022", E4, "external-provider-transfer", "Übermittlung an den KI-Dienst unter festen Auflagen",
    "As a data protection officer, I want every transfer to the AI service to run only against a verified provider configuration, so that the data transfer is provably compliant.",
    ["FR-038", "FR-074", "DP-002", "DP-005", "SEC-006"], "must-have",
    [
        sc("The prompt content is transmitted unchanged", "happy-path",
           "the provider configuration has been verified and a prompt is ready to be classified",
           "the scanner calls the AI service",
           "the provider receives the original prompt text without redaction",
           "the transmission is recorded in the audit trail as metadata without content", level="unit"),
        sc("A provider configuration that violates the terms aborts the scan", "rejection-path",
           "the configured provider does not guarantee retention free processing in the EU",
           "the scanner would call the AI service",
           "the call is not executed and the version moves to FAILED",
           "no prompt content leaves the system", level="unit"),
        sc("A failing provider call leaves the version in a recoverable state", "edge-case",
           "the provider is reachable but the call fails with a timeout",
           "the scan runs",
           "the version moves to FAILED and can be retried manually",
           "no partial scan result is stored"),
    ],
)

story(
    "US-023", E4, "waiver", "Manuelle Ausnahme-Freigabe eines geflaggten Prompts",
    "As a security admin, I want to release a flagged prompt with a documented reason, so that false positives do not block my teams.",
    ["FR-039", "SEC-010"], "must-have",
    [
        sc("The security admin releases a flagged version with a reason", "happy-path",
           "a version is FLAGGED with a finding of severity HIGH",
           "the security admin releases it with a reason",
           "the API responds with 200 and the version reaches status WAIVED",
           "the audit trail records actor, timestamp and reason"),
        sc("A developer cannot release a flagged version", "rejection-path",
           "a version is FLAGGED",
           "a developer attempts to release it",
           "the API responds with 403 and the status stays FLAGGED"),
        sc("A release without a reason is rejected", "edge-case",
           "a version is FLAGGED",
           "the security admin submits a release without a reason",
           "the API responds with 422 and the status stays FLAGGED",
           "no audit entry is written"),
    ],
    constraint_refs=["I-3"],
)

# ================================================================ E5
story(
    "US-024", E5, "live-status", "Live-Aktualisierung des Prüfstatus in der Oberfläche",
    "As a developer, I want the interface to show the status change without a reload, so that I know immediately whether my prompt is approved.",
    ["FR-040", "NFR-003", "FR-042"], "must-have",
    [
        sc("The status change appears without reloading", "happy-path",
           "a prompt version is in status SCANNING and the user has the list open",
           "the scan finishes and the status changes to APPROVED",
           "the interface shows APPROVED within two seconds without a manual reload", level="e2e"),
        sc("The live channel rejects unauthenticated clients", "rejection-path",
           "no valid token is presented",
           "a client opens the live status channel",
           "the server refuses the connection and no status data is streamed", level="e2e"),
        sc("After a lost connection the current state is restored", "edge-case",
           "the live channel drops while a scan is running",
           "the client reconnects",
           "the client receives the current status of the prompt and continues to receive updates",
           "no intermediate update is delivered twice with a different state", level="e2e"),
    ],
)

story(
    "US-025", E5, "audit-trail", "Status- und Änderungshistorie",
    "As a security admin, I want an audit trail of status changes, so that I can reconstruct what happened with a prompt.",
    ["FR-041", "SEC-010", "FR-060"], "must-have",
    [
        sc("Every status transition is recorded", "happy-path",
           "a version went from PENDING to SCANNING to APPROVED",
           "the author opens the history",
           "the API responds with 200 and lists all three transitions with timestamp and cause"),
        sc("The history of another organisation is not readable", "rejection-path",
           "a history belongs to a prompt of organisation B",
           "a developer of organisation A requests it",
           "the API responds with 404 and no history entry is returned"),
        sc("Audit entries without content are removed after the retention period", "edge-case",
           "audit entries older than 90 days exist",
           "the retention job runs",
           "those entries are deleted and no prompt content is left behind",
           "the remaining entries are unaffected"),
    ],
)

# ================================================================ E6
story(
    "US-026", E6, "security-alert-email", "Warn-E-Mail an den Security-Admin",
    "As a security admin, I want to be alerted by e-mail when a prompt is flagged, so that I can react without watching the interface.",
    ["FR-050", "FR-051"], "must-have",
    [
        sc("A flagged version triggers an alert to all configured recipients", "happy-path",
           "an organisation has two security admin recipients configured",
           "a version of that organisation becomes FLAGGED",
           "one alert e-mail is delivered to each of the two recipients",
           "the delivery attempt is recorded with timestamp and recipients", level="manual"),
        sc("The alert contains no prompt content", "rejection-path",
           "a version containing a secret was flagged",
           "the alert e-mail is inspected",
           "the body contains only organisation, prompt id and title, version, finding categories and severities, timestamp and a deep link",
           "neither the prompt text nor the secret nor the finding excerpts appear in the mail body"),
        sc("The alert is sent even without configured recipients", "edge-case",
           "an organisation has no security admin recipient configured",
           "a version of that organisation becomes FLAGGED",
           "the alert is delivered to the organisation admins and the fallback is recorded"),
    ],
)

story(
    "US-027", E6, "notification-retry", "Zustellungswiederholung und Totmannschaltung",
    "As an operator, I want failed alert deliveries to be retried and finally surfaced, so that no warning is silently lost.",
    ["FR-052", "FR-070", "NFR-007"], "must-have",
    [
        sc("A failing mail server leads to repeated attempts", "rejection-path",
           "the mail server is temporarily unreachable and a version becomes FLAGGED",
           "the notification worker retries the delivery",
           "the delivery is retried with increasing backoff and the attempt count is recorded"),
        sc("After exhausting the attempts the delivery ends in the dead letter queue", "edge-case",
           "the mail server stays unreachable and all configured attempts are used up",
           "the notification worker gives up",
           "the delivery is moved to the dead letter queue and is visible in the interface",
           "the dead letter entry can be retried manually"),
        sc("Mail problems never change the scan status", "happy-path",
           "the mail server is unreachable while a scan completes",
           "the scan finishes",
           "the version reaches APPROVED or FLAGGED as the scan determined",
           "the delivery failure is recorded separately and does not block the scan"),
    ],
)

# ================================================================ E7
story(
    "US-028", E7, "retention-job", "Retention-Löschung nach 90 Tagen",
    "As a data protection officer, I want old telemetry deleted automatically, so that the platform keeps only what it may keep.",
    ["FR-060", "FR-061", "DP-003", "NFR-013"], "must-have",
    [
        sc("Telemetry older than 90 days is deleted, prompts are kept", "happy-path",
           "log entries, scan raw data and notification records older than 90 days exist and prompts of any age exist",
           "the retention job runs",
           "the telemetry is deleted and the prompts and versions remain",
           "the job writes a summary of the deleted volume"),
        sc("Running the job twice changes nothing", "edge-case",
           "the retention job has already run successfully",
           "the job is triggered again",
           "the second run deletes nothing and reports zero deleted records without an error"),
        sc("A failing retention job is retried and reported", "edge-case",
           "the database rejects the deletion statement",
           "the retention job runs",
           "the job ends with an error, is retried and raises an alert",
           "no partial or corrupted state remains"),
    ],
    constraint_refs=["A-03"],
)

story(
    "US-029", E7, "right-to-erasure", "Recht auf Löschung",
    "As a user, I want my account and personal references to be deletable, so that my personal data is removed when I request it.",
    ["FR-063", "DP-004", "DP-007", "DP-008"], "must-have",
    [
        sc("Erasing a user removes the account and anonymises authorship", "happy-path",
           "a user authored three prompts and holds an active session",
           "the organisation admin triggers the erasure",
           "the account, its tokens and its sessions are deleted and the authorship in the three prompts is anonymised",
           "the prompts themselves remain and are queued for a new scan"),
        sc("A user of another organisation cannot be erased", "rejection-path",
           "a user belongs to organisation B",
           "an admin of organisation A attempts the erasure",
           "the API responds with 403 and nothing is deleted"),
        sc("Erasure does not destroy the audit trail", "edge-case",
           "the erased user performed security relevant actions",
           "the erasure is executed",
           "the audit entries remain with actor and timestamp but without the personal reference and without content"),
    ],
)

story(
    "US-030", E7, "privacy-notice", "Datenschutzhinweis in Deutsch und Englisch",
    "As a user, I want a clear privacy notice in my language, so that I know what happens with my data.",
    ["FR-064", "DP-001", "DP-006"], "must-have",
    [
        sc("The notice is reachable in both languages", "happy-path",
           "the application is running",
           "a user opens the privacy notice",
           "the API responds with the notice in German and in English including purpose, legal basis, retention periods and rights",
           level="manual"),
        sc("The notice is versioned", "edge-case",
           "the notice text has been changed in a new release",
           "a user opens the notice",
           "the API returns the current version and the version identifier is exposed",
           "the previous version is archived for proof", level="manual"),
    ],
    level="manual",
)

# ================================================================ E8
story(
    "US-031", E8, "coverage-gate", "Testabdeckung und Qualitäts-Gate",
    "As a reviewer, I want the build to fail below 80 percent coverage, so that quality is enforced and not just intended.",
    ["NFR-009", "NFR-010", "FR-073", "CON-008"], "must-have",
    [
        sc("The pipeline runs tests and measures coverage", "happy-path",
           "a change is pushed to a branch",
           "the pipeline runs",
           "the pipeline executes unit, integration and end to end tests and reports the line coverage of backend and frontend"),
        sc("Coverage below the threshold fails the build", "rejection-path",
           "the measured line coverage is 74 percent",
           "the pipeline evaluates the coverage gate",
           "the pipeline fails with the missing percentage and the change cannot be merged"),
        sc("The tenant isolation test is part of the mandatory set", "edge-case",
           "a developer changes a repository query",
           "the pipeline runs the integration tests",
           "the cross tenant test runs against a real database and fails when a query loses its organisation filter"),
    ],
)

story(
    "US-032", E8, "observability", "Observability: Health, Metriken und inhaltsfreie Logs",
    "As an operator, I want health, metrics and content free logs, so that I can operate the platform without reading user data.",
    ["FR-072", "NFR-014", "FR-062"], "must-have",
    [
        sc("Health, metrics and structured logs are available", "happy-path",
           "the application is running",
           "the operator queries the health endpoint and the metrics endpoint",
           "both respond with 200 and report queue depth, scan duration, error rate, delivery rate and status distribution",
           "every log line carries a correlation id"),
        sc("Log lines never contain prompt content", "rejection-path",
           "a prompt with a secret and a customer name is created and later flagged",
           "all log lines of that request are inspected",
           "no log line contains the prompt text, the secret, the customer name or the finding excerpts"),
        sc("Internal errors are not leaked to the client", "edge-case",
           "an unexpected internal error occurs in a request handler",
           "the client receives the error response",
           "the response contains a generic error and a correlation id and no stack trace or internal detail"),
    ],
)

story(
    "US-033", E8, "degraded-mode", "Degraded Mode und manuelle Wiederholung",
    "As a developer, I want the platform to keep working while the AI service is down and to let me retry later, so that an outage does not stop my team.",
    ["FR-070", "FR-071", "NFR-006", "NFR-007"], "must-have",
    [
        sc("An AI outage does not stop the write path", "rejection-path",
           "the AI service is down and the queue fills up",
           "the developer creates and reads prompts",
           "the API responds normally and the queued scans wait",
           "no request fails with an AI related error and no data is lost"),
        sc("Queued jobs are processed in order after recovery", "edge-case",
           "several scan jobs are queued while the AI service is down",
           "the AI service becomes available again",
           "the jobs are processed in their original order",
           "each version ends in a final status"),
        sc("A failed scan can be retried manually", "happy-path",
           "a version is in status FAILED",
           "the author triggers a retry",
           "a new scan job is created and the version moves to PENDING and later to a final status",
           "the manual retry is recorded in the audit trail"),
    ],
)

# ================================================================ cross-cutting
story(
    "US-034", E8, "security-baseline", "Sicherheits-, Datenschutz- und Betriebsgrundlage",
    "As a reviewer, I want the security and privacy baseline to be enforced automatically, so that no release can weaken it.",
    ["FR-065", "FR-074", "NFR-011", "NFR-012", "NFR-017", "SEC-005", "SEC-009", "SEC-011", "SEC-012", "DP-005", "CON-005"],
    "must-have",
    [
        sc("The security scan reports no critical or high finding", "happy-path",
           "the application is built and deployed into the review environment",
           "the security scan runs",
           "the report lists no finding rated Critical or High",
           "the scan covers authentication, authorisation, injection, XSS, secrets, dependencies and the container image", level="manual"),
        sc("No secret is committed to the repository or baked into the image", "happy-path",
           "the repository and the built image are present",
           "the secret scan runs over both",
           "the scan finds no credential, token or private key",
           "the scan is part of the pipeline and fails the build on a hit"),
        sc("Malformed input is rejected at the boundary", "rejection-path",
           "the application is running",
           "a client sends a request with an invalid field type, an unknown enumeration value and an oversized body",
           "the API responds with 400 or 422 and a field level error",
           "no unvalidated value reaches the domain logic"),
        sc("Transport and response headers are hardened", "happy-path",
           "the application is reachable over the public endpoint",
           "a client performs a request",
           "the response carries HSTS, X-Content-Type-Options and X-Frame-Options DENY plus a restrictive content security policy",
           "plain HTTP is redirected to HTTPS and the certificate is valid"),
        sc("Secrets are read from the environment only", "happy-path",
           "the application starts with a configured environment",
           "the configuration is inspected at runtime",
           "every secret is resolved from an environment variable or a mounted secret",
           "no secret value appears in the source tree, the image layers or the effective configuration dump"),
        sc("Refresh tokens are stored hashed and data stays in the EU", "edge-case",
           "a refresh token has been issued and the platform runs in the target region",
           "the token storage and the deployment configuration are inspected",
           "the stored value is a hash that cannot be replayed and the deployment region is europe-west3",
           "the AI provider is configured without training and without durable storage"),
    ],
)

story(
    "US-035", E8, "accessibility-and-typing", "Barrierefreiheit, Typsicherheit und Bedienbarkeit",
    "As a developer, I want an accessible and strictly typed interface, so that the platform can be used by everyone and stays maintainable.",
    ["NFR-015", "NFR-016", "NFR-018"], "must-have",
    [
        sc("The interface is fully operable with the keyboard", "happy-path",
           "the application is running",
           "a user navigates prompt list, detail view, editor and version history using the keyboard only",
           "every interactive element is reachable in a logical order and operable without a mouse",
           "focus is visible at all times", level="manual"),
        sc("Screen readers announce the relevant elements", "happy-path",
           "the application is running",
           "a screen reader user opens the prompt list and a flagged prompt",
           "the scan status is announced with its text label and not only by colour",
           "form fields have associated labels and the status change is announced", level="manual"),
        sc("The build fails on type or lint errors", "rejection-path",
           "a change with a type error is pushed",
           "the pipeline runs",
           "the pipeline fails with the type error and the change cannot be merged",
           "no implicit any, no unhandled promise and no unresolved lint warning is tolerated"),
        sc("The interface is German while the API contract stays English", "edge-case",
           "the application is running",
           "a user reads the interface and an API client reads an error response",
           "the interface texts are German and the API error codes and field names are English",
           "the language of the interface can be switched and the choice is remembered"),
    ],
)

# ================================================================ emit
os.makedirs(STORIES_DIR, exist_ok=True)
os.makedirs(FEATURES_DIR, exist_ok=True)

story_index = []
for s in S:
    sid = s["id"]
    acs = []
    for i, scn in enumerate(s["scenarios"], start=1):
        ac = {
            "id": "AC-%s-%d" % (sid.split("-")[1], i),
            "given": scn["given"],
            "when": scn["when"],
            "then": scn["then"],
            "and": scn.get("and", []),
            "test_level": scn.get("test_level", s["default_level"]),
            "scenario_ref": "%s-%s.feature: %s" % (sid, s["slug"], scn["name"]),
        }
        acs.append(ac)

    doc = OrderedDict(
        [
            ("id", sid),
            ("epic", s["epic"]),
            ("title", s["title"]),
            ("description", s["description"]),
            ("requirement_ids", s["requirement_ids"]),
            ("constraint_refs", s["constraint_refs"]),
            ("acceptance_criteria", acs),
            ("priority", s["priority"]),
            ("status", s["status"]),
            ("feature_file", "docs/requirements/features/%s-%s.feature" % (sid, s["slug"])),
            ("notes", s["notes"]),
        ]
    )
    with open(os.path.join(STORIES_DIR, "%s.json" % sid), "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")

    lines = ["# language: en"]
    tags = ["@%s" % sid, "@%s" % s["epic"].split(" ")[0]] + ["@%s" % r for r in s["requirement_ids"]]
    lines.append(" ".join(tags))
    lines.append("Feature: %s" % s["title"])
    lines.append("")
    desc = s["description"]
    parts = desc.split(", I want ", 1)
    if len(parts) == 2:
        role = parts[0].replace("As a ", "").replace("As an ", "")
        rest = parts[1]
        want, _, so = rest.partition(", so that ")
        lines.append("  As %s" % role)
        lines.append("  I want %s" % want.strip())
        lines.append("  So that %s" % so.strip())
    else:
        lines.append("  %s" % desc)
    lines.append("")
    for scn in s["scenarios"]:
        lines.append("  @%s" % scn["type"])
        lines.append("  Scenario: %s" % scn["name"])
        lines.append("    Given %s" % scn["given"])
        lines.append("    When %s" % scn["when"])
        lines.append("    Then %s" % scn["then"])
        for extra in scn.get("and", []):
            lines.append("    And %s" % extra)
        lines.append("")
    with open(os.path.join(FEATURES_DIR, "%s-%s.feature" % (sid, s["slug"])), "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    story_index.append((s, acs))

# ================================================================ traceability
REQ_STORY = OrderedDict()
for s, acs in story_index:
    for r in s["requirement_ids"]:
        REQ_STORY.setdefault(r, []).append((s, acs))

# Requirements that exist in the PRD but are verified by architecture artefacts (ADR / openapi.yaml)
# instead of by a user story. Kept explicit so that nobody mistakes them for forgotten requirements.
ARCH_CONSTRAINTS = {
    "CON-001": "Backend auf JVM (Langchain4j) - ADR-001",
    "CON-002": "Frontend React 19, TypeScript strict - ADR-001",
    "CON-003": "PostgreSQL 16 mit organisation_id-Scoping und RLS - ADR-002, schema.sql",
    "CON-004": "KI-Zugriff ausschliesslich ueber Langchain4j, Anbieter konfigurierbar - ADR-006",
    "CON-006": "Alle APIs folgen docs/api/openapi.yaml (OAS 3.0+) - openapi.yaml",
    "CON-007": "Deployment Docker nach Cloud Run europe-west3 - ADR-001, deploy pipeline",
    "CON-010": "Mandantentrennung auch auf Infrastruktur-Ebene - ADR-002",
}

entries = []
for r in sorted(REQ_STORY):
    pairs = REQ_STORY[r]
    entries.append(
        OrderedDict(
            [
                ("requirement_id", r),
                ("story_ids", [p[0]["id"] for p in pairs]),
                ("acceptance_criteria", [ac["id"] for p in pairs for ac in p[1]]),
                ("features", ["docs/requirements/features/%s-%s.feature" % (p[0]["id"], p[0]["slug"]) for p in pairs]),
                ("tests", []),
                ("code", []),
                ("coverage_state", "seeded"),
                ("verification", "test"),
                ("gap_blocks_gate_1", False),
            ]
        )
    )

gaps = []
for e in entries:
    gaps.append(
        OrderedDict(
            [
                ("requirement_id", e["requirement_id"]),
                ("kind", "no-test-yet"),
                ("reason", "Greenfield: no test exists yet - to be created in step 5 (test matrix) and step 6 (implementation)"),
                ("blocks_gate_1", False),
                ("resolved_in", "step-5"),
            ]
        )
    )

for r, reason in sorted(ARCH_CONSTRAINTS.items()):
    gaps.append(
        OrderedDict(
            [
                ("requirement_id", r),
                ("kind", "architecture-constraint"),
                ("reason", "Architecture constraint without a user story - verified in phase 2 via %s" % reason),
                ("blocks_gate_1", False),
                ("resolved_in", "step-2"),
            ]
        )
    )

requirements_without_story = [
    OrderedDict(
        [
            ("requirement_id", "FR-017"),
            ("kind", "post-mvp"),
            ("reason", "Prompt kopieren is marked SOLL and deliberately has no story in this backlog - scope decision O1/O2, see PRD 6.3"),
            ("blocks_gate_1", False),
        ]
    )
]
gaps.extend(requirements_without_story)

manual = []
for s, acs in story_index:
    for ac in acs:
        if ac["test_level"] == "manual":
            manual.append(
                OrderedDict(
                    [
                        ("acceptance_criteria", ac["id"]),
                        ("story_id", s["id"]),
                        ("reason", "Manually verifiable - legal text, deliverability check, scan report review or user interface behaviour"),
                    ]
                )
            )

matrix = OrderedDict(
    [
        ("generated_at", GEN_TS),
        ("project", "PromptGuard"),
        ("phase", "1 — Anforderungserhebung"),
        ("schema_version", "1.1"),
        (
            "notes",
            [
                "Greenfield-Projekt: es existieren noch keine Tests und kein Anwendungscode.",
                "tests und code bleiben daher leer; sie werden in Phase 5 (Test-Setup) und Phase 6 (Implementierung) gefuellt.",
                "Nicht automatisierbare Kriterien sind mit test_level 'manual' markiert und zaehlen nicht als Gap.",
                "Die Spalte code verweist ab Phase 6 auf die dann realisierten Module.",
                "Gap-Art 'no-test-yet' blockiert Gate 1 nicht: es ist der Planungsstand eines Greenfield-Projekts.",
                "Gap-Art 'architecture-constraint' wird in Phase 2 ueber ADR und openapi.yaml nachgewiesen.",
            ],
        ),
        ("entries", entries),
        ("gaps", gaps),
        ("requirements_without_story", requirements_without_story),
        ("architecture_constraints", sorted(ARCH_CONSTRAINTS)),
        ("manual_criteria", manual),
        (
            "coverage_summary",
            OrderedDict(
                [
                    ("requirements_total", len(entries) + len(ARCH_CONSTRAINTS) + len(requirements_without_story)),
                    ("requirements_with_story", len(entries)),
                    ("requirements_architecture_verified", len(ARCH_CONSTRAINTS)),
                    ("requirements_post_mvp", len(requirements_without_story)),
                    ("requirements_with_test", 0),
                    ("acceptance_criteria_total", sum(len(a) for _, a in story_index)),
                    ("manual_criteria_total", len(manual)),
                    ("stories_total", len(S)),
                    ("features_total", len(S)),
                ]
            ),
        ),
    ]
)

with open(os.path.join(OUT, "traceability_matrix.json"), "w", encoding="utf-8") as f:
    json.dump(matrix, f, ensure_ascii=False, indent=2)
    f.write("\n")

print("stories   :", len(S))
print("features  :", len(os.listdir(FEATURES_DIR)))
print("matrix    :", len(entries), "requirements,", sum(len(a) for _, a in story_index), "acceptance criteria")

# ================================================================ human readable artefacts
ART = os.path.join(OUT, "artifacts")
os.makedirs(ART, exist_ok=True)

REQ_RE = re.compile(r"^\|\s*((?:FR|NFR|SEC|DP|CON)-\d+)\s*\|\s*(.+?)\s*\|(.*)$")
REQ_TEXT = OrderedDict()
REQ_PRIO = {}
prd = os.path.join(OUT, "PRD.md")
if os.path.exists(prd):
    with open(prd, encoding="utf-8") as f:
        for line in f:
            m = REQ_RE.match(line.rstrip("\n"))
            if not m:
                continue
            rid, text, rest = m.group(1), m.group(2), m.group(3)
            text = text.replace("**", "").strip()
            REQ_TEXT[rid] = text
            REQ_PRIO[rid] = "MUSS" if "MUSS" in rest else ("SOLL" if "SOLL" in rest else "—")

REQ_STORIES = OrderedDict()
for s, acs in story_index:
    for r in s["requirement_ids"]:
        REQ_STORIES.setdefault(r, []).append(s["id"])

lines = ["# PromptGuard — Anforderungskatalog (kompakt)", ""]
lines.append("| Feld | Wert |")
lines.append("|---|---|")
lines.append("| Quelle | `docs/requirements/PRD.md` (Abschnitte 8-12) |")
lines.append("| Stand | %s |" % GEN_TS)
lines.append("| Anforderungen | %d |" % len(REQ_TEXT))
lines.append("| User Stories | %d |" % len(S))
lines.append("")
lines.append("| ID | Anforderung | Prio | Story | Status |")
lines.append("|---|---|---|---|---|")
for rid, text in REQ_TEXT.items():
    stories = REQ_STORIES.get(rid, [])
    if rid in ARCH_CONSTRAINTS:
        status = "Architektur (Phase 2)"
    elif stories:
        status = "Story vorhanden, Tests offen"
    elif rid == "FR-017":
        status = "Post-MVP ohne Story"
    else:
        status = "**Lücke — keine Story**"
    lines.append(
        "| `%s` | %s | %s | %s | %s |"
        % (rid, text.replace("|", "\\|"), REQ_PRIO.get(rid, "—"), ", ".join(stories) or "—", status)
    )
lines.append("")
with open(os.path.join(ART, "requirements.md"), "w", encoding="utf-8") as f:
    f.write("\n".join(lines))

EPIC_ORDER = []
for s in S:
    if s["epic"] not in EPIC_ORDER:
        EPIC_ORDER.append(s["epic"])

lines = ["# PromptGuard — User Stories und Akzeptanzkriterien", ""]
lines.append("Alle %d Stories in %d Epics. Maschinenlesbare Fassung: `docs/requirements/stories/US-*.json`." % (len(S), len(EPIC_ORDER)))
lines.append("BDD: `docs/requirements/features/US-*.feature`")
lines.append("")
for epic in EPIC_ORDER:
    stories = [s for s in S if s["epic"] == epic]
    lines.append("## %s" % epic)
    lines.append("")
    for s in stories:
        lines.append("### %s — %s" % (s["id"], s["title"]))
        lines.append("")
        lines.append("%s" % s["description"])
        lines.append("")
        lines.append("- **Anforderungen:** %s" % ", ".join("`%s`" % r for r in s["requirement_ids"]))
        if s["constraint_refs"]:
            lines.append("- **Randbedingungen:** %s" % ", ".join("`%s`" % r for r in s["constraint_refs"]))
        lines.append("- **Priorität:** %s · **Feature:** `%s`" % (s["priority"], "docs/requirements/features/%s-%s.feature" % (s["id"], s["slug"])))
        lines.append("")
        lines.append("| AC | Given | When | Then | Test-Level |")
        lines.append("|---|---|---|---|---|")
        for i, scn in enumerate(s["scenarios"], start=1):
            ac_id = "AC-%s-%d" % (s["id"].split("-")[1], i)
            given = scn["given"]
            when = scn["when"]
            then = scn["then"] + (" · " + " · ".join(scn.get("and", [])) if scn.get("and") else "")
            lines.append(
                "| `%s` | %s | %s | %s | %s |"
                % (ac_id, given, when, then.replace("|", "\\|"), scn.get("test_level", s["default_level"]))
            )
        lines.append("")
with open(os.path.join(ART, "user-stories.md"), "w", encoding="utf-8") as f:
    f.write("\n".join(lines))

print("artefacts : requirements.md, user-stories.md")

