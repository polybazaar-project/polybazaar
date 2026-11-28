<!-- Co-authored-by: Claude Code (Claude Opus 5.5) -->
# AGENTS.md

Durable rules for any AI agent (and any teammate) working in this repository. Read before acting.

## The app

PolyBazaar is a Kotlin / Jetpack Compose Android app: a peer-to-peer marketplace where students rent out items and offer skills. Backend is Firebase (Auth, Firestore, Storage) plus Google Maps and push notifications. It must work offline: Firestore persistence, and drafts that sync when the connection returns. Package: `com.android.polybazaar`.

## Where to look

- `ARCHITECTURE.md`: the packages, how the app is wired, the Firestore and Storage data, the offline mechanisms, the tests, a glossary and past decisions. Look up the package, data or mechanism your task touches.
- `.github/skills/code-review/SKILL.md` to review a PR, `.github/skills/open-pull-request/SKILL.md` to open one, and `.github/pull_request_template.md` for its description.
- `README.md`: the pitch, and how to test and deploy the Cloud Functions.
- The GitHub wiki: the Figma files, the Excalidraw diagram, the team planning and user feedback.

## Architecture rules

- **MVVM, split by feature.** Each feature package, such as `listing/`, holds `model/` (data classes, repository interfaces and their implementations), `viewmodel/` and `ui/` (composables).
- **ViewModels never import Firebase** or a repository implementation; they depend on repository interfaces. Firebase code lives only in the `model/` packages, `photo/` and `MainActivity`, which constructs the implementations.
- Prefer composables and navigation over new Activities.
- Keep each change focused on its task; do not build for hypothetical future needs.

## Commands

Run from the repo root with the Gradle wrapper, exactly as CI does:

- `./gradlew ktfmtFormat` formats the code; `./gradlew ktfmtCheck` verifies it.
- `./gradlew assemble lint` builds and lints.
- `./gradlew check` runs unit tests (JUnit, Robolectric) and checks. `./gradlew testDebugUnitTest --tests '*NameTest'` runs one test class.
- `./gradlew connectedCheck` runs instrumented tests (Compose, Kaspresso); needs an emulator.
- `./gradlew jacocoTestReport` produces the coverage report that Sonar reads.
- `(cd rules-tests && npm ci && npm test)` tests the security rules, and `(cd functions && npm ci && npm test)` the Cloud Functions. Both start the Firebase emulators, which need Java 21 and ports 8080 and 9199 free.
- `git config core.hooksPath .githooks` turns on the pre-commit hook, which runs `ktfmtCheck`.

`app/google-services.json` is needed to build locally. Get it from a teammate; it is gitignored and CI generates it from the `GOOGLE_SERVICES` secret.

A Google Maps key goes in `local.properties` as `MAPS_API_KEY=...` (gitignored; get it from a teammate). Without it the app still builds but map tiles stay blank. CI reads it from the `MAPS_API_KEY` secret, which holds the base64 of a `local.properties` file; CI keeps only its `MAPS_API_KEY` line.

A `GEOCODING_API_KEY=...` line in `local.properties` (gitignored; get it from a teammate) enables town-name suggestions for off-campus listing locations; without it no name is suggested. That key is restricted to the Geocoding API, with no application restriction. CI reads it from the `GEOCODING_API_KEY` secret, which holds the raw key.

## Definition of done

- The behavior works and matches the task's acceptance criteria.
- New behavior comes with tests, in the same PR as the feature.
- `./gradlew ktfmtCheck` and `./gradlew check` pass locally, and CI is green.
- `ARCHITECTURE.md` still matches the code: a PR that makes it wrong updates it too, such as one that adds or renames a package, a type it names, a Firestore collection or Storage path, an object that lives for the whole process, an offline mechanism or a Cloud Function.
- At least one teammate has approved the PR.

## Git and pull requests

- Branches: `feature/<name>`, `fix/<name>`, `chore/<name>`, `ci/<name>`, `docs/<name>`. Never push to `main`.
- Commits: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `ci:`, `chore:`), imperative, small and frequent.
- Stage only the files you changed; never `git add .` or `git add -A`.
- One task per PR. Open a draft PR early; mark it ready when tests pass. Use `.github/pull_request_template.md`: a short **Summary** first, then **What**, **Why**, **Validation** and **AI use**.
- Code reviews and PRs are in English. Review with `.github/skills/code-review/SKILL.md`.

## Security

- Never commit `google-services.json`, API keys, tokens, `local.properties`, or a Supabase/Firebase service-account key.
- Firestore and Storage security rules are required for every collection and path the app uses.

## Acknowledging AI

The course treats unacknowledged AI use as plagiarism.

- Every file an agent creates or modifies carries a sign-off in its header comment: `Co-authored-by: <agent name and model>`. Keep one line per agent; do not duplicate it. Files whose format has no comments (JSON, for example) are covered by the PR's AI use section instead.
- Fill in the PR's **AI use** section, naming the agent and what it did.
- You own every line you submit. "The agent wrote it" is not a defence; be ready to explain it.
