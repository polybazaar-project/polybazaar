# AGENTS.md

Durable rules for any AI agent (and any teammate) working in this repository. Read before acting.

## The app

PolyBazaar is a Kotlin / Jetpack Compose Android app: a peer-to-peer marketplace where students rent out items and offer skills. Backend is Firebase (Auth, Firestore, Storage) plus Google Maps and push notifications. It must work offline: Firestore persistence, and drafts that sync when the connection returns. Package: `com.android.polybazaar`.

## Architecture rules

- **MVVM.** `model/` holds data classes, repository interfaces and their Firebase implementations. `ui/` holds composables and ViewModels.
- **ViewModels never import Firebase** or a repository implementation; they depend on repository interfaces. Firebase code lives only in `model/`.
- Prefer composables and navigation over new Activities.
- Keep each change focused on its task; do not build for hypothetical future needs.

## Commands

Run from the repo root with the Gradle wrapper, exactly as CI does:

- `./gradlew ktfmtFormat` formats the code; `./gradlew ktfmtCheck` verifies it.
- `./gradlew assemble lint` builds and lints.
- `./gradlew check` runs unit tests (JUnit, Robolectric) and checks.
- `./gradlew connectedCheck` runs instrumented tests (Compose, Kaspresso); needs an emulator.
- `./gradlew jacocoTestReport` produces the coverage report that Sonar reads.

`app/google-services.json` is needed to build locally. Get it from a teammate; it is gitignored and CI generates it from the `GOOGLE_SERVICES` secret.

## Definition of done

- The behavior works and matches the task's acceptance criteria.
- New behavior comes with tests, in the same PR as the feature.
- `./gradlew ktfmtCheck` and `./gradlew check` pass locally, and CI is green.
- At least one teammate has approved the PR.

## Git and pull requests

- Branches: `feature/<name>`, `fix/<name>`, `chore/<name>`, `ci/<name>`, `docs/<name>`. Never push to `main`.
- Commits: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`, `ci:`, `chore:`), imperative, small and frequent.
- Stage only the files you changed; never `git add .` or `git add -A`.
- One task per PR. Open a draft PR early; mark it ready when tests pass. Use `.github/pull_request_template.md`.
- Code reviews and PRs are in English. Review with `.github/skills/code-review/SKILL.md`.

## Security

- Never commit `google-services.json`, API keys, tokens, `local.properties`, or a Supabase/Firebase service-account key.
- Firestore and Storage security rules are required for every collection and path the app uses.

## Acknowledging AI

The course treats unacknowledged AI use as plagiarism.

- Put this at the top of every source file an agent wrote or substantially changed: `Portions of this code were generated with the help of <tool>.`
- Add a `Co-Authored-By:` trailer to commits an agent helped write.
- Name the agent and what it did in the PR description.
- You own every line you submit. "The agent wrote it" is not a defence; be ready to explain it.
