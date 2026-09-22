#![cfg_attr(target_os = "windows", windows_subsystem = "windows")]

mod dialog;

use fs2::FileExt;
use serde::Deserialize;
use sha2::{Digest, Sha256};
use std::{
    env,
    fs::{self, File, OpenOptions},
    io::{Read, Write},
    path::{Path, PathBuf},
    process::{Command, Stdio},
    thread,
    time::{Duration, Instant},
};

type Result<T> = std::result::Result<T, Box<dyn std::error::Error>>;
const MAX_ARCHIVE: u64 = 256 * 1024 * 1024;
const MAX_EXTRACTED: u64 = 1024 * 1024 * 1024;

#[derive(Deserialize)]
struct Config {
    jvm_args: Vec<String>,
}
#[derive(Deserialize)]
struct Asset {
    binary: Binary,
}
#[derive(Deserialize)]
struct Binary {
    package: Package,
}
#[derive(Deserialize)]
struct Package {
    link: String,
    checksum: String,
    size: u64,
}

fn main() {
    match run() {
        Ok(code) => std::process::exit(code),
        Err(error) => {
            let message = format!(
                "Modtale could not start.\n\n{error}\n\nCheck your connection and available disk space, then reopen Modtale to retry. You can also install Java 25 and set JAVA_HOME. Hytale is not required.\n\nDetails: ~/.modtale/launcher/bootstrap.log"
            );
            eprintln!("{message}");
            if let Ok(home) = home()
                && let Ok(mut log) = OpenOptions::new()
                    .create(true)
                    .append(true)
                    .open(home.join(".modtale/launcher/bootstrap.log"))
            {
                let _ = writeln!(log, "{message}");
            }
            if !diagnostic() {
                dialog::message("Modtale Launcher", &message, true);
            }
            std::process::exit(1);
        }
    }
}

fn diagnostic() -> bool {
    env::args_os()
        .nth(1)
        .is_some_and(|a| a == "--modtale-bootstrap-check")
}
fn http_agent(timeout: Duration) -> ureq::Agent {
    let config = ureq::Agent::config_builder();
    #[cfg(any(windows, target_os = "macos"))]
    let config = config.tls_config(
        ureq::tls::TlsConfig::builder()
            .provider(ureq::tls::TlsProvider::NativeTls)
            .root_certs(ureq::tls::RootCerts::PlatformVerifier)
            .build(),
    );
    config
        .https_only(true)
        .timeout_global(Some(timeout))
        .timeout_connect(Some(Duration::from_secs(20)))
        .timeout_recv_body(Some(Duration::from_secs(30)))
        .build()
        .into()
}
fn java_name() -> &'static str {
    if cfg!(windows) { "java.exe" } else { "java" }
}
fn home() -> Result<PathBuf> {
    env::var_os(if cfg!(windows) { "USERPROFILE" } else { "HOME" })
        .map(PathBuf::from)
        .ok_or_else(|| "Cannot locate your home directory".into())
}
fn command(java: &Path) -> Command {
    let mut c = Command::new(java);
    // Match jpackage: do not let unrelated Java installations inject VM options.
    for key in [
        "JAVA_TOOL_OPTIONS",
        "_JAVA_OPTIONS",
        "JDK_JAVA_OPTIONS",
        "CLASSPATH",
    ] {
        c.env_remove(key);
    }
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        c.creation_flags(0x08000000); // CREATE_NO_WINDOW
    }
    c
}
fn app_directory(exe: &Path) -> Result<PathBuf> {
    let parent = exe.parent().ok_or("Launcher has no parent directory")?;
    Ok(if cfg!(windows) {
        parent.join("app")
    } else if cfg!(target_os = "macos") {
        parent.join("../app")
    } else {
        parent.join("../lib/app")
    })
}
fn java_candidates(home: &Path, cache: &Path) -> Vec<PathBuf> {
    let mut roots = vec![];
    // A custom install can opt in without editing the launcher installation.
    if let Some(root) = env::var_os("MODTALE_HYTALE_HOME") {
        roots.push(PathBuf::from(root));
    }
    if cfg!(windows) {
        for key in ["APPDATA", "LOCALAPPDATA"] {
            if let Some(root) = env::var_os(key) {
                roots.push(PathBuf::from(root).join("Hytale"));
            }
        }
        roots.push(home.join("AppData/Roaming/Hytale"));
        if let Some(root) = env::var_os("ProgramFiles") {
            roots.push(PathBuf::from(root).join("Hypixel Studios/Hytale Launcher"));
        }
    } else if cfg!(target_os = "macos") {
        roots.push(home.join("Library/Application Support/Hytale"));
        roots.push(PathBuf::from(
            "/Applications/Hytale Launcher.app/Contents/MacOS",
        ));
    } else {
        for key in ["XDG_DATA_HOME", "HOST_XDG_DATA_HOME"] {
            if let Some(root) = env::var_os(key) {
                roots.push(PathBuf::from(root).join("Hytale"));
            }
        }
        for root in [
            ".var/app/com.hypixel.HytaleLauncher/data/Hytale",
            ".local/share/Hytale",
            ".config/Hytale",
            ".hytale",
        ] {
            roots.push(home.join(root));
        }
    }
    roots.push(home.join("Hytale"));
    let mut paths = vec![];
    if let Some(java) = env::var_os("MODTALE_JAVA") {
        paths.push(PathBuf::from(java));
    }
    for root in roots {
        for branch in ["release", "pre-release", "prerelease"] {
            paths.push(root.join(format!(
                "install/{branch}/package/jre/latest/bin/{}",
                java_name()
            )));
            paths.push(root.join(format!(
                "install/{branch}/package/jre/latest/Contents/Home/bin/{}",
                java_name()
            )));
        }
        paths.push(root.join("jre/latest/bin").join(java_name()));
        paths.push(root.join("jre/latest/Contents/Home/bin").join(java_name()));
    }
    if let Some(java) = find_java(cache, 4) {
        paths.push(java);
    }
    if let Some(root) = env::var_os("JAVA_HOME") {
        paths.push(PathBuf::from(root).join("bin").join(java_name()));
    }
    if let Some(path) = env::var_os("PATH") {
        paths.extend(
            env::split_paths(&path)
                .filter(|p| p.is_absolute())
                .map(|p| p.join(java_name())),
        );
    }
    paths
}
fn find_java(root: &Path, depth: usize) -> Option<PathBuf> {
    let direct = root.join("bin").join(java_name());
    if direct.is_file() {
        return Some(direct);
    }
    if depth == 0 {
        return None;
    }
    let mut dirs: Vec<_> = fs::read_dir(root)
        .ok()?
        .filter_map(|e| e.ok())
        .filter(|e| e.file_type().is_ok_and(|t| t.is_dir()))
        .map(|e| e.path())
        .collect();
    dirs.sort();
    dirs.into_iter().find_map(|p| find_java(&p, depth - 1))
}
fn probe(java: &Path, app: &Path, log: &File) -> bool {
    probe_with_timeout(java, app, log, Duration::from_secs(15))
}
fn probe_with_timeout(java: &Path, app: &Path, log: &File, timeout: Duration) -> bool {
    if !java.is_file() {
        return false;
    }
    let Ok(output) = log.try_clone() else {
        return false;
    };
    let Ok(errors) = log.try_clone() else {
        return false;
    };
    let Ok(mut child) = command(java)
        .args(["--enable-native-access=ALL-UNNAMED", "-cp"])
        .arg(app.join("*"))
        .arg("net.modtale.launcher.RuntimeProbe")
        .stdin(Stdio::null())
        .stdout(output)
        .stderr(errors)
        .spawn()
    else {
        return false;
    };
    let deadline = Instant::now() + timeout;
    loop {
        match child.try_wait() {
            Ok(Some(status)) => return status.success(),
            Ok(None) if Instant::now() < deadline => thread::sleep(Duration::from_millis(50)),
            _ => {
                let _ = child.kill();
                let _ = child.wait();
                return false;
            }
        }
    }
}
fn run() -> Result<i32> {
    let home = home()?;
    let state = home.join(".modtale/launcher");
    fs::create_dir_all(&state)?;
    let log_path = state.join("bootstrap.log");
    if fs::metadata(&log_path).is_ok_and(|m| m.len() > 2 * 1024 * 1024) {
        let _ = fs::rename(&log_path, state.join("bootstrap.previous.log"));
    }
    let mut log = OpenOptions::new()
        .create(true)
        .append(true)
        .open(state.join("bootstrap.log"))?;
    let installed_app = app_directory(&env::current_exe()?)?;
    let base_config = fs::read(installed_app.join("bootstrap.json"))?;
    let executable = env::var_os("APPIMAGE")
        .map(PathBuf::from)
        .unwrap_or(env::current_exe()?);
    let update_root = update_root(&state, &executable, &base_config);
    let cache = state.join("runtime-25");
    let java = install_runtime(&state, &cache, &installed_app, &log)?;
    let mut app = installed_app.clone();
    if let Some(candidate) = active_update(&update_root) {
        if serde_json::from_slice::<Config>(
            &fs::read(candidate.join("bootstrap.json")).unwrap_or_default(),
        )
        .is_ok()
            && probe(&java, &candidate, &log)
        {
            app = candidate;
        } else {
            writeln!(
                log,
                "Update startup check failed; using the installed launcher."
            )?;
            let _ = fs::remove_file(update_root.join("active"));
            let _ = fs::write(
                update_root.join("update-failure"),
                "The update failed its startup check. The installed launcher has been restored. Try updating again.",
            );
        }
    }
    let config: Config = serde_json::from_reader(File::open(app.join("bootstrap.json"))?)?;
    writeln!(log, "Validated runtime: {}", java.display())?;
    writeln!(log, "Launcher application: {}", app.display())?;
    if diagnostic() {
        return Ok(0);
    }
    writeln!(log, "Starting Modtale with {}", java.display())?;
    // Replace the Unix bootstrap process so the Dock/taskbar does not retain a second launcher.
    let mut launch = application_command(&java, &app, &config);
    launch
        .env("MODTALE_UPDATE_ROOT", &update_root)
        .env("MODTALE_LAUNCHER_EXECUTABLE", &executable)
        .args(env::args_os().skip(1))
        .stdout(log.try_clone()?)
        .stderr(log.try_clone()?);
    #[cfg(unix)]
    {
        use std::os::unix::process::CommandExt;
        Err(launch.exec().into())
    }
    #[cfg(windows)]
    {
        let status = launch.spawn()?.wait()?;
        if !status.success() {
            return Err(format!(
                "The launcher exited with {status}. See bootstrap.log for details."
            )
            .into());
        }
        Ok(0)
    }
}
fn update_root(state: &Path, executable: &Path, base_config: &[u8]) -> PathBuf {
    let mut hash = Sha256::new();
    hash.update(executable.as_os_str().as_encoded_bytes());
    hash.update([0]);
    hash.update(base_config);
    state.join("updates").join(format!("{:x}", hash.finalize()))
}

fn active_update(root: &Path) -> Option<PathBuf> {
    let name = fs::read_to_string(root.join("active")).ok()?;
    if !name.starts_with("version-")
        || !name.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'-')
    {
        return None;
    }
    let app = root.join(name);
    if app.is_dir() { Some(app) } else { None }
}

fn application_command(java: &Path, app: &Path, config: &Config) -> Command {
    let mut launch = command(java);
    launch.args(&config.jvm_args);
    #[cfg(target_os = "macos")]
    {
        launch.arg("-Xdock:name=Modtale Launcher");
        if let Ok(icons) = fs::read_dir(
            env::current_exe()
                .ok()
                .and_then(|exe| app_directory(&exe).ok())
                .unwrap_or_else(|| app.to_path_buf())
                .join("../Resources"),
        ) {
            if let Some(icon) = icons
                .filter_map(|entry| entry.ok())
                .map(|entry| entry.path())
                .find(|path| path.extension().is_some_and(|ext| ext == "icns"))
            {
                let mut argument = std::ffi::OsString::from("-Xdock:icon=");
                argument.push(icon);
                launch.arg(argument);
            }
        }
    }
    launch
        .arg("-cp")
        .arg(app.join("*"))
        .arg("net.modtale.launcher.LauncherMain");
    launch
}

fn acquire_setup_lock(state: &Path, timeout: Duration) -> Result<File> {
    let lock = OpenOptions::new()
        .create(true)
        .truncate(false)
        .read(true)
        .write(true)
        .open(state.join("runtime.lock"))?;
    let deadline = Instant::now() + timeout;
    loop {
        match lock.try_lock_exclusive() {
            Ok(()) => break,
            Err(e)
                if e.raw_os_error() == fs2::lock_contended_error().raw_os_error()
                    && Instant::now() < deadline =>
            {
                thread::sleep(Duration::from_millis(200))
            }
            Err(e) => return Err(format!("Could not acquire the Java setup lock: {e}").into()),
        }
    }
    Ok(lock)
}
fn install_runtime(state: &Path, cache: &Path, app: &Path, log: &File) -> Result<PathBuf> {
    let _lock = acquire_setup_lock(state, Duration::from_secs(660))?;
    if let Some(java) = find_java(cache, 4).filter(|j| probe(j, app, log)) {
        return Ok(java);
    }
    // Only the lock holder can create download directories; remove debris from killed setup processes.
    for entry in fs::read_dir(state)? {
        let entry = entry?;
        if entry
            .file_name()
            .to_string_lossy()
            .starts_with("runtime-download-")
            && entry.file_type()?.is_dir()
        {
            fs::remove_dir_all(entry.path())?;
        }
    }
    for java in java_candidates(&home()?, cache) {
        if !probe(&java, app, log) {
            continue;
        }
        match adopt_runtime(&java, state, cache, app, log) {
            Ok(java) => return Ok(java),
            Err(error) => {
                let _ = writeln!(&*log, "Could not adopt {}: {error}", java.display());
            }
        }
    }
    if !diagnostic() {
        dialog::message(
            "Modtale Launcher — Java setup",
            "Modtale needs to download Java before it can start. Hytale does not need to be installed.\n\nClick OK to begin. Setup may take several minutes. Modtale will open automatically when it finishes. Future launches can use this Java installation offline.",
            false,
        );
    }
    let agent = http_agent(Duration::from_secs(600));
    let os = if cfg!(windows) {
        "windows"
    } else if cfg!(target_os = "macos") {
        "mac"
    } else {
        "linux"
    };
    let arch = match env::consts::ARCH {
        "x86_64" => "x64",
        "aarch64" => "aarch64",
        other => return Err(format!("Unsupported architecture: {other}").into()),
    };
    let url = format!(
        "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture={arch}&image_type=jre&os={os}&vendor=eclipse"
    );
    let assets: Vec<Asset> =
        serde_json::from_str(&agent.get(&url).call()?.body_mut().read_to_string()?)?;
    let package = &assets
        .first()
        .ok_or("No compatible Java download was found")?
        .binary
        .package;
    validate_package(package)?;
    let staging = tempfile::Builder::new()
        .prefix("runtime-download-")
        .tempdir_in(state)?;
    let archive = staging.path().join("runtime.archive");
    let mut response = agent.get(&package.link).call()?;
    download(response.body_mut().as_reader(), &archive, package)?;
    let extracted = staging.path().join("extracted");
    fs::create_dir(&extracted)?;
    extract(&archive, &extracted)?;
    let java = find_java(&extracted, 4).ok_or("Downloaded runtime has no Java executable")?;
    if !probe(&java, app, log) {
        return Err("Downloaded Java failed the compatibility check".into());
    }
    let relative = java.strip_prefix(&extracted)?.to_owned();
    // Publish only a complete, verified runtime. Never touch Hytale's installation.
    if cache.exists() {
        fs::remove_dir_all(cache)?;
    }
    fs::rename(&extracted, cache)?;
    Ok(cache.join(relative))
}
// A private snapshot avoids holding files open in Hytale's update directory (especially on Windows).
fn adopt_runtime(
    java: &Path,
    state: &Path,
    cache: &Path,
    app: &Path,
    log: &File,
) -> Result<PathBuf> {
    let java = java.canonicalize()?;
    let bin = java.parent().ok_or("Java has no bin directory")?;
    if bin.file_name().is_none_or(|name| name != "bin") {
        return Err("Java is not in a runtime bin directory".into());
    }
    let root = bin.parent().ok_or("Java has no runtime directory")?;
    let staging = tempfile::Builder::new()
        .prefix("runtime-download-")
        .tempdir_in(state)?;
    let runtime = staging.path().join("runtime");
    fs::create_dir(&runtime)?;
    let mut remaining = 2 * MAX_EXTRACTED;
    // Retain runtime files and notices; development-only include/jmods directories are not needed.
    for name in ["bin", "lib", "conf", "legal", "release", "NOTICE"] {
        if root.join(name).exists() {
            copy_runtime_tree(&root.join(name), &runtime.join(name), 32, &mut remaining)?;
        }
    }
    let candidate = runtime.join("bin").join(java_name());
    if !probe(&candidate, app, log) {
        return Err(
            "Copied Java failed validation; the source may have changed during setup".into(),
        );
    }
    if cache.exists() {
        fs::remove_dir_all(cache)?;
    }
    fs::rename(&runtime, cache)?;
    Ok(cache.join("bin").join(java_name()))
}
fn copy_runtime_tree(
    source: &Path,
    destination: &Path,
    depth: usize,
    remaining: &mut u64,
) -> Result<()> {
    if depth == 0 {
        return Err("Runtime contains recursive links".into());
    }
    // Materialize links: the cached runtime must survive removal of the original installation.
    let metadata = fs::metadata(source)?;
    if metadata.is_dir() {
        fs::create_dir(destination)?;
        for entry in fs::read_dir(source)? {
            let entry = entry?;
            copy_runtime_tree(
                &entry.path(),
                &destination.join(entry.file_name()),
                depth - 1,
                remaining,
            )?;
        }
    } else if metadata.is_file() {
        *remaining = remaining
            .checked_sub(metadata.len())
            .ok_or("Installed Java runtime is too large")?;
        fs::copy(source, destination)?;
    } else {
        return Err("Unsupported file in Java runtime".into());
    }
    Ok(())
}

fn validate_package(package: &Package) -> Result<()> {
    if !package
        .link
        .starts_with("https://github.com/adoptium/temurin25-binaries/releases/download/")
        || package.checksum.len() != 64
        || !package.checksum.bytes().all(|b| b.is_ascii_hexdigit())
        || package.size == 0
        || package.size > MAX_ARCHIVE
    {
        return Err("Java provider returned invalid download metadata".into());
    }
    Ok(())
}
fn download(mut source: impl Read, destination: &Path, package: &Package) -> Result<()> {
    let mut out = File::create(destination)?;
    let mut hash = Sha256::new();
    let mut total = 0_u64;
    let mut buffer = [0_u8; 65536];
    loop {
        let n = source.read(&mut buffer)?;
        if n == 0 {
            break;
        }
        total += n as u64;
        if total > package.size {
            return Err("Java download exceeds expected size".into());
        }
        hash.update(&buffer[..n]);
        out.write_all(&buffer[..n])?;
    }
    if total != package.size || format!("{:x}", hash.finalize()) != package.checksum.to_lowercase()
    {
        return Err("Java download is incomplete or failed SHA-256 verification".into());
    }
    out.sync_all()?;
    Ok(())
}
fn extract(archive: &Path, destination: &Path) -> Result<()> {
    #[cfg(windows)]
    {
        extract_zip(archive, destination)
    }
    #[cfg(unix)]
    {
        extract_tar(archive, destination)
    }
}
#[cfg(any(windows, test))]
fn extract_zip(archive: &Path, destination: &Path) -> Result<()> {
    let file = File::open(archive)?;
    let mut total = 0_u64;
    let mut zip = zip::ZipArchive::new(file)?;
    for index in 0..zip.len() {
        let mut entry = zip.by_index(index)?;
        total = total.checked_add(entry.size()).ok_or("Archive too large")?;
        if total > MAX_EXTRACTED {
            return Err("Archive too large".into());
        }
        let path = destination.join(entry.enclosed_name().ok_or("Unsafe archive path")?);
        if entry.is_symlink() {
            return Err("Unexpected link in Java ZIP".into());
        }
        if entry.is_dir() {
            fs::create_dir_all(path)?;
        } else {
            fs::create_dir_all(path.parent().ok_or("Invalid archive path")?)?;
            std::io::copy(&mut entry, &mut File::create(path)?)?;
        }
    }
    Ok(())
}
#[cfg(any(unix, test))]
fn extract_tar(archive: &Path, destination: &Path) -> Result<()> {
    let file = File::open(archive)?;
    let mut total = 0_u64;
    let mut tar = tar::Archive::new(flate2::read::GzDecoder::new(file));
    for entry in tar.entries()? {
        let mut entry = entry?;
        total = total.checked_add(entry.size()).ok_or("Archive too large")?;
        if total > MAX_EXTRACTED {
            return Err("Archive too large".into());
        }
        // tar's unpack_in confines paths and link targets to the staging directory.
        if !entry.unpack_in(destination)? {
            return Err("Unsafe archive path".into());
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    fn package(bytes: &[u8]) -> Package {
        Package {
            link:
                "https://github.com/adoptium/temurin25-binaries/releases/download/test/java.tar.gz"
                    .into(),
            checksum: format!("{:x}", Sha256::digest(bytes)),
            size: bytes.len() as u64,
        }
    }
    #[test]
    fn updates_are_scoped_to_installation_and_packaged_version() {
        let state = Path::new("state");
        let first = update_root(state, Path::new("/apps/launcher"), b"version1");
        assert_eq!(
            first,
            update_root(state, Path::new("/apps/launcher"), b"version1")
        );
        assert_ne!(
            first,
            update_root(state, Path::new("/apps/launcher"), b"version2")
        );
        assert_ne!(
            first,
            update_root(state, Path::new("/apps/other"), b"version1")
        );
    }

    #[test]
    fn active_update_rejects_missing_and_unsafe_paths() {
        let root = tempfile::tempdir().unwrap();
        assert!(active_update(root.path()).is_none());
        for name in [
            "../version-other",
            "version-../../other",
            "/version-absolute",
            "version-missing",
        ] {
            fs::write(root.path().join("active"), name).unwrap();
            assert!(active_update(root.path()).is_none());
        }
        let version = root.path().join("version-123");
        fs::create_dir(&version).unwrap();
        fs::write(root.path().join("active"), "version-123").unwrap();
        assert_eq!(active_update(root.path()).unwrap(), version);
    }

    #[test]
    fn preserves_argument_boundaries_for_native_launch() {
        let config = Config {
            jvm_args: vec!["-Dmodtale.launcherVersion=1.0".into()],
        };
        let mut launch =
            application_command(Path::new("java"), Path::new("app with spaces ü"), &config);
        launch.arg("modtale://install/project?name=space and ü");
        let args: Vec<_> = launch.get_args().collect();
        assert_eq!(args[0], "-Dmodtale.launcherVersion=1.0");
        assert_eq!(
            args[args.len() - 3],
            Path::new("app with spaces ü").join("*").as_os_str()
        );
        assert_eq!(args[args.len() - 2], "net.modtale.launcher.LauncherMain");
        assert_eq!(
            args[args.len() - 1],
            "modtale://install/project?name=space and ü"
        );
        #[cfg(target_os = "macos")]
        assert!(args.contains(&std::ffi::OsStr::new("-Xdock:name=Modtale Launcher")));
    }
    #[test]
    fn verifies_download_before_use() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("download");
        let data = b"runtime bytes";
        download(&data[..], &path, &package(data)).unwrap();
        assert_eq!(fs::read(&path).unwrap(), data);
        assert!(download(&data[..4], &path, &package(data)).is_err());
        assert!(download(&b"runtime bytex"[..], &path, &package(data)).is_err());
        assert!(download(&b"runtime bytes extra"[..], &path, &package(data)).is_err());
    }
    #[test]
    fn validates_provider_metadata() {
        let mut p = package(b"java");
        validate_package(&p).unwrap();
        p.link = "https://github.com.evil.example/adoptium/java".into();
        assert!(validate_package(&p).is_err());
        p = package(b"java");
        p.size = MAX_ARCHIVE + 1;
        assert!(validate_package(&p).is_err());
        p = package(b"java");
        p.checksum = "invalid".into();
        assert!(validate_package(&p).is_err());
    }
    #[test]
    fn finds_runtime_in_macos_archive_and_handles_absence() {
        let dir = tempfile::tempdir().unwrap();
        assert!(find_java(dir.path(), 4).is_none());
        let bin = dir.path().join("jdk-25/Contents/Home/bin");
        fs::create_dir_all(&bin).unwrap();
        fs::write(bin.join(java_name()), "java").unwrap();
        assert_eq!(find_java(dir.path(), 4).unwrap(), bin.join(java_name()));
    }
    #[test]
    fn concurrent_setup_is_exclusive_and_recovers_after_release() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("lock");
        let first = File::create(&path).unwrap();
        let second = OpenOptions::new().write(true).open(&path).unwrap();
        first.try_lock_exclusive().unwrap();
        assert!(second.try_lock_exclusive().is_err());
        drop(first);
        second.try_lock_exclusive().unwrap();
    }
    #[test]
    fn setup_waits_for_another_process_and_times_out_cleanly() {
        let dir = tempfile::tempdir().unwrap();
        let first = acquire_setup_lock(dir.path(), Duration::from_secs(1)).unwrap();
        assert!(acquire_setup_lock(dir.path(), Duration::from_millis(50)).is_err());
        thread::scope(|scope| {
            let waiter =
                scope.spawn(|| acquire_setup_lock(dir.path(), Duration::from_secs(2)).is_ok());
            thread::sleep(Duration::from_millis(100));
            drop(first);
            assert!(waiter.join().unwrap());
        });
    }
    #[cfg(unix)]
    #[test]
    fn cached_copy_survives_source_removal_and_preserves_executability() {
        use std::os::unix::fs::{PermissionsExt, symlink};
        let dir = tempfile::tempdir().unwrap();
        let original = dir.path().join("original");
        fs::create_dir_all(original.join("bin")).unwrap();
        fs::write(original.join("bin/java"), "#!/bin/sh\nexit 0\n").unwrap();
        fs::set_permissions(original.join("bin/java"), fs::Permissions::from_mode(0o755)).unwrap();
        fs::create_dir(original.join("lib")).unwrap();
        fs::write(original.join("lib/data"), "runtime bytes").unwrap();
        symlink("data", original.join("lib/link")).unwrap();
        let cache = dir.path().join("cache");
        let log = File::create(dir.path().join("log")).unwrap();
        let java = adopt_runtime(
            &original.join("bin/java"),
            dir.path(),
            &cache,
            dir.path(),
            &log,
        )
        .unwrap();
        fs::remove_dir_all(original).unwrap();
        assert!(probe(&java, dir.path(), &log));
        assert_eq!(
            fs::read_to_string(cache.join("lib/link")).unwrap(),
            "runtime bytes"
        );
        assert!(
            !fs::symlink_metadata(cache.join("lib/link"))
                .unwrap()
                .is_symlink()
        );
    }
    #[test]
    fn rejects_zip_traversal_without_writing_outside_staging() {
        use zip::{ZipWriter, write::SimpleFileOptions};
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("java.zip");
        let mut zip = ZipWriter::new(File::create(&path).unwrap());
        zip.start_file("../escaped", SimpleFileOptions::default())
            .unwrap();
        zip.write_all(b"bad").unwrap();
        zip.finish().unwrap();
        let staging = dir.path().join("staging");
        fs::create_dir(&staging).unwrap();
        assert!(extract_zip(&path, &staging).is_err());
        assert!(!dir.path().join("escaped").exists());
    }
    #[test]
    fn extracts_verified_tar_runtime() {
        let dir = tempfile::tempdir().unwrap();
        let archive = dir.path().join("java.tar.gz");
        let mut tar = tar::Builder::new(flate2::write::GzEncoder::new(
            File::create(&archive).unwrap(),
            flate2::Compression::fast(),
        ));
        let mut header = tar::Header::new_gnu();
        header.set_size(4);
        header.set_mode(0o755);
        header.set_cksum();
        tar.append_data(
            &mut header,
            format!("jdk/bin/{}", java_name()),
            &b"java"[..],
        )
        .unwrap();
        tar.into_inner().unwrap().finish().unwrap();
        let staging = dir.path().join("staging");
        fs::create_dir(&staging).unwrap();
        extract_tar(&archive, &staging).unwrap();
        assert_eq!(fs::read(find_java(&staging, 4).unwrap()).unwrap(), b"java");
    }
    #[cfg(unix)]
    #[test]
    fn skips_broken_java_and_kills_hung_probe() {
        use std::os::unix::fs::PermissionsExt;
        let dir = tempfile::tempdir().unwrap();
        let java = dir.path().join("java");
        let log = File::create(dir.path().join("log")).unwrap();
        fs::write(&java, "#!/bin/sh\nexit 1\n").unwrap();
        fs::set_permissions(&java, fs::Permissions::from_mode(0o755)).unwrap();
        assert!(!probe(&java, dir.path(), &log));
        fs::write(&java, "#!/bin/sh\nexec sleep 30\n").unwrap();
        let start = Instant::now();
        assert!(!probe_with_timeout(
            &java,
            dir.path(),
            &log,
            Duration::from_millis(100)
        ));
        assert!(start.elapsed() < Duration::from_secs(2));
        fs::write(&java, "#!/bin/sh\nexit 0\n").unwrap();
        assert!(probe(&java, dir.path(), &log));
    }
    #[test]
    #[ignore = "Downloads and validates a real Temurin runtime; requires MODTALE_TEST_APP"]
    fn real_download_works_without_hytale() {
        let app = PathBuf::from(env::var_os("MODTALE_TEST_APP").expect("MODTALE_TEST_APP"));
        let dir = tempfile::tempdir().unwrap();
        let agent = http_agent(Duration::from_secs(180));
        let os = if cfg!(windows) {
            "windows"
        } else if cfg!(target_os = "macos") {
            "mac"
        } else {
            "linux"
        };
        let arch = if cfg!(target_arch = "aarch64") {
            "aarch64"
        } else {
            "x64"
        };
        let url = format!(
            "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture={arch}&image_type=jre&os={os}&vendor=eclipse"
        );
        let assets: Vec<Asset> = serde_json::from_str(
            &agent
                .get(&url)
                .call()
                .unwrap()
                .body_mut()
                .read_to_string()
                .unwrap(),
        )
        .unwrap();
        let package = &assets[0].binary.package;
        validate_package(package).unwrap();
        let archive = dir.path().join("java.archive");
        download(
            agent
                .get(&package.link)
                .call()
                .unwrap()
                .body_mut()
                .as_reader(),
            &archive,
            package,
        )
        .unwrap();
        let runtime = dir.path().join("runtime");
        fs::create_dir(&runtime).unwrap();
        extract(&archive, &runtime).unwrap();
        let java = find_java(&runtime, 4).unwrap();
        let log = File::create(dir.path().join("probe.log")).unwrap();
        assert!(
            probe(&java, &app, &log),
            "{}",
            fs::read_to_string(dir.path().join("probe.log")).unwrap()
        );
        // The second check uses only the extracted runtime, with no provider request.
        assert!(probe(&find_java(&runtime, 4).unwrap(), &app, &log));
    }
}
