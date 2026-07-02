# supervisor-mobile-app — Standard Development Workflow

This file complements `.claude/CLAUDE.md` (architecture & code standards). The workflow below
is MANDATORY for every develop/modify/refactor request. Never skip a step. Never start coding
before Steps 2 AND 3 are explicitly approved by the user.

## Step 1 — Understand the requirement

- Read the full request carefully; be sure the objective is fully understood.
- If anything is unclear or ambiguous, ask simple, direct clarification questions.
- Do not make assumptions.

## Step 2 — Implementation plan (STOP: wait for approval)

Once the requirement is clear, provide a detailed plan covering:

- Objective of the change
- Overall approach
- Files to be created, modified, or removed
- Components, APIs, database, or services affected
- Edge cases and risks
- Expected output after implementation

Wait for explicit confirmation before proceeding.

## Step 3 — Test cases (STOP: wait for approval)

After the plan is approved, write all functional test cases before any implementation:

- Positive, negative, and edge-case scenarios
- Validation and error-handling tests

Wait for explicit approval of the test cases before coding.

## Step 4 — Development

Only after test cases are approved:

- Implement the feature following the existing project architecture and coding standards
  (see `.claude/CLAUDE.md` code-review checklist).
- Keep code modular, reusable, and maintainable.
- Avoid any changes outside the agreed scope unless explicitly instructed.

## UI verification loop (MANDATORY for every screen built from a design)

1. **Measure, never eyeball** — derive font sizes, paddings, radii, heights from a
   high-resolution (150dpi+) render of the design; create named tokens for measured
   values. Mapping to the nearest existing token is forbidden.
2. **Ask for Figma Dev Mode specs** (typography, spacing, effects) at Step 1 when the
   user can provide them — exact specs eliminate measurement error.
3. **Distrust platform defaults** — Material elevation ≠ Figma box-shadow (use a shared
   softShadow modifier); default component heights ≠ design heights (set explicitly);
   system font ≠ brand fonts (use font-family tokens).
4. **One batched visual QA round** — after delivery, request ONE device screenshot,
   collect ALL visual deltas, fix them in a single batch. Never iterate one fix at a time.
5. **Replicate alignment intent** — match the design's alignment axis per row/group and
   note it in a code comment.

## Step 5 — Final summary

After development is complete, provide:

- List of files changed
- Summary of the implementation
- Any assumptions made
- Commands required to run or test the changes (`./gradlew detekt :app:testDebugUnitTest`)
- Follow-up improvements or known limitations
