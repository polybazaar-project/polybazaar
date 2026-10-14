---
name: open-pull-request
description: Open a PolyBazaar pull request with the team's conventions, as a draft, with a filled-in description
---

1. Check `git status` and read the full diff against `main`. Stop if unrelated changes are mixed in.
2. Run `./gradlew ktfmtCheck` and `./gradlew check`. Record what passed and what could not run.
3. Check that no PR already exists for the branch: `gh pr list --head <branch>`.
4. Open a **draft** PR with `gh pr create --draft`. The title is a Conventional Commit, such as `feat(listings): add item form`. Fill in `.github/pull_request_template.md`, starting with the short Summary.
5. Check that every file an agent touched has the `Co-authored-by` header sign-off from `AGENTS.md`.
6. Never claim a check passed unless it ran. Never force-push or merge.
