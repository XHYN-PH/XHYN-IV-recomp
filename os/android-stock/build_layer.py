#!/usr/bin/env python3
"""Build the Android layer against a pinned stock APK; no native compilation."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

from preflight import APP_ID, APPLICATION, BASE_SHA256, VERSION_CODE, VERSION_NAME, file_digest, signature_entry, validate_base, verify_candidate

ROOT = Path(__file__).resolve().parent
ANDROID = "{http://schemas.android.com/apk/res/android}"
ET.register_namespace("android", ANDROID[1:-1])


def run(args: list[str | Path], log: Path):
    with log.open("ab") as output:
        subprocess.run([str(value) for value in args], stdout=output, stderr=subprocess.STDOUT, check=True)


def manifest(path: Path):
    tree = ET.parse(path)
    root = tree.getroot()
    root.set("package", APP_ID)
    app = root.find("application")
    assert app is not None and not app.get(ANDROID + "name"), "Unexpected stock Application"
    app.set(ANDROID + "name", APPLICATION)
    app.set(ANDROID + "label", "XHYN IV")
    app.set(ANDROID + "debuggable", "false")
    for name, process in (("com.xhyn.stock.PanelActivity", None), ("com.xhyn.stock.SessionActivity", ":game")):
        attrs = {ANDROID + "name": name, ANDROID + "exported": "false", ANDROID + "screenOrientation": "sensorLandscape", ANDROID + "theme": "@style/LauncherTheme", ANDROID + "configChanges": "keyboard|keyboardHidden|navigation|orientation|screenLayout|uiMode|screenSize|smallestScreenSize"}
        if process:
            attrs[ANDROID + "process"] = process
            attrs[ANDROID + "launchMode"] = "singleTop"
        ET.SubElement(app, "activity", attrs)
    ET.SubElement(app, "provider", {ANDROID + "name": "com.xhyn.stock.LayerFileProvider", ANDROID + "authorities": APP_ID + ".layer.files", ANDROID + "exported": "false", ANDROID + "grantUriPermissions": "true"})
    # Opaque panel Activities retain stock SDL's pause/resume lifecycle.
    tree.write(path, encoding="utf-8", xml_declaration=True)


def build(base: Path, apktool: Path, sdk: Path, out: Path):
    validate_base(base)
    out.mkdir(parents=True, exist_ok=True)
    work = out / "work"
    if work.exists():
        shutil.rmtree(work)
    work.mkdir()
    log = out / "build.log"
    log.write_text("")
    java = Path(os.environ.get("JAVA_HOME", "/usr")) / "bin"
    android = sdk / "platforms/android-35/android.jar"
    buildtools = sdk / "build-tools/35.0.0"
    sources = sorted((ROOT / "layer/src/main/java").rglob("*.java"))
    classes, dex = work / "classes", work / "dex"
    classes.mkdir()
    dex.mkdir()
    run([java / "javac", "--release", "17", "-classpath", android, "-d", classes, *sources], log)
    jar = work / "layer.jar"
    run([java / "jar", "--create", "--file", jar, "-C", classes, "."], log)
    run([buildtools / "d8", "--release", "--min-api", "29", "--lib", android, "--output", dex, jar], log)
    if sorted(p.name for p in dex.iterdir()) != ["classes.dex"]:
        raise ValueError("Expected a single layer DEX")
    decoded = work / "stock"
    run([java / "java", "-jar", apktool, "d", "--no-src", "-f", "-o", decoded, base], log)
    manifest(decoded / "AndroidManifest.xml")
    config = decoded / "apktool.yml"
    text = config.read_text().replace("versionCode: 442", f"versionCode: {VERSION_CODE}").replace("versionName: 0.9.24-renderer-v442", f"versionName: {VERSION_NAME}")
    config.write_text(text)
    # Only need the compiled manifest. Original stock payload is copied from the base below.
    for name in ("lib", "assets", "original", "unknown"):
        if (decoded / name).is_dir():
            shutil.rmtree(decoded / name)
    (decoded / "classes.dex").unlink(missing_ok=True)
    container = work / "manifest.apk"
    run([java / "java", "-jar", apktool, "b", "-f", "-o", container, decoded], log)
    source_hash = hashlib.sha256()
    for path in sources + [ROOT / "build_layer.py", ROOT / "preflight.py"]:
        source_hash.update(path.relative_to(ROOT).as_posix().encode() + b"\0" + path.read_bytes())
    provenance = {"base_sha256": BASE_SHA256, "source_sha256": source_hash.hexdigest(), "version_code": VERSION_CODE, "version_name": VERSION_NAME, "application_id": APP_ID, "apktool_sha256": file_digest(apktool), "native_libraries": "all 12 unchanged", "original_dex": "unchanged", "gameplay_validation": "requires Android device"}
    unsigned = work / "unaligned.apk"
    with zipfile.ZipFile(base) as original, zipfile.ZipFile(container) as compiled, zipfile.ZipFile(unsigned, "w") as packaged:
        for entry in original.infolist():
            if signature_entry(entry.filename):
                continue
            content = compiled.read("AndroidManifest.xml") if entry.filename == "AndroidManifest.xml" else original.read(entry)
            packaged.writestr(entry, content, compresslevel=9 if entry.compress_type == zipfile.ZIP_DEFLATED else None)
        packaged.writestr("classes2.dex", (dex / "classes.dex").read_bytes(), compress_type=zipfile.ZIP_STORED)
        packaged.writestr("assets/xhyn-layer/build.json", json.dumps(provenance, indent=2) + "\n", compress_type=zipfile.ZIP_DEFLATED)
    result = out / f"XHYN-IV-{VERSION_NAME}-unsigned.apk"
    run([buildtools / "zipalign", "-P", "16", "-f", "4", unsigned, result], log)
    run([buildtools / "zipalign", "-c", "-P", "16", "4", result], log)
    report = verify_candidate(base, result)
    report["provenance"] = provenance
    (out / "verification.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"apk": str(result), "bytes": result.stat().st_size, "sha256": file_digest(result), "report": str(out / "verification.json")}, indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True)
    parser.add_argument("--apktool", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    build(*(p.resolve() for p in (args.base, args.apktool, args.sdk, args.out)))


if __name__ == "__main__":
    main()
