"""Exercise the packaged bootstrap with isolated runtime locations."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

executable = Path(sys.argv[1]).resolve()
with tempfile.TemporaryDirectory(prefix="modtale-bootstrap-") as directory:
    home = Path(directory)
    environment = os.environ.copy()
    for key in ("MODTALE_JAVA", "MODTALE_HYTALE_HOME", "JAVA_HOME", "PATH", "APPDATA", "LOCALAPPDATA", "XDG_DATA_HOME"):
        environment.pop(key, None)
    environment.update(HOME=str(home), USERPROFILE=str(home), PATH="", XDG_DATA_HOME=str(home))
    if len(sys.argv) > 2:
        # Use a supplied installation; the second run must work after discovery is removed.
        environment["JAVA_HOME"] = sys.argv[2]
    subprocess.run([executable, "--modtale-bootstrap-check"], env=environment, check=True, timeout=240)
    cache = home / ".modtale" / "launcher" / "runtime-25"
    assert cache.is_dir(), "A complete private runtime must be published"
    assert not list(cache.parent.glob("runtime-download-*")), "Setup debris must be removed"
    environment.pop("JAVA_HOME", None)
    # A nonworking proxy proves the cached launch does not need network access.
    environment.update(HTTPS_PROXY="http://127.0.0.1:1", HTTP_PROXY="http://127.0.0.1:1", ALL_PROXY="http://127.0.0.1:1")
    subprocess.run([executable, "--modtale-bootstrap-check"], env=environment, check=True, timeout=45)
    print("Packaged runtime setup and offline cache reuse passed")
