"""Exercise the packaged bootstrap with isolated runtime locations."""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

executable = Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory(prefix="modtale bootstrap ü ") as directory:
    home = Path(directory)
    environment = os.environ.copy()
    for key in ("MODTALE_JAVA", "MODTALE_HYTALE_HOME", "JAVA_HOME", "PATH", "APPDATA", "LOCALAPPDATA", "XDG_DATA_HOME", "ProgramFiles"):
        environment.pop(key, None)
    environment.update(HOME=str(home), USERPROFILE=str(home), PATH="", XDG_DATA_HOME=str(home))
    offline = dict(HTTPS_PROXY="http://127.0.0.1:1", HTTP_PROXY="http://127.0.0.1:1", ALL_PROXY="http://127.0.0.1:1", NO_PROXY="")
    hytale = None
    if len(sys.argv) > 2:
        if "--hytale" in sys.argv:
            if sys.platform == "win32":
                environment["APPDATA"] = str(home / "AppData" / "Roaming")
                hytale = Path(environment["APPDATA"]) / "Hytale"
            elif sys.platform == "darwin":
                hytale = home / "Library" / "Application Support" / "Hytale"
            else:
                hytale = home / ".var" / "app" / "com.hypixel.HytaleLauncher" / "data" / "Hytale"
            runtime = hytale / "install" / "pre-release" / "package" / "jre" / "latest"
            if sys.platform == "darwin":
                runtime = runtime / "Contents" / "Home"
            shutil.copytree(sys.argv[2], runtime, symlinks=False, ignore_dangling_symlinks=True)
        else:
            environment["JAVA_HOME"] = sys.argv[2]
        # Discovery and adoption must succeed without downloading a substitute runtime.
        environment.update(offline)
    subprocess.run([executable, "--modtale-bootstrap-check"], env=environment, check=True, timeout=240)
    cache = home / ".modtale" / "launcher" / "runtime-25"
    assert cache.is_dir(), "A complete private runtime must be published"
    assert not list(cache.parent.glob("runtime-download-*")), "Setup debris must be removed"
    if hytale:
        shutil.rmtree(hytale)
    environment.pop("JAVA_HOME", None)
    environment.update(offline)
    subprocess.run([executable, "--modtale-bootstrap-check"], env=environment, check=True, timeout=45)
    print("Packaged runtime setup and offline cache reuse passed")
