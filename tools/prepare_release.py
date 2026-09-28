#!/usr/bin/env python3
"""Validate and stage the three signed release APKs without uploading them."""

import argparse
import hashlib
import json
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path


APK_DIR = Path("app/build/outputs/apk/release")
METADATA = APK_DIR / "output-metadata.json"
EXPECTED = {
    "app-arm64-v8a-release.apk": ({"arm64-v8a"}, "arm64-v8a"),
    "app-armeabi-v7a-release.apk": ({"armeabi-v7a"}, "armeabi-v7a"),
    "app-universal-release.apk": ({"arm64-v8a", "armeabi-v7a"}, "universal-arm"),
}


class ReleaseError(Exception):
    pass


def read_version(path):
    values = {}
    try:
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                values[key.strip()] = value.strip()
    except OSError as exc:
        raise ReleaseError("cannot read version.properties") from exc
    name = values.get("VERSION_NAME", "")
    code = values.get("VERSION_CODE", "")
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?", name) or not code.isdigit():
        raise ReleaseError("version.properties has invalid VERSION_NAME or VERSION_CODE")
    return code, name


def check_metadata(path, version_code, version_name):
    try:
        metadata = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ReleaseError("release output-metadata.json is missing or invalid") from exc
    elements = metadata.get("elements") if isinstance(metadata, dict) else None
    if not isinstance(elements, list) or len(elements) != 3:
        raise ReleaseError("release metadata must describe exactly three APKs")
    described = set()
    for element in elements:
        if not isinstance(element, dict):
            raise ReleaseError("invalid APK entry in release metadata")
        filename = element.get("outputFile")
        if not isinstance(filename, str) or Path(filename).name != filename:
            raise ReleaseError("invalid APK filename in release metadata")
        described.add(filename)
        if str(element.get("versionCode", "")) != version_code or str(element.get("versionName", "")) != version_name:
            raise ReleaseError("release APK version values do not match version.properties")
    if described != set(EXPECTED):
        raise ReleaseError("release metadata does not list exactly the three expected APKs")


def apk_abis(path):
    try:
        with zipfile.ZipFile(path) as archive:
            return {match.group(1) for name in archive.namelist()
                    if (match := re.match(r"^lib/([^/]+)/[^/]+\.so$", name))}
    except (OSError, zipfile.BadZipFile) as exc:
        raise ReleaseError("an APK is unreadable or is not a valid ZIP archive") from exc


def certificate_digest(apksigner, apk):
    try:
        result = subprocess.run(
            [apksigner, "verify", "--print-certs", str(apk)],
            check=False, capture_output=True, text=True,
        )
    except OSError as exc:
        raise ReleaseError("could not run apksigner") from exc
    if result.returncode != 0:
        raise ReleaseError("apksigner verification failed for an APK")
    matches = re.findall(
        r"certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", result.stdout + "\n" + result.stderr,
        flags=re.IGNORECASE,
    )
    if not matches:
        raise ReleaseError("apksigner did not report a SHA-256 certificate digest")
    normalized = {re.sub(r"[^0-9a-f]", "", digest.lower()) for digest in matches}
    if len(normalized) != 1 or any(len(digest) != 64 for digest in normalized):
        raise ReleaseError("an APK has ambiguous or invalid signer certificate digests")
    return normalized.pop()


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", required=True, type=Path, help="empty staging directory (preferably outside Git repositories)")
    parser.add_argument("--apksigner", required=True, help="path or command name for apksigner")
    args = parser.parse_args()

    try:
        code, name = read_version(Path("version.properties"))
        output = args.output_dir.expanduser().resolve()
        if output.exists() and (not output.is_dir() or any(output.iterdir())):
            raise ReleaseError("output directory must be empty; refusing to overwrite artifacts")
        check_metadata(METADATA, code, name)
        verified = {}
        fingerprints = set()
        for source_name, (abis, suffix) in EXPECTED.items():
            source = APK_DIR / source_name
            if not source.is_file():
                raise ReleaseError("a required release APK is missing")
            if apk_abis(source) != abis:
                raise ReleaseError("an APK contains an unexpected native ABI set")
            fingerprint = certificate_digest(args.apksigner, source)
            fingerprints.add(fingerprint)
            verified[source_name] = (source, f"ytrd-android-v{name}-{suffix}.apk")
        if len(fingerprints) != 1:
            raise ReleaseError("APK signing certificate fingerprints do not match")

        output.mkdir(parents=True, exist_ok=True)
        staged = []
        try:
            for source, filename in verified.values():
                destination = output / filename
                shutil.copy2(source, destination)
                staged.append(destination)
            sums = [(sha256(path), path.name) for path in staged]
            (output / "SHA256SUMS").write_text(
                "".join(f"{digest}  {filename}\n" for digest, filename in sums), encoding="ascii"
            )
        except Exception:
            for path in staged:
                path.unlink(missing_ok=True)
            raise
        print(f"Подготовлен релиз {name} (versionCode {code}):")
        for path in staged:
            print(f"  {path.name} — {path.stat().st_size} байт")
        print("  SHA256SUMS")
        print("Проверка подписи и ABI пройдена для всех APK.")
        return 0
    except (ReleaseError, OSError) as exc:
        print(f"Ошибка подготовки релиза: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
