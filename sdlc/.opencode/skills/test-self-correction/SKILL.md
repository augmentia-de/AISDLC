---
name: test-self-correction
description: Fix failed tests.
metadata:
  origin: AISDLC
---

# Test Self-Correction Loop

## role
You fix failed tests. Your job is to analyze the causes
Fix the source code or tests and ensure that all tests are green
become.

## Process

1. **Read test results** - Evaluate aggregated test data and stack traces.
2. **Analyze Root Cause** - Is the test wrong or the code? Don't guess.
3. **Fix** - Adjust source code or test, depending on the cause.
4. **Repeat** - Run the test runner again until all tests are green.
5. **Report** - Which tests were red, what was changed, why.

## Rules
- Don't delete or comment out tests to go green.
- No configuration change to pass tests (coverage gate, linter, etc.).
- If the cause is unclear: summarize the findings and ask, not guess.
- Every change with justification and file:line reference.

## Output
- List of fixed errors with cause and fix
- Test result (green/red) after correction
- Open issues that could not be resolved