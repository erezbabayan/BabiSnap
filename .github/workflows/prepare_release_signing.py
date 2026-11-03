#!/usr/bin/env python3
"""Build voicecal-keystore.properties from GitHub Actions secrets.

Reads only SNAP_REGISTRY_CLIENT_KEY and ANDROID_KEYSTORE_* names. Never reads
SNAP_REGISTRY_ADMIN_KEY. Prints set/empty for each name and never prints values.
"""
import base64
import os
import pathlib
import re
import subprocess
import sys

NAMES = [
    "SNAP_REGISTRY_CLIENT_KEY",
    "ANDROID_KEYSTORE_BASE64",
    "ANDROID_KEYSTORE_B64",
    "ANDROID_KEYSTORE_FILE",
    "ANDROID_KEYSTORE_JKS_BASE64",
    "ANDROID_KEYSTORE_P12_BASE64",
    "ANDROID_KEYSTORE_PASSWORD",
    "ANDROID_KEYSTORE_STORE_PASSWORD",
    "ANDROID_KEYSTORE_KEY_PASSWORD",
    "ANDROID_KEYSTORE_ALIAS",
    "ANDROID_KEYSTORE_KEY_ALIAS",
    "ANDROID_KEYSTORE_PROPERTIES",
    "ANDROID_KEYSTORE_PROPS",
    "ANDROID_KEYSTORE_PROPS_BASE64",
]
KEYSTORE_B64_NAMES = [
    "ANDROID_KEYSTORE_BASE64",
    "ANDROID_KEYSTORE_B64",
    "ANDROID_KEYSTORE_FILE",
    "ANDROID_KEYSTORE_JKS_BASE64",
    "ANDROID_KEYSTORE_P12_BASE64",
]
PROPS_NAMES = [
    "ANDROID_KEYSTORE_PROPERTIES",
    "ANDROID_KEYSTORE_PROPS",
    "ANDROID_KEYSTORE_PROPS_BASE64",
]


def val(name):
    return os.environ.get(name, "") or ""


def scrub(text, secrets):
    out = text
    for secret in secrets:
        if secret:
            out = out.replace(secret, "***")
    return out


def unescape_prop(raw):
    out = []
    i = 0
    while i < len(raw):
        ch = raw[i]
        if ch != "\\":
            out.append(ch)
            i += 1
            continue
        if i + 1 >= len(raw):
            break
        n = raw[i + 1]
        mapping = {"n": "\n", "r": "\r", "t": "\t", "\\": "\\"}
        if n in mapping:
            out.append(mapping[n])
            i += 2
        elif n == "u" and i + 5 < len(raw):
            out.append(chr(int(raw[i + 2 : i + 6], 16)))
            i += 6
        else:
            out.append(n)
            i += 2
    return "".join(out)


def escape_prop(text):
    out = []
    for ch in text:
        o = ord(ch)
        if ch == "\\":
            out.append("\\\\")
        elif ch == "\n":
            out.append("\\n")
        elif ch == "\r":
            out.append("\\r")
        elif ch == "\t":
            out.append("\\t")
        elif o < 0x20 or o > 0x7E:
            out.append("\\u%04x" % o)
        else:
            out.append(ch)
    return "".join(out)


def parse_props(text):
    props = {}
    for line in text.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or stripped.startswith("!"):
            continue
        if "=" in stripped:
            key, raw = stripped.split("=", 1)
        elif ":" in stripped:
            key, raw = stripped.split(":", 1)
        else:
            continue
        props[key.strip()] = unescape_prop(raw.strip())
    return props


def looks_like_props(text):
    return "storeFile" in text or "storePassword" in text or "keyAlias" in text


def decode_b64(data):
    raw = data.strip()
    if raw.startswith("data:") and "," in raw:
        raw = raw.split(",", 1)[1]
    compact = re.sub(r"\s+", "", raw)
    pad = "=" * ((4 - len(compact) % 4) % 4)
    try:
        return base64.b64decode(compact + pad, validate=False)
    except Exception:
        return b""


def is_keystore(blob):
    if len(blob) < 16:
        return False
    header = blob[:4].hex()
    return header in ("feedfeed", "cececece") or blob.startswith(b"\x30")


def props_text_from(name):
    raw = val(name).strip()
    if not raw:
        return ""
    if looks_like_props(raw):
        return raw
    decoded = decode_b64(raw)
    try:
        text = decoded.decode("utf-8")
    except UnicodeDecodeError:
        text = ""
    if looks_like_props(text):
        return text
    return raw if looks_like_props(raw) else ""


def main():
    secrets = [val(n) for n in NAMES]
    for name in NAMES:
        print("%s=%s" % (name, "set" if val(name).strip() else "empty"))

    if not val("SNAP_REGISTRY_CLIENT_KEY").strip():
        print("ERROR: SNAP_REGISTRY_CLIENT_KEY is empty; refusing to publish a release APK without the client key.", file=sys.stderr)
        return 1

    blob = b""
    used = ""
    for name in KEYSTORE_B64_NAMES:
        raw = val(name).strip()
        if not raw:
            continue
        decoded = decode_b64(raw)
        if is_keystore(decoded):
            blob = decoded
            used = name
            break
    if not blob:
        print("ERROR: no ANDROID_KEYSTORE_* secret contained a JKS/PKCS12 keystore.", file=sys.stderr)
        return 1
    header = blob[:4].hex()

    tmp = pathlib.Path(os.environ["RUNNER_TEMP"])
    ks_path = tmp / "release.keystore"
    props_path = tmp / "voicecal-keystore.properties"
    ks_path.write_bytes(blob)
    os.chmod(ks_path, 0o600)
    print("keystore_bytes=%d source=%s header=%s" % (len(blob), used, header))

    props = {}
    for name in PROPS_NAMES:
        text = props_text_from(name)
        if text:
            props.update(parse_props(text))
            print("properties_source=%s" % name)
            break

    store_pass = val("ANDROID_KEYSTORE_STORE_PASSWORD").strip() or val("ANDROID_KEYSTORE_PASSWORD").strip() or props.get("storePassword", "")
    key_pass = val("ANDROID_KEYSTORE_KEY_PASSWORD").strip() or props.get("keyPassword", "") or store_pass
    alias = val("ANDROID_KEYSTORE_KEY_ALIAS").strip() or val("ANDROID_KEYSTORE_ALIAS").strip() or props.get("keyAlias", "")

    if not store_pass:
        print("ERROR: keystore store password secret is empty.", file=sys.stderr)
        return 1

    if not alias:
        listed = subprocess.run(
            ["keytool", "-list", "-keystore", str(ks_path), "-storepass", store_pass],
            capture_output=True, text=True,
        )
        if listed.returncode != 0:
            err = scrub((listed.stderr or listed.stdout or "keytool failed"), secrets)
            print("ERROR: keytool could not read the keystore: %s" % err.strip(), file=sys.stderr)
            return 1
        aliases = []
        for line in (listed.stdout or "").splitlines():
            if "PrivateKeyEntry" in line:
                aliases.append(line.split(",")[0].strip())
        print("keystore_aliases=%d" % len(aliases))
        if len(aliases) != 1:
            print("ERROR: set ANDROID_KEYSTORE_ALIAS to choose the signing key.", file=sys.stderr)
            return 1
        alias = aliases[0]

    lines = [
        "storeFile=%s" % escape_prop(str(ks_path)),
        "storePassword=%s" % escape_prop(store_pass),
        "keyAlias=%s" % escape_prop(alias),
        "keyPassword=%s" % escape_prop(key_pass),
    ]
    props_path.write_text("\n".join(lines) + "\n", encoding="ascii")
    os.chmod(props_path, 0o600)
    print("wrote_signing_props=yes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
