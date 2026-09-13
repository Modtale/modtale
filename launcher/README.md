# Modtale Launcher

Java 25 / JavaFX 26 desktop launcher for Modtale.

## Setup

Install a full JDK 25, then run from the repository root:

```bash
cd launcher
./gradlew run
```

On Windows, use `gradlew.bat` instead of `./gradlew`. A linked Hytale account is required to access the launcher UI.

The launcher uses the production Modtale site and API by default. To use local services:

```bash
MODTALE_SITE_BASE_URL=http://localhost:5173 MODTALE_API_BASE_URL=http://localhost:8080/api/v1 ./gradlew run
```

## Development

```bash
./gradlew test       # Run tests
./gradlew assemble   # Build without native packaging
./gradlew build      # Run checks and package for the host OS
```

Native packages are written to `build/distributions/`. Packaging requires JDK 25's `jpackage` and platform packaging tools; Linux AppImage builds require `appimagetool` on `PATH`.

Keep contributions focused and run `./gradlew test` before submitting changes.
