# Modtale Launcher

Native Java 21 JavaFX launcher for installing and updating Modtale projects in a local Hytale mods folder. A linked Hytale account is required before the launcher UI is available. Modtale sign-in is only required for Modtale API-backed features such as browsing, installs, updates, notifications, following, and favorites; local world mod management and game launch can work without a Modtale session.

The Play tab can launch a locally installed Hytale client with official Hytale authentication. Hytale launch first tries to refresh the Hytale OAuth session and create fresh game-session tokens. If Hytale authentication is unavailable or rejects the active session, Modtale may reuse the last Hytale-issued launch tokens stored for the linked account and still starts the client in authenticated mode so Hytale itself applies its first-party offline restrictions. The launcher does not create offline identities, fabricate tokens, or provide a piracy launch mode.

## Run From Source

```bash
./gradlew run
```

Launcher sign-in talks to `https://api.modtale.net/api/v1` by default. OAuth provider flows return to a temporary local launcher callback. Browser fallback sign-in opens `https://modtale.net`. For local auth testing, override the site and API URLs with environment variables:

```bash
MODTALE_SITE_BASE_URL=http://localhost:5173 MODTALE_API_BASE_URL=http://localhost:8080/api/v1 ./gradlew run
```

Discord Rich Presence is enabled when a Discord application ID is configured:

```bash
MODTALE_DISCORD_CLIENT_ID=123456789012345678 ./gradlew run
```

The equivalent JVM property is `-Dmodtale.discordClientId=123456789012345678`. `DISCORD_CLIENT_ID` is also accepted as a fallback for environments that already provide the OAuth application ID.
Packaged builds can embed the ID with `./gradlew packageAll -PdiscordClientId=123456789012345678`.

## Build Packages

```bash
./gradlew build
```

The launcher build produces a self-contained native package for the host OS:

- Windows hosts: `build/distributions/Modtale Launcher-<version>.exe`
- macOS hosts: `build/distributions/Modtale Launcher-<version>.dmg`
- Linux hosts: `build/distributions/modtale-launcher-<version>-<arch>.AppImage`

Each package embeds its own Java runtime and launcher dependencies; users do not need a system JDK or JRE to run it. Windows and macOS builds produce OS installers. Linux `packageLinux` produces a self-contained AppImage; desktop integration is handled by the user's AppImage integration tool when present. `packageAll` is matrix-friendly: run it on Windows, macOS, and Linux hosts to produce the default release artifact for each platform.

Additional Linux package formats are available:

```bash
./gradlew packageLinuxDeb
./gradlew packageLinuxRpm
./gradlew packageLinuxFlatpak
./gradlew packageLinuxPacman
./gradlew packageLinuxAll
```

These produce:

- `build/distributions/modtale-launcher_<version>-<release>_<arch>.deb`
- `build/distributions/modtale-launcher-<version>-<release>.<arch>.rpm`
- `build/distributions/net.modtale.launcher-<version>-<arch>.flatpak`
- `build/distributions/modtale-launcher-<version>-<release>-<arch>.pkg.tar.zst`

Build hosts still need a full JDK 21 because `jpackage` is a JDK tool. Linux AppImage builds also need `appimagetool` on `PATH`; set `APPIMAGETOOL` or `-PappImageTool=/path/to/appimagetool` if it is installed elsewhere. RPM builds need `rpmbuild`, Flatpak builds need `flatpak` plus the configured Freedesktop runtime/SDK, and pacman builds need `zstd`. Windows installer builds require the native packaging tools expected by `jpackage` for `.exe` or `.msi` output.

Alternative native installer types can be selected per host:

```bash
./gradlew packageWindows -PwindowsPackageType=msi
./gradlew packageMac -PmacPackageType=pkg
```

To smoke-test a self-contained unpacked app image without creating an installer, run:

```bash
./gradlew jpackageAppImage
build/jpackage/app-image/Modtale\ Launcher/bin/Modtale\ Launcher
```

The jar remains available in `build/libs/` as a build artifact for development and diagnostics, but it is not the end-user launcher distribution.

## GitHub Releases and Updates

The launcher release workflow builds Windows, macOS, and Linux packages in GitHub Actions and attaches them to a GitHub release. Push a tag like `launcher-v0.2.0`, or run the `Launcher Release` workflow manually with a version, to publish native launcher assets.

Packaged launchers check `Modtale/modtale` releases for newer launcher builds. The updater matches release assets by platform (`.exe`/`.msi`, `.dmg`/`.pkg`, or `.AppImage`) and opens the matching installer when the user chooses to update. For development or forks, override the release source with `MODTALE_LAUNCHER_UPDATES_REPOSITORY=owner/repo`.
