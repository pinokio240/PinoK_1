#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
mask-secrets.py (волна 31, задача 31-a) — маскировка секретных значений в *.md/*.txt.

Что делает:
  - Рекурсивно сканирует все *.md и *.txt от корня репозитория
    (исключая .git/ и node_modules/), правит найденные секреты in-place.
  - Маскируются ТОЛЬКО значения (тела токенов/секретов); ключевые слова,
    markdown-структура (заголовки/таблицы/фенсы) и остальной текст не меняются.
  - Формат маски: первые 4 + '...MASKED...' (ASCII-точки) + последние 4 символа тела.
    Тело короче 12 символов заменяется целиком на 'MASKED'.
  - Идемпотентность: '...MASKED...' рвёт символьный класс тела, поэтому
    повторный запуск ничего не меняет (проверяется selftest'ом).

Паттерны (порог = минимальная длина тела для маскировки):
  1) vk-token       : vk1.a.<тело> / vk2.a.<тело>                          (тело 15+)
  2) access_token   : access_token=<тело> / "access_token":"<тело>"        (тело 15+)
  3) bearer         : Bearer <тело>                                        (тело 20+)
  4) client_secret  : client_secret=<тело> / "client_secret":"<тело>"      (тело 15+)
  5) kv-secret      : (…_)?token|secret|password + '='|':' / JSON-значение (тело 20+)
  Тело = сегменты [A-Za-z0-9_-]+, разделённые одиночными точками
  (покрывает JWT-подобные значения: anonymous_token и т.п.).

Вывод: по файлу — количество замаскированных значений. Сами значения
ни в stdout, ни куда-либо ещё не печатаются.

Кодировки: файл читается байтово; UTF-8 строгой декодировкой, при неудаче —
latin-1 (потеря-free отображение байтов 1:1) и запись тем же способом,
поэтому не-ASCII байты любого файла сохраняются байт-в-байт, а ASCII-тела
токенов корректно распознаются при любой однобайтовой кодировке.

Запуск:
  python3 scripts/mask-secrets.py            # скан репозитория + in-place правка
  python3 scripts/mask-secrets.py --selftest # синтетические проверки (без печати значений)
"""

import os
import re
import sys

MASK = "...MASKED..."
SKIP_DIRS = {".git", "node_modules"}
TEXT_EXTS = (".md", ".txt")

# Тело токена: сегменты [A-Za-z0-9_-]+ через одиночные точки (JWT-подобные значения).
_SEG = r"[A-Za-z0-9_\-]+"
_BODY = _SEG + r"(?:\." + _SEG + r")*"
# Разделитель ключ/значение: '=', ':' или URL-кодированный '%3D'; кавычки вокруг значения опциональны.
_SEP = r"\s*(?:[=:]|%3[dD])\s*"
_Q = r"[\"']?"

# (имя паттерна, скомпилированный regex, минимальная длина тела для маскировки)
PATTERNS = (
    ("vk-token", re.compile(r"\bvk[12]\.a\.(" + _BODY + r")", re.IGNORECASE), 15),
    ("access_token", re.compile(r"\baccess_tokens?\b" + _Q + _SEP + _Q + r"(" + _BODY + r")", re.IGNORECASE), 15),
    ("bearer", re.compile(r"\bBearer\s+(" + _BODY + r")", re.IGNORECASE), 20),
    ("client_secret", re.compile(r"\bclient_secrets?\b" + _Q + _SEP + _Q + r"(" + _BODY + r")", re.IGNORECASE), 15),
    ("kv-secret", re.compile(
        r"\b(?:[A-Za-z0-9_]+_)?(?:token|secret|password)s?\b" + _Q + _SEP + _Q + r"(" + _BODY + r")",
        re.IGNORECASE), 20),
)


def mask_body(body):
    """Маска тела: первые 4 + '...MASKED...' + последние 4; короче 12 — целиком 'MASKED'."""
    if not body:
        return body
    if len(body) < 12:
        return "MASKED"
    return body[:4] + MASK + body[-4:]


def _replacer(match, min_len):
    """Замена совпадения: префикс (ключ/разделитель/кавычка) сохраняется, тело маскируется."""
    body = match.group(1)
    if not body or len(body) < min_len:
        return match.group(0)
    if MASK in body:
        return match.group(0)  # страховка от повторной обработки уже маскированного тела
    prefix_len = match.start(1) - match.start(0)
    return match.group(0)[:prefix_len] + mask_body(body)


def mask_text(text):
    """Последовательно применяет все паттерны. Возвращает (новый текст, число РЕАЛЬНЫХ замен).

    Совпадения ниже порога длины replacer возвращает без изменений — они не считаются
    (иначе re.subn завышал бы счётчик no-op совпадениями на уже маскированном тексте).
    """
    total = 0
    for _name, rx, min_len in PATTERNS:
        counter = [0]

        def repl(m, ml=min_len, c=counter):
            res = _replacer(m, ml)
            if res != m.group(0):
                c[0] += 1
            return res

        text = rx.sub(repl, text)
        total += counter[0]
    return text, total


def iter_hits(text):
    """Итератор хитов для валидатора: (имя паттерна, match). Дубли одного тела схлопываются снаружи.

    Согласован с mask_text по паттернам и порогам; на маскированном тексте хитов не даёт.
    """
    for name, rx, min_len in PATTERNS:
        for m in rx.finditer(text):
            body = m.group(1)
            if body and len(body) >= min_len and MASK not in body:
                yield name, m


def load_text(path):
    """Читает файл байтово и декодирует без потерь: utf-8, при неудаче latin-1 (байты 1:1).

    Возвращает (текст, кодировка для обратной записи). OSError пробрасывается наверх.
    """
    with open(path, "rb") as f:
        raw = f.read()
    try:
        return raw.decode("utf-8"), "utf-8"
    except UnicodeDecodeError:
        # latin-1 отображает каждый байт 1:1 — запись обратно даёт исходные не-ASCII байты,
        # при этом ASCII-тела токенов распознаются корректно при любой однобайтовой кодировке.
        return raw.decode("latin-1"), "latin-1"


def write_text(path, text, encoding):
    with open(path, "wb") as f:
        f.write(text.encode(encoding))


def repo_root():
    return os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def iter_text_files(root):
    """Рекурсивный обход *.md/*.txt от root (skip .git/, node_modules/), детерминированный порядок."""
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(d for d in dirnames if d not in SKIP_DIRS)
        for fn in sorted(filenames):
            if fn.lower().endswith(TEXT_EXTS):
                yield os.path.join(dirpath, fn)


def mask_repo(root):
    """Скан репозитория, in-place маскировка. Печатает по файлу количество замаскированных значений."""
    scanned = changed = total = 0
    for path in iter_text_files(root):
        rel = os.path.relpath(path, root)
        try:
            text, enc = load_text(path)
        except OSError as e:
            print("WARN: skip (read error): %s (%s)" % (rel, e.__class__.__name__))
            continue
        new_text, n = mask_text(text)
        scanned += 1
        if n <= 0:
            continue
        try:
            write_text(path, new_text, enc)
        except OSError as e:
            print("WARN: skip (write error): %s (%s)" % (rel, e.__class__.__name__))
            continue
        changed += 1
        total += n
        print("%s: %d masked" % (rel, n))
    print("---")
    print("scanned=%d changed_files=%d masked_values=%d" % (scanned, changed, total))
    return 0


def run_selftest():
    """Синтетические проверки на сгенерированных строках. Значения не печатаются."""
    failures = []

    def check(name, cond):
        if cond:
            print("selftest ok: %s" % name)
        else:
            failures.append(name)
            print("SELFTEST FAIL: %s" % name)

    body = "Ab1-_" * 6  # 24 символа, класс [A-Za-z0-9_-]
    # 1) vk1.a. — маска формата first4+MASK+last4, префикс сохранён
    t = "vk1.a." + body
    t1, n = mask_text(t)
    check("vk1.a masked once", n == 1)
    check("vk1.a mask format", t1 == "vk1.a." + body[:4] + MASK + body[-4:])
    t2, n2 = mask_text(t1)
    check("vk1.a idempotent", n2 == 0 and t2 == t1)

    # 2) vk2.a. + вложение в URL с & и access_token=
    # (значение access_token — сам vk2.a-токен: (a) маскирует тело, (b) на уже
    # маскированном значении даёт тело короче порога => РЕАЛЬНО 1 замена)
    url = "https://oauth.vk.com/blank.html#access_token=vk2.a." + body + "&expires_in=86400&user_id=1"
    u1, n = mask_text(url)
    check("url access_token+vk2.a masked (1 value)", n == 1)
    check("url structure kept", u1.startswith("https://oauth.vk.com/blank.html#access_token=")
          and u1.endswith("&expires_in=86400&user_id=1"))
    _u2, n = mask_text(u1)
    check("url idempotent", n == 0)

    # 3) JSON-формы
    js = '{"access_token":"' + body + '","client_secret":"' + body + '","anonymous_token":"' + body + '"}'
    j1, n = mask_text(js)
    check("json values masked (3)", n == 3)
    check("json braces/quotes kept", j1.startswith('{"access_token":"') and j1.endswith('"}') and j1.count('"') == js.count('"'))
    _j2, n = mask_text(j1)
    check("json idempotent", n == 0)

    # 4) Bearer и JWT-подобное тело с точками
    jwt = "Bearer " + ("eyJ" + "A" * 10) + "." + ("eyJ" + "B" * 10) + "." + ("sig" + "C" * 8)
    b1, n = mask_text(jwt)
    check("bearer jwt masked (1)", n == 1)
    _b2, n = mask_text(b1)
    check("bearer idempotent", n == 0)

    # 5) generic-ключи: token:, password =, %3D, вложенные *_token
    k1, n = mask_text("token: " + body + "\npassword = " + body + "\nrefresh_token%3D" + body + "\n")
    check("kv keys masked (3)", n == 3)
    _k2, n = mask_text(k1)
    check("kv idempotent", n == 0)

    # 6) НЕ секреты — не маскируются
    plain = [
        "client_secret передаётся в теле запроса",
        "token: null",
        "token: см. таблицу ниже",
        "Bearer tokens are rotated hourly",
        "vk1.a. формат токенов",
        "access_token=<token>",
        "access_token=YOUR_SECRET",  # 11 символов < 15
        "nonsecret=AAAAAAAAAAAAAAAAAAAA",
        "password_hash: $2y$10$abcdefghijklmnopqrstuvwxyz",
        "токен выдан на 86400 секунд",
    ]
    p1, n = mask_text("\n".join(plain))
    check("non-secrets untouched", n == 0 and p1 == "\n".join(plain))

    # 7) короткое тело (< 12) — целиком MASKED (проверка mask_body напрямую)
    check("short body -> MASKED", mask_body("AbCdEf") == "MASKED")

    # 8) markdown-структура: таблица/фенсы/заголовки живы, изменено только значение
    md = ("# Заголовок\n\n| поле | значение |\n|---|---|\n"
          "| access_token | vk1.a." + body + " |\n\n```json\n{\"token\":\"" + body + "\"}\n```\n")
    m1, n = mask_text(md)
    check("md values masked (2)", n == 2)
    check("md structure kept", m1.startswith("# Заголовок") and "|---|---|" in m1
          and m1.count("|") == md.count("|") and "```json" in m1 and m1.count("```") == 2)

    # 9) CRLF и BOM сохраняются байт-в-байт (кодировка utf-8)
    crlf = "line1\r\naccess_token=" + body + "\r\nline3"
    c1, n = mask_text(crlf)
    check("crlf kept", n == 1 and "\r\n" in c1 and c1.count("\r\n") == 2)

    # 10) iter_hits согласован с маскировкой: на маскированном — пусто, на сыром — есть
    raw = "secret=" + body
    check("hits on raw >= 1", sum(1 for _ in iter_hits(raw)) >= 1)
    r1, _ = mask_text(raw)
    check("hits on masked == 0", sum(1 for _ in iter_hits(r1)) == 0)

    if failures:
        print("SELFTEST FAILED: %d case(s): %s" % (len(failures), ", ".join(failures)))
        return 1
    print("selftest: ALL PASS")
    return 0


def main(argv):
    if "--selftest" in argv:
        return run_selftest()
    return mask_repo(repo_root())


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
