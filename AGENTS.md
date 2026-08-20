# Codex PR Review Instructions

When reviewing a pull request:

## 1. Comment on new issues

Leave review comments for problems that are introduced by the changes in the current pull request.

Do not leave review comments for unrelated problems that already existed before this branch.

## 2. Summarise pre-existing issues

If you notice significant problems that already existed on the base branch, do not treat them as PR findings.

Instead, include them separately in the review summary under:

### Pre-existing issues found

Keep these separate from issues introduced by the current PR.

## 3. Check version code and version name

Verify that both the application version code and version name have been updated correctly.

The build action runs when changes are pushed to `develop`, so by the end of the pull request:

* The version code must be unique and newer than the version currently on `develop`.
* The version name must be updated appropriately for the new build.
* Flag the PR if either value has not been updated correctly.

## 4. Identify unit test opportunities

Review the changes for opportunities to add unit tests, with the goal of increasing test coverage.

Prioritise tests that cover:

* New logic.
* Changed behaviour.
* Bug fixes.
* Edge cases.
* Validation.
* Error handling.
* Branches and conditions that are not currently tested.
* Regressions that could reasonably occur in future.

Where useful, point out specific functions, classes, or behaviours that should gain unit tests as a result of the PR.

Prefer meaningful tests that increase coverage while verifying real behaviour, rather than tests that only exercise trivial implementation details.

## Review principle

Keep the review focused on the current pull request.

PR comments should identify problems caused by the new changes.

Pre-existing problems should only appear in the separate summary.
