#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
sign_manifest.py — подпись/проверка манифеста обновлений PinoK (волна 46 #UPDATER-SIGNING).

Ed25519 по RFC 8032. Один файл, НОЛЬ зависимостей: ниже адаптация public-domain
reference-реализации (ed25519.cr.yp.to, D. J. Bernstein et al.; та же математика,
что в тест-векторах RFC 8032 §7.1 — самопроверка командой selftest).

Поток доверия (docs/UPDATER.md §3):
  приватный ключ (32 байта seed, hex) — ТОЛЬКО у релиз-менеджера, вне репозитория;
  публичный ключ (base64) — зашит в APK (re.pinok.updater.UpdaterSigning);
  version.json.sig — base64 detached-подписи РОВНО над байтами version.json.

Команды:
  selftest                              RFC 8032 тест-векторы (обязателен после любых правок математики)
  keygen [--force]                      новый ключ: private_key.hex (СЕКРЕТ) + печать публичного
  pubkey  [--key FILE]                  публичный ключ из приватного (base64 + готовая Kotlin-константа)
  sign    FILE [--key FILE]             подписать файл → FILE.sig
  verify  FILE [--sig FILE] [--pub B64] проверить detached-подпись

Приватный ключ хранить вне git (gitignore tools/updater-sign/private_key* —
страховка от случайного коммита). Потеря ключа = вечный fail-closed для уже
установленных сборок: ротация требует новой сборки APK с новым публичным ключом.
"""

import argparse
import base64
import hashlib
import os
import sys

# ── RFC 8032 reference (public-domain код ed25519.cr.yp.to, адаптация py3) ──

b = 256
q = 2 ** 255 - 19
l = 2 ** 252 + 27742317777372353535851937790883648493
d = (-121665 * pow(121666, q - 2, q)) % q
I = pow(2, (q - 1) // 4, q)


def _inv(x):
    return pow(x, q - 2, q)


def _xrecover(y):
    xx = (y * y - 1) * _inv(d * y * y + 1)
    x = pow(xx, (q + 3) // 8, q)
    if (x * x - xx) % q != 0:
        x = (x * I) % q
    if x % 2 != 0:
        x = q - x
    return x


_By = (4 * _inv(5)) % q
_B = (_xrecover(_By), _By)  # (x, y) базовой точки ed25519


def _edwards(p, other):
    """Сложение точек (аффинные координаты, twisted Edwards)."""
    x1, y1 = p
    x2, y2 = other
    k = d * x1 * x2 * y1 * y2
    x3 = (x1 * y2 + x2 * y1) * _inv(1 + k)
    y3 = (y1 * y2 + x1 * x2) * _inv(1 - k)
    return (x3 % q, y3 % q)


def _scalarmult(p, e):
    """e*p удвоением-сложением (итеративно — без рекурсии)."""
    result = (0, 1)  # нейтральный элемент
    base = p
    while e > 0:
        if e & 1:
            result = _edwards(result, base)
        base = _edwards(base, base)
        e >>= 1
    return result


def _isoncurve(p):
    x, y = p
    return (-x * x + y * y - 1 - d * x * x * y * y) % q == 0


def _decodeint(data):
    return int.from_bytes(data[:32], "little")


def _decodepoint(data):
    y = _decodeint(data) & ((1 << 255) - 1)
    x = _xrecover(y)
    if x & 1 != (data[31] >> 7) & 1:
        x = q - x
    p = (x % q, y % q)
    if not _isoncurve(p):
        raise ValueError("точка не на кривой")
    return p


def _encodepoint(p):
    x, y = p
    bits = [(y >> i) & 1 for i in range(255)] + [x & 1]
    out = bytearray(32)
    for i in range(32):
        byte = 0
        for j in range(8):
            byte |= bits[i * 8 + j] << j
        out[i] = byte
    return bytes(out)


def _hint(data):
    """SHA-512 → little-endian int (как в reference)."""
    return int.from_bytes(hashlib.sha512(data).digest(), "little")


def _secret_expand(seed):
    """Клэмпинг скаляра: бит 254 установлен, биты 0–2 сняты, старшие биты не используются."""
    if len(seed) != 32:
        raise ValueError("seed должен быть ровно 32 байта")
    h = hashlib.sha512(seed).digest()
    a = 2 ** (b - 2)
    for i in range(3, b - 2):
        a += 2 ** i * ((h[i >> 3] >> (i & 7)) & 1)
    return a, h


def publickey(seed):
    a, _ = _secret_expand(seed)
    return _encodepoint(_scalarmult(_B, a))


def signature(msg, seed, pk):
    a, h = _secret_expand(seed)
    r = _hint(h[32:64] + msg)
    R = _scalarmult(_B, r)
    rs = _encodepoint(R)
    k = _hint(rs + pk + msg)
    s = (r + k * a) % l
    return rs + s.to_bytes(32, "little")


def verify(sig, msg, pk):
    if len(sig) != 64 or len(pk) != 32:
        return False
    try:
        R = _decodepoint(sig[:32])
        A = _decodepoint(pk)
    except Exception:
        return False
    s = _decodeint(sig[32:])
    if s >= l:
        return False
    k = _hint(_encodepoint(R) + pk + msg)
    return _scalarmult(_B, s) == _edwards(R, _scalarmult(A, k))


# ── Тест-векторы RFC 8032 §7.1 (TEST 1–3) ────────────────────────────────────

VECTORS = [
    (
        "9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60",
        "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a",
        "",
        "e5564300c360ac729086e2cc806e828a84877f1eb8e5d974d873e06522490155"
        "5fb8821590a33bacc61e39701cf9b46bd25bf5f0595bbe24655141438e7a100b",
    ),
    (
        "4ccd089b28ff96da9db6c346ec114e0f5b8a319f35aba624da8cf6ed4fb8a6fb",
        "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c",
        "72",
        "92a009a9f0d4cab8720e820b5f642540a2b27b5416503f8fb3762223ebdb69da"
        "085ac1e43e15996e458f3613d0f11d8c387b2eaeb4302aeeb00d291612bb0c00",
    ),
    (
        "c5aa8df43f9f837bedb7442f31dcb7b166d38535076f094b85ce3a2e0b4458f7",
        "fc51cd8e6218a1a38da47ed00230f0580816ed13ba3303ac5deb911548908025",
        "af82",
        "6291d657deec24024827e69c3abe01a30ce548a284743a445e3680d7db5ac3ac"
        "18ff9b538d16f290ae67f760984dc6594a7c15e9716ed28dc027beceea1ec40a",
    ),
]


def cmd_selftest(_args):
    ok = True
    for idx, (sk_hex, pk_hex, msg_hex, sig_hex) in enumerate(VECTORS, 1):
        seed = bytes.fromhex(sk_hex)
        want_pk = bytes.fromhex(pk_hex)
        msg = bytes.fromhex(msg_hex)
        want_sig = bytes.fromhex(sig_hex)
        got_pk = publickey(seed)
        got_sig = signature(msg, seed, want_pk)
        checks = [
            ("publickey", got_pk == want_pk),
            ("signature", got_sig == want_sig),
            ("verify ok", verify(want_sig, msg, want_pk)),
            ("verify reject (битый msg)", not verify(want_sig, msg + b"x", want_pk)),
            ("verify reject (битая sig)", not verify(want_sig[:63] + bytes([want_sig[63] ^ 1]), msg, want_pk)),
        ]
        for name, passed in checks:
            print("TEST %d %-28s %s" % (idx, name, "OK" if passed else "FAIL"))
            ok = ok and passed
    print("selftest: %s" % ("PASS" if ok else "FAIL"))
    return 0 if ok else 1


# ── CLI ──────────────────────────────────────────────────────────────────────

DEFAULT_KEY = "private_key.hex"


def _load_seed(path):
    if not os.path.exists(path):
        print("ОШИБКА: файл ключа не найден: " + path)
        sys.exit(1)
    with open(path, "r", encoding="utf-8") as fh:
        text = fh.read().strip()
    try:
        seed = bytes.fromhex(text)
    except ValueError:
        print("ОШИБКА: в файле ключа не hex: " + path)
        sys.exit(1)
    if len(seed) != 32:
        print("ОШИБКА: seed должен быть 32 байта (64 hex-символа), получено " + str(len(seed)))
        sys.exit(1)
    return seed


def cmd_keygen(args):
    if os.path.exists(args.key) and not args.force:
        print("ОШИБКА: " + args.key + " уже существует (перезаписать — только с --force)")
        sys.exit(1)
    seed = os.urandom(32)
    pk = publickey(seed)
    with open(args.key, "w", encoding="utf-8") as fh:
        fh.write(seed.hex() + "\n")
    os.chmod(args.key, 0o600)
    print("Приватный ключ (СЕКРЕТ, не коммитить!): " + os.path.abspath(args.key))
    print("Публичный ключ (base64): " + base64.b64encode(pk).decode("ascii"))
    print("Отпечаток публичного (sha256, hex): " + hashlib.sha256(pk).hexdigest())
    print()
    print("Готовая константа для re.pinok.updater.UpdaterSigning:")
    print('    const val RELEASE_PUBLIC_KEY_B64 = "' + base64.b64encode(pk).decode("ascii") + '"')
    return 0


def cmd_pubkey(args):
    seed = _load_seed(args.key)
    pk = publickey(seed)
    print("Публичный ключ (base64): " + base64.b64encode(pk).decode("ascii"))
    print("Отпечаток публичного (sha256, hex): " + hashlib.sha256(pk).hexdigest())
    print()
    print('    const val RELEASE_PUBLIC_KEY_B64 = "' + base64.b64encode(pk).decode("ascii") + '"')
    return 0


def cmd_sign(args):
    seed = _load_seed(args.key)
    with open(args.file, "rb") as fh:
        msg = fh.read()
    pk = publickey(seed)
    sig = signature(msg, seed, pk)
    out_path = args.file + ".sig"
    with open(out_path, "w", encoding="utf-8") as fh:
        fh.write(base64.b64encode(sig).decode("ascii") + "\n")
    print("Подписан: " + args.file + " (" + str(len(msg)) + " байт)")
    print("Подпись:  " + out_path + " (base64, " + str(len(sig)) + " байта)")
    print("Отпечаток публичного ключа: " + hashlib.sha256(pk).hexdigest())
    print("Самопроверка: " + ("OK" if verify(sig, msg, pk) else "FAIL — НЕ ИСПОЛЬЗОВАТЬ"))
    return 0


def cmd_verify(args):
    with open(args.file, "rb") as fh:
        msg = fh.read()
    sig_path = args.sig if args.sig else args.file + ".sig"
    with open(sig_path, "r", encoding="utf-8") as fh:
        sig_text = fh.read().strip()
    sig = base64.b64decode(sig_text, validate=True)
    if args.pub:
        pk = base64.b64decode(args.pub.strip(), validate=True)
    else:
        seed = _load_seed(args.key)
        pk = publickey(seed)
    verdict = verify(sig, msg, pk)
    print(("ПОДПИСЬ ВЕРНА" if verdict else "ПОДПИСЬ НЕ ВЕРНА") + ": " + args.file + " (" + str(len(msg)) + " байт)")
    return 0 if verdict else 1


def main():
    parser = argparse.ArgumentParser(
        description="ed25519-подпись манифеста обновлений PinoK (волна 46 #UPDATER-SIGNING)",
    )
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("selftest", help="RFC 8032 тест-векторы")

    p = sub.add_parser("keygen", help="новая пара ключей")
    p.add_argument("--key", default=DEFAULT_KEY)
    p.add_argument("--force", action="store_true")

    p = sub.add_parser("pubkey", help="публичный ключ из приватного")
    p.add_argument("--key", default=DEFAULT_KEY)

    p = sub.add_parser("sign", help="подписать манифест")
    p.add_argument("file")
    p.add_argument("--key", default=DEFAULT_KEY)

    p = sub.add_parser("verify", help="проверить подпись")
    p.add_argument("file")
    p.add_argument("--sig", default=None)
    p.add_argument("--pub", default=None)
    p.add_argument("--key", default=DEFAULT_KEY)

    args = parser.parse_args()
    handlers = {
        "selftest": cmd_selftest,
        "keygen": cmd_keygen,
        "pubkey": cmd_pubkey,
        "sign": cmd_sign,
        "verify": cmd_verify,
    }
    sys.exit(handlers[args.cmd](args))


if __name__ == "__main__":
    main()
