#!/usr/bin/env python3
"""Build the pinned OpenJFX Linux backend and retain the published Java classes."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

REVISION = "96af634d010c917a6a4adf33da5508576b9bdb18"
LIBRARIES = ("libglass.so", "libglassgtk3.so", "libprism_es2.so")


def run(*args, cwd=None, env=None):
    subprocess.run([str(arg) for arg in args], cwd=cwd, env=env, check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-jar", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--work", type=Path, required=True)
    parser.add_argument("--patch", type=Path, required=True)
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--gradle", type=Path, required=True)
    parser.add_argument("--native-dir", type=Path)
    args = parser.parse_args()
    work = args.work.resolve()
    work.mkdir(parents=True, exist_ok=True)
    native_dir = args.native_dir
    if native_dir is None:
        source = work / "source"
        if not (source / ".git").exists():
            source.mkdir(parents=True, exist_ok=True)
            run("git", "init", source)
        if subprocess.run(["git", "cat-file", "-e", REVISION + "^{commit}"], cwd=source,
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
            run("git", "fetch", "--depth", "1", "https://github.com/openjdk/jfx26u.git", REVISION, cwd=source)
        run("git", "checkout", "--detach", "--force", REVISION, cwd=source)
        run("git", "clean", "-fd", cwd=source)
        run("git", "apply", "--check", args.patch.resolve(), cwd=source)
        run("git", "apply", args.patch.resolve(), cwd=source)
        env = dict(os.environ, JAVA_HOME=str(args.java_home.resolve()))
        run(args.gradle.resolve(), "--no-daemon", ":graphics:compileFullJava", ":graphics:nativeGlass",
            ":graphics:nativePrismES2", "-PCONF=Release", "-PCOMPILE_MEDIA=false", "-PCOMPILE_WEBKIT=false",
            cwd=source, env=env)
        native_dir = work / "natives"
        native_dir.mkdir(exist_ok=True)
        libraries = source / "modules/javafx.graphics/build/libs"
        for name in LIBRARIES:
            component = "prismES2" if name == "libprism_es2.so" else "glass"
            shutil.copy2(libraries / component / "linux" / name, native_dir / name)
            run("strip", "--strip-unneeded", native_dir / name)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_suffix(".tmp")
    with zipfile.ZipFile(args.base_jar) as source, zipfile.ZipFile(
        temporary, "w", zipfile.ZIP_DEFLATED, compresslevel=9
    ) as output:
        for entry in source.infolist():
            if entry.filename not in LIBRARIES and entry.filename != "META-INF/modtale-native-wayland":
                output.writestr(entry, source.read(entry.filename))
        for name in LIBRARIES:
            output.write(native_dir / name, name)
        output.writestr("META-INF/modtale-native-wayland", REVISION + "\n")
    temporary.replace(args.output)


if __name__ == "__main__":
    main()
