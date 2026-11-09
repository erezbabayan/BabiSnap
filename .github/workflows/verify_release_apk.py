#!/usr/bin/env python3
"""Check the release APK: version 2.23 (34), v2.19 signer, client key baked in, no admin key."""
import hashlib
import json
import os
import pathlib
import re
import struct
import sys

# Public certificate from the published v2.19 BabiSnap.apk (CN=Erez Babayan). Not a secret.
EXPECTED_SHA256 = bytes.fromhex(
    "5A8CBCB9E89365476126EDCCBF2A023ACCB7A16D15384044D94AEBEAD6B9D315"
)
V2_ID = 0x7109871A
V3_ID = 0xF05368C0
V31_ID = 0x1B93AD61


def u32(buf, off):
    return struct.unpack_from("<I", buf, off)[0]


def certs_from_signer_block(block):
    """Return DER certs from an APK Signature Scheme v2/v3 signer sequence."""
    if len(block) < 4:
        return []
    signers_len = u32(block, 0)
    off = 4
    end = min(len(block), off + signers_len)
    certs = []
    while off + 4 <= end:
        slen = u32(block, off)
        off += 4
        signer = block[off : off + slen]
        off += slen
        if len(signer) < 4:
            break
        signed_len = u32(signer, 0)
        signed = signer[4 : 4 + signed_len]
        sd = 0
        if len(signed) < 4:
            continue
        dig_len = u32(signed, sd)
        sd += 4 + dig_len
        if sd + 4 > len(signed):
            continue
        certs_len = u32(signed, sd)
        sd += 4
        ce = min(len(signed), sd + certs_len)
        while sd + 4 <= ce:
            cl = u32(signed, sd)
            sd += 4
            certs.append(signed[sd : sd + cl])
            sd += cl
    return certs


def signing_certs(apk_bytes):
    eocd = apk_bytes.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise SystemExit("ERROR: APK has no ZIP end of central directory")
    cd_off = struct.unpack_from("<I", apk_bytes, eocd + 16)[0]
    magic = apk_bytes[cd_off - 16 : cd_off]
    if magic != b"APK Sig Block 42":
        raise SystemExit("ERROR: APK is not signed with APK Signature Scheme v2")
    size_of_block = struct.unpack_from("<Q", apk_bytes, cd_off - 24)[0]
    block_start = cd_off - size_of_block - 8
    if struct.unpack_from("<Q", apk_bytes, block_start)[0] != size_of_block:
        raise SystemExit("ERROR: APK signing block size mismatch")
    p = block_start + 8
    end = cd_off - 24
    found = []
    while p + 12 <= end:
        pair_len = struct.unpack_from("<Q", apk_bytes, p)[0]
        pid = struct.unpack_from("<I", apk_bytes, p + 8)[0]
        val = apk_bytes[p + 12 : p + 8 + pair_len]
        if pid in (V2_ID, V3_ID, V31_ID):
            found.extend(certs_from_signer_block(val))
        p += 8 + pair_len
    return found


def main():
    tmp = pathlib.Path(os.environ["RUNNER_TEMP"])
    src = pathlib.Path("android/app/build/outputs/apk/release/app-release.apk")
    meta = pathlib.Path("android/app/build/outputs/apk/release/output-metadata.json")
    if not src.is_file():
        print("ERROR: missing %s" % src, file=sys.stderr)
        return 1
    info = json.loads(meta.read_text())
    element = info["elements"][0]
    version_code = int(element["versionCode"])
    version_name = element["versionName"]
    print("versionCode=%s versionName=%s" % (version_code, version_name))
    if version_code != 34 or version_name != "2.23":
        print("ERROR: expected versionCode 34 versionName 2.23", file=sys.stderr)
        return 1

    apk = src.read_bytes()
    if b"SNAP_REGISTRY_ADMIN_KEY" in apk:
        print("ERROR: APK contains SNAP_REGISTRY_ADMIN_KEY", file=sys.stderr)
        return 1
    certs = signing_certs(apk)
    digests = [hashlib.sha256(c).digest() for c in certs]
    print("signer_certs=%d" % len(certs))
    if EXPECTED_SHA256 not in digests:
        print("ERROR: APK signer does not match the v2.19 release certificate", file=sys.stderr)
        for digest in digests:
            print("found_sha256=%s" % digest.hex(), file=sys.stderr)
        return 1

    configs = list(pathlib.Path("android/app/build").rglob("BuildConfig.java"))
    release_configs = [p for p in configs if "release" in p.as_posix()]
    if not release_configs:
        print("ERROR: release BuildConfig.java was not generated", file=sys.stderr)
        return 1
    text = release_configs[0].read_text(encoding="utf-8", errors="replace")
    if "SNAP_REGISTRY_ADMIN" in text:
        print("ERROR: BuildConfig mentions the admin key", file=sys.stderr)
        return 1
    match = re.search(r'SNAP_REGISTRY_CLIENT_KEY = "(.*)";', text)
    if not match:
        print("ERROR: BuildConfig is missing SNAP_REGISTRY_CLIENT_KEY", file=sys.stderr)
        return 1
    # Escaped literal length only — do not print the key.
    literal = match.group(1)
    key_len = len(literal)
    print("client_key_baked_in=%s" % ("yes" if key_len else "no"))
    print("client_key_length=%d" % key_len)
    if key_len <= 0:
        print("ERROR: client key was not baked into BuildConfig", file=sys.stderr)
        return 1

    dest = tmp / "BabiSnap.apk"
    dest.write_bytes(apk)
    print("apk_bytes=%d" % dest.stat().st_size)
    return 0


if __name__ == "__main__":
    sys.exit(main())
