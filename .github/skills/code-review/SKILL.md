---
name: code-review
description: Team checklist for reviewing a pull request in PolyBazaar, whether written by a teammate or an AI agent
---
<!-- Co-authored-by: Claude Code (Claude Opus 5.5) -->

Read the whole diff and the surrounding code, then review against AGENTS.md and these points. Report each as OK or ISSUE, and do not approve what you cannot review.

- **Scope:** the PR does one task; unrelated changes are split out.
- **Tests:** new behavior has tests that assert behavior (not just that code runs), including edge cases and failures; CI is green.
- **Architecture:** MVVM holds; ViewModels do not import Firebase or repository implementations.
- **Offline:** the feature still works, or degrades cleanly, without a connection.
- **Security:** no secrets or `google-services.json`; Firestore/Storage rules cover new data.
- **Docs:** `ARCHITECTURE.md` still matches the code, including any new or renamed package, type it names, collection, Storage path, process-wide object, offline mechanism or Cloud Function.
- **Clarity:** names and commit messages explain the change; no dead code or over-engineering.
- **AI acknowledgement:** every file an agent touched carries its `Co-authored-by` header where AGENTS.md requires one, and the PR's **AI use** section names the agent and what it did.

Comment on the code, not the author. Prefix each comment with `Important`, `Question` or `Nitpick`, and include something positive when it applies. Write in English.
