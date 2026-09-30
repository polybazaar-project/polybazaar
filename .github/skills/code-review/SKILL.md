---
name: code-review
description: Team checklist for reviewing a pull request in PolyBazaar, whether written by a teammate or an AI agent
---

Read the whole diff and the surrounding code, then review against AGENTS.md and these points. Report each as OK or ISSUE, and do not approve what you cannot review.

- **Scope:** the PR does one task; unrelated changes are split out.
- **Tests:** new behavior has tests that assert behavior (not just that code runs), including edge cases and failures; CI is green.
- **Architecture:** MVVM holds; ViewModels do not import Firebase or repository implementations.
- **Offline:** the feature still works, or degrades cleanly, without a connection.
- **Security:** no secrets or `google-services.json`; Firestore/Storage rules cover new data.
- **Clarity:** names and commit messages explain the change; no dead code or over-engineering.
- **AI acknowledgement:** files an agent wrote carry the header note, commits have `Co-Authored-By`, and the PR names the agent.

Comment on the code, not the author. Prefix each comment with `Important`, `Question` or `Nitpick`, and include something positive when it applies. Write in English.
