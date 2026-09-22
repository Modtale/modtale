# Modtale Launcher

Java 26 / JavaFX 26 desktop launcher for Modtale.

## Setup

Install a full JDK 26, then run from the repository root:

```bash
cd launcher
./gradlew run
```

On Windows, use `gradlew.bat` instead of `./gradlew`. A linked Hytale account is required to access the launcher UI.

The launcher uses the production Modtale site and API by default. To use local services:

```bash
MODTALE_SITE_BASE_URL=http://localhost:5173 MODTALE_API_BASE_URL=http://localhost:8080/api/v1 ./gradlew run
```

## Game updates

Play checks Hytale's authenticated patch service for the latest build of the selected channel,
including release, pre-release, and versioned channels. The transfer animation shows download,
installation, and verification progress. A failed update prevents launch.

Verified builds are stored under `~/.modtale/launcher/game/<platform>/<channel>/<build>`;
existing official installs, mods, and saves are preserved. The next Play checks for updates again
and reuses a completed build when it is still current. Saved build numbers describe the installed
build; they do not pin a channel to an old build.

Official Wharf patches are applied and checked against their official content signatures with
[Butler 15.31.0](https://github.com/itchio/butler/releases/tag/v15.31.0).
The launcher downloads the platform tool on demand and verifies its pinned SHA-256 before use.

## Development

```bash
./gradlew test       # Run tests
./gradlew assemble   # Build without native packaging
./gradlew build      # Run checks and package for the host OS
```

Native packages are written to `build/distributions/`. Packaging requires JDK 26's `jpackage` and platform packaging tools; Linux AppImage builds require `appimagetool` on `PATH`.

Keep contributions focused and run `./gradlew test` before submitting changes.
