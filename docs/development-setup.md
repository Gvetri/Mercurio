# Development setup

A fresh clone builds once two machine-local settings are in place. The clone carries neither: one is
git-ignored, the other lives in the shell.

## Prerequisites

JDK 21, Android SDK Platform 35, and Build Tools 35.0.0 — the toolchain the
[README build requirements](../README.md#build) declare. The Gradle wrapper is checked in, so no separate
Gradle install is needed.

## The SDK location a clone does not carry

`local.properties` is listed in `.gitignore`, so a clone has no SDK location, and the first Android
build stops at configuration with:

```text
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by
setting the sdk.dir path in your project's local properties file at '<checkout>/local.properties'.
```

Create the file in the checkout root and point it at the directory on this machine that holds
`platforms/android-35` and `build-tools/35.0.0`:

```properties
sdk.dir=<Android SDK directory>
```

Write that shape, not a path copied from another machine: the file is per-machine, describes one
checkout, and never enters git. Gradle reads the SDK from `sdk.dir` first, then from the
`ANDROID_HOME` variable, then from the default location (`Android/Sdk` under the home directory on
Linux). A machine that keeps the SDK in the default location therefore builds without the file; any
other machine needs the file, or the export below.

## The environment variables the release-script suites read

Four suites in `.github/scripts/tests` — `test_sign_apk.py`, `test_release_artifacts.py`,
`test_stable_public_beta.py`, and `test_nightly_release.py` — reach the SDK and the JDK through a
shared fixture. It builds a disposable APK with `aapt2` from the SDK, generates a throwaway keystore
with `keytool` from the JDK, and signs the APK with `apksigner`. The suites read those tools from
`ANDROID_HOME` and `JAVA_HOME`; they never read `local.properties`. Export both before a command
that reaches them:

```text
export ANDROID_HOME=<Android SDK directory>
export JAVA_HOME=<JDK 21 directory>
```

Export them for the run, not for the checkout: the suites need them only while they execute, and a
shell that exported them earlier is what makes the difference between a suite that runs and one that
fails closed with `KeyError: 'ANDROID_HOME'` before its first test.

`./gradlew -p buildSrc test` runs all four, through the `QualityGateConfigurationTest` that the
`buildsrc-contracts` job selects with a `--tests` filter. A single suite runs on its own by path, for
example `python3 .github/scripts/tests/test_sign_apk.py`. The workflows set both variables
themselves, so this export is a local need, not a CI one.

## Build the app and run one module test

```text
./gradlew :app:assembleDebug
./gradlew :feature:entry:domain:test
```

The first command writes the debug APK to `app/build/outputs/apk/debug/app-debug.apk`; the second
runs one module's JVM unit tests. Both end in `BUILD SUCCESSFUL`, which is the point where the
checkout is ready to work in.

## Next

The loop for a change, the wall-clock each lane costs, and the full gate are in
[Verify your changes](agents/verify-your-changes.md); the one-module fast loop is
[Local quality gate for one module](agents/local-quality-gate.md).
