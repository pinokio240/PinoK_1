#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
check-secrets.py (волна 31, задача 31-a) — валидатор утечек секретов в документации.

Сканирует ТОЛЬКО git-tracked *.md/*.txt (через `git ls-files`) на предмет
НЕМАСКИРОВАННЫХ секретных значений — теми же паттернами и порогами, что
использует scripts/mask-secrets.py (загружает его как модуль, чтобы списки
паттернов не разъезжались).

Вывод при хите: `SECRET HIT: файл:строка (паттерн)` — БЕЗ самих значений.
Exit-коды: 0 — OK (хитов нет), 1 — найдены немаскированные хиты, 2 — ошибка окружения.

Запуск:
  python3 scripts/check-secrets.py
"""

import importlib.util
import os
import subprocess
import sys

EXIT_OK = 0
EXIT_HITS = 1
EXIT_ERROR = 2

TEXT_EXTS = (".md", ".txt")


def load_mask_module():
    """Загружает scripts/mask-secrets.py как модуль (единый источник паттернов/порогов)."""
    here = os.path.dirname(os.path.abspath(__file__))
    path = os.path.join(here, "mask-secrets.py")
    spec = importlib.util.spec_from_file_location("mask_secrets", path)
    if spec is None or spec.loader is None:
        print("ERROR: cannot load mask-secrets.py from %s" % path, file=sys.stderr)
        sys.exit(EXIT_ERROR)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def tracked_text_files(root):
    """Список git-tracked *.md/*.txt (относительные пути). Детерминированный порядок."""
    try:
        proc = subprocess.run(["git", "-C", root, "ls-files", "-z"], capture_output=True)
    except OSError as e:
        print("ERROR: git unavailable: %s" % e.__class__.__name__, file=sys.stderr)
        sys.exit(EXIT_ERROR)
    if proc.returncode != 0:
        stderr = proc.stderr.decode("utf-8", "replace").strip()
        print("ERROR: git ls-files failed%s" % (": " + stderr if stderr else ""), file=sys.stderr)
        sys.exit(EXIT_ERROR)
    names = proc.stdout.decode("utf-8", "replace").split("\x00")
    return sorted(n for n in names if n and n.lower().endswith(TEXT_EXTS))


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    mod = load_mask_module()

    hits = []
    seen_bodies = set()
    checked = 0
    for rel in tracked_text_files(root):
        path = os.path.join(root, rel)
        if not os.path.isfile(path):
            print("WARN: tracked but missing on disk: %s" % rel)
            continue
        try:
            text, _enc = mod.load_text(path)
        except OSError as e:
            print("WARN: unreadable: %s (%s)" % (rel, e.__class__.__name__))
            continue
        checked += 1
        for name, m in mod.iter_hits(text):
            span = (m.start(1), m.end(1))
            if span in seen_bodies:
                continue  # одно значение, пойманное несколькими паттернами
            seen_bodies.add(span)
            line = text.count("\n", 0, m.start()) + 1
            hits.append((rel, line, name))

    if hits:
        for rel, line, name in hits:
            print("SECRET HIT: %s:%d (%s)" % (rel, line, name))
        print("FAILED: %d unmasked secret hit(s) in %d tracked md/txt file(s)" % (len(hits), checked))
        return EXIT_HITS
    print("OK: no unmasked secrets in %d tracked md/txt file(s)" % checked)
    return EXIT_OK


if __name__ == "__main__":
    sys.exit(main())
