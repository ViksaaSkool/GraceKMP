# Building a signed Android AAB locally

This covers day-to-day local builds. The helper script is
[`tools/build-local-aab.sh`](tools/build-local-aab.sh).

## Prerequisites

- JDK 17 (`java -version`, `jarsigner`). On macOS: `brew install --cask temurin@17`.
- Android SDK (see `local.properties` `sdk.dir`).
- Upload keystore. Default: `keystore_grace.jks` in the repo root, alias `grace`.

## Secrets: `local.properties`

`local.properties` is gitignored. The script resolves each value as
`environment variable → local.properties → secure prompt`.

Add any of these to `local.properties`:

```properties
GRACE_KEYSTORE_PATH=keystore_grace.jks
GRACE_KEYSTORE_PASSWORD=...
GRACE_KEY_ALIAS=grace
GRACE_KEY_PASSWORD=...
```

Relative keystore paths resolve against the repo root. Passwords are only
exported for the Gradle invocation and are never written to disk by the script.

## Usage

```bash
./tools/build-local-aab.sh v-1.0.7
```

- `v-<version>` is required. `<version>` must be `MAJOR.MINOR` or
  `MAJOR.MINOR.PATCH` (e.g. `1.0`, `1.0.7`); it becomes `versionName`.
- `versionCode` increments automatically. The counter lives in the gitignored
  `.local-version` file (single integer). Each run adds `1` and passes
  `-PandroidVersionCode` / `-PandroidVersionName` to `:composeApp:bundleRelease`.
- Re-running the same `v-` value still bumps `versionCode`, which is Play-safe
  (gaps are fine, decreases are not).

## Version counter initialisation

The repo's committed default is `versionCode = 1` (`versionName = "1.0.4"`), and
the original Grace Play release used `versionCode 1`. The gitignored
`.local-version` is therefore seeded with `1`, so the first script run produces
`versionCode 2`. If a later build is ever uploaded to Play first (from CI or
manually), raise `.local-version` to the highest version code already uploaded
before the next run.

## Output

- Gradle emits `composeApp/build/outputs/bundle/release/composeApp-release.aab`.
- The script verifies it with `jarsigner -verify`, then copies it to
  `build-local/Grace_<versionName>.aab` (e.g. `build-local/Grace_1.0.7.aab`).
- `build-local/` is gitignored. The final line prints the path with `versionCode`
  and `versionName`.

## Troubleshooting

- `no functional Java runtime` / macOS `Unable to locate a Java Runtime`: macOS
  ships a `/usr/bin/java` stub that passes a PATH check but has no runtime
  (`/Library/Java/JavaVirtualMachines/` empty). Install JDK 17 with
  `brew install --cask temurin@17`, reopen the terminal, and retry
  `java -version`. The script detects this via `java -version` and
  `/usr/libexec/java_home -v 17`, and the failed run does not bump `.local-version`.
- `keystore not found`: check `GRACE_KEYSTORE_PATH` or the `local.properties` entry.
- `versionCode exceeds maximum`: counter passed `2100000000`; reset
  `.local-version` only if you are sure no higher code was uploaded.
- `.local-version must contain a single integer`: fix or delete the file to
  restart from the value matching the highest code already uploaded to Play.
- Unsigned build: the script always exports signing env vars, so a successful run
  is signed. A manual `./gradlew :composeApp:bundleRelease` without those env vars
  produces an unsigned AAB for inspection only.