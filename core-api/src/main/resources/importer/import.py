import sys
import os
import json
import re
import shutil
import subprocess
import zipfile
import base64

REPO_PATTERN = re.compile(r"^[\w.-]+/[\w.-]+$")

MAX_ENTRIES = 10_000
MAX_TOTAL_UNCOMPRESSED = 1 * 1024 ** 3  # 1 GiB
MAX_SINGLE_FILE = 200 * 1024 ** 2  # 200 MB
MAX_RATIO = 200


def fail(msg):
    print(msg, file=sys.stderr)
    sys.exit(1)


def describe_path(path):
    if not os.path.exists(path):
        return f"path does not exist: {path}"
    try:
        st = os.stat(path)
        with open(path, "rb") as f:
            head = f.read(8)
        return f"path={path} size={st.st_size} mode={oct(st.st_mode)} uid={st.st_uid} gid={st.st_gid} head={head!r}"
    except OSError as e:
        return f"path={path} exists but is unreadable: {e}"


def run_zip(zip_path, out_dir):
    if not zipfile.is_zipfile(zip_path):
        fail(f"Not a valid zip file ({describe_path(zip_path)})")

    with zipfile.ZipFile(zip_path) as zf:
        infos = zf.infolist()
        if len(infos) > MAX_ENTRIES:
            fail(f"Too many entries in zip ({len(infos)})")

        total = 0
        for info in infos:
            total += info.file_size
            if info.file_size > MAX_SINGLE_FILE:
                fail(f"Entry too large: {info.filename}")
            if info.compress_size > 0:
                ratio = info.file_size / info.compress_size
                if ratio > MAX_RATIO:
                    fail(f"Suspicious compression ratio on {info.filename}: {ratio:.0f}x")
        if total > MAX_TOTAL_UNCOMPRESSED:
            fail(f"Uncompressed size too large ({total} bytes)")

        extract_dir = os.path.join(out_dir, "context")
        os.makedirs(extract_dir, exist_ok=True)

        written = 0
        for info in infos:
            if info.is_dir():
                continue
            # zipfile.extract() normalizes/strips unsafe path components (zip-slip safe since Python 3.6+)
            target = zf.extract(info, path=extract_dir)
            written += os.path.getsize(target)
            if written > MAX_TOTAL_UNCOMPRESSED:
                fail("Uncompressed size exceeded during extraction")

    scan_and_write_manifest(extract_dir, out_dir)


def scan_and_write_manifest(extract_dir, out_dir):
    entries = os.listdir(extract_dir)
    if "Dockerfile" in entries:
        manifest = {"kind": "dockerfile", "path": "Dockerfile"}
    elif "docker-compose.yml" in entries or "docker-compose.yaml" in entries:
        manifest = {"kind": "compose"}
    else:
        manifest = {"kind": "none"}

    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, "manifest.json"), "w") as f:
        json.dump(manifest, f)


def run_git(repository, ref, out_dir):
    if not REPO_PATTERN.match(repository):
        fail(f"Invalid repository: {repository}")
    token = os.environ.get("GIT_TOKEN")
    if not token:
        fail("Missing GIT_TOKEN")

    extract_dir = os.path.join(out_dir, "context")
    url = f"https://github.com/{repository}.git"
    credentials = base64.b64encode(f"x-access-token:{token}".encode()).decode()
    header = f"AUTHORIZATION: basic {credentials}"
    result = subprocess.run(
        ["git", "-c", f"http.extraHeader={header}", "clone", "--depth", "1", "--branch", ref, url, extract_dir],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        fail(f"git clone failed: {result.stderr.strip()}")

    shutil.rmtree(os.path.join(extract_dir, ".git"), ignore_errors=True)
    scan_and_write_manifest(extract_dir, out_dir)


def main():
    if len(sys.argv) < 2:
        fail("Usage: import.py <mode> ...")
    mode = sys.argv[1]
    if mode == "zip":
        if len(sys.argv) != 4:
            fail("Usage: import.py zip <input> <outDir>")
        run_zip(sys.argv[2], sys.argv[3])
    elif mode == "git":
        if len(sys.argv) != 5:
            fail("Usage: import.py git <repository> <ref> <outDir>")
        run_git(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        fail(f"Unknown mode: {mode}")


if __name__ == "__main__":
    main()
