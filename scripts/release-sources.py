"""Bundle one committed Accord tree and its pinned third-party sources, without local files."""
import argparse
import concurrent.futures
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import tarfile
import urllib.request
from urllib.parse import urlsplit


def checked_path(value):
    path = PurePosixPath(value)
    if path.is_absolute() or ".." in path.parts or "\\" in value or ":" in value:
        raise ValueError("Unsafe archive path")
    return path


def verified(data, expected):
    if hashlib.sha256(data).hexdigest() != expected:
        raise ValueError("Source checksum does not match the committed inventory")
    return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--dependency-sources", type=Path)
    parser.add_argument("--extra-sources", type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{40}", args.commit):
        parser.error("--commit must be a full Git commit SHA")
    repo = Path(__file__).resolve().parent.parent

    def git(*arguments):
        return subprocess.check_output(["git", "-C", str(repo), *arguments])

    inventory = json.loads(git("show", args.commit + ":app/src/main/assets/licenses/runtime-dependencies.json"))
    modules = {a["coordinate"]: a["sources"] for a in inventory["artifacts"]}

    def dependency(item):
        coordinate, source = item
        relative = checked_path(source["archive"])
        if args.dependency_sources:
            data = (args.dependency_sources / Path(str(relative))).read_bytes()
        else:
            group, name, version = coordinate.split(":")
            location = f"{group.replace('.', '/')}/{name}/{version}/{name}-{version}-sources.jar"
            bases = ["https://dl.google.com/dl/android/maven2/", "https://repo.maven.apache.org/maven2/"]
            if not group.startswith("androidx."):
                bases.reverse()
            data = None
            for base in bases:
                try:
                    with urllib.request.urlopen(base + location, timeout=30) as response:
                        data = response.read()
                    break
                except OSError:
                    continue
            if data is None:
                raise RuntimeError("Source archive unavailable: " + coordinate)
        return "dependencies/" + str(relative), verified(data, source["sha256"])

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        dependencies = list(pool.map(dependency, sorted(modules.items())))
    native = []
    for kind, source in [("native", s) for s in inventory["nativeSources"]] + [("resources", s) for s in inventory.get("resourceArtifacts", [])]:
        relative = checked_path(source["archive"])
        if args.extra_sources:
            data = (args.extra_sources / Path(str(relative))).read_bytes()
        else:
            url = source["url"]
            if urlsplit(url).scheme != "https" or urlsplit(url).hostname not in {"android.googlesource.com", "www.sqlite.org", "repo.maven.apache.org"}:
                raise ValueError("Unrecognized native-source host")
            with urllib.request.urlopen(url, timeout=30) as response:
                data = response.read()
        native.append(("dependencies/" + kind + "/" + str(relative), verified(data, source["sha256"])))

    committed = git("archive", "--format=tar", "--prefix=accord/", args.commit)
    building = (
        "Accord source bundle\n\n"
        f"Commit: {args.commit}\n"
        "accord/ contains exactly the committed project, without .git or ignored local files.\n"
        "Build tools: JDK 21, Android SDK 37, bundled Gradle wrapper.\n"
        "From accord/: gradlew.bat :app:assembleDebug (Windows), or ./gradlew :app:assembleDebug.\n"
        "The optional local poc/pocpc benches are not needed.\n"
        "Release builds require your own signing key and service settings; see accord/README.md.\n"
        "Private signing keys, passwords, user data and local.properties are not supplied.\n\n"
        "dependencies/ contains the matching unmodified Maven source JARs, which can be opened as ZIPs.\n"
        "dependencies/native/ supplements the JVM sources with AndroidX native modules and SQLite amalgamation.\n"
        "dependencies/resources/ preserves the original PDFBox AAR, including its font and mapping data.\n"
        "Versions, SHA-256 checksums, upstream revisions and licenses: "
        "accord/app/src/main/assets/licenses/runtime-dependencies.json.\n"
        "Dependency sources retain their own licenses. Accord code is GNU GPL version 3 only.\n"
    ).encode()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + ".tmp")
    with tarfile.open(temporary, "w:gz", format=tarfile.PAX_FORMAT) as target:
        with tarfile.open(fileobj=io.BytesIO(committed), mode="r:") as project:
            for member in project.getmembers():
                checked_path(member.name)
                target.addfile(member, project.extractfile(member) if member.isfile() else None)
        for name, data in [("BUILDING.txt", building), *dependencies, *native]:
            member = tarfile.TarInfo(name)
            member.size, member.mode, member.mtime = len(data), 0o644, 0
            target.addfile(member, io.BytesIO(data))
    temporary.replace(args.output)
    print(f"Source bundle: {args.output} ({args.output.stat().st_size} bytes)")
    print("SHA-256:", hashlib.sha256(args.output.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
