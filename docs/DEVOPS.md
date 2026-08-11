# WearAnxietyWatch DevOps

## Repository Role

This monorepo contains the exploratory Wear OS, React Native, web, API, contracts, ML, and local infrastructure foundation for AnxietyWatch.

It is not the production public API. Production backend and web deployments live in:

- `anxietywatch-org/anxietywatch-backend`
- `anxietywatch-org/anxietywatch-web`

## Branches

- `main` is the integration branch.
- Use pull requests for feature work.
- Keep generated build outputs, Android artifacts, `.env`, and local secrets out of Git.

## CI

The `CI` workflow runs on `main`, pull requests, and manual `workflow_dispatch`.

Jobs are filtered by changed paths:

- `node`: TypeScript contracts, Express API, and Vite web app.
- `wear`: Wear OS Gradle module.
- `mobile-android`: React Native Android app.
- `ml`: FastAPI ML service.

Manual runs execute all jobs.

## Required Runtimes

- Node.js 24 in CI.
- JDK 17 for Android builds.
- Python 3.12 for the ML service.

## Local Smoke Tests

```powershell
npm.cmd ci
npm.cmd run build
npm.cmd run test
npm.cmd run lint
.\gradlew.bat :apps:wear:assembleDebug
Set-Location apps\mobile\android
.\gradlew.bat assembleDebug
```

For ML:

```powershell
python -m pip install -e "./services/ml[dev]"
python -m pytest services/ml
```

## Secrets

No production secrets are required by CI. Any future deploy keys, API tokens, signing keys, or keystores must be stored as GitHub Actions secrets or environment secrets, never committed to the repository.
