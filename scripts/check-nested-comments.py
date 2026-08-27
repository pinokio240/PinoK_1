#!/usr/bin/env python3
"""
check-nested-comments.py — защита от nested block comment багов в Kotlin.

ПРОБЛЕМА:
  Kotlin поддерживает ВЛОЖЕННЫЕ блочные комментарии: `/* … /* … */ … */`.
  Если внутри `/** … */` KDoc-блока случайно написан `/*` (например, glob-
  шаблон `m.vk.ru/*` или inline-комментарий `/* … */` в примере кода),
  Kotlin парсит это как opening nested comment. Один `*/` в конце KDoc
  закрывает только внутренний уровень → весь последующий код до следующего
  `*/` становится комментарием → 15+ ошибок компиляции в каскаде
  (Unresolved reference, Cannot infer type parameter, Missing '}',
  Unclosed comment).

  Реальные случаи в этом проекте:
    - WebTokenAuth.kt (фикс 854a9a9d8, 2026-08-05) — `m.vk.ru/*` в KDoc
    - ChannelWebSocketClient.kt (фикс a69472cc7, 2026-08-05) — `/* … */` в примере

ИСПОЛЬЗОВАНИЕ:
  python3 scripts/check-nested-comments.py            # проверить все .kt/.kts
  python3 scripts/check-nested-comments.py path/file.kt  # проверить один файл
  python3 scripts/check-nested-comments.py --fix       # показать что заменить (без правок)

ВЫХОД:
  0 — всё чисто
  1 — найдены опасные паттерны (подробности в stdout)

См. CODING_STYLE.md → «KDoc и вложенные комментарии».
"""
import glob
import sys
import os
import re

def find_dangerous_patterns(text):
    """
    Правильный nested-comment парсер Kotlin.
    Возвращает список (line, kind, content):
      kind='NESTED_OPEN'  — `/*` (не `/**`) внутри block comment
      kind='UNBALANCED'   — файл заканчивается с level != 0
    """
    findings = []
    lines = text.split('\n')
    level = 0
    i = 0
    n = len(text)
    pos_line = 1
    while i < n:
        if text[i] == '\n':
            pos_line += 1
        if level > 0:
            if text[i:i+2] == '/*':
                # `/*` внутри block comment — это nested opening.
                # Исключение: `/**` (triple-star) — это новый KDoc opener,
                # тоже legitimate (хотя и редкий), не считаем опасным.
                if not (i + 2 < n and text[i + 2] == '*'):
                    content = lines[pos_line - 1] if pos_line - 1 < len(lines) else ''
                    findings.append((pos_line, 'NESTED_OPEN', content))
                level += 1
                i += 2
                continue
            if text[i:i+2] == '*/':
                level -= 1
                i += 2
                continue
            i += 1
            continue
        else:
            # не внутри block comment
            if text[i:i+2] == '//':
                # line comment — пропускаем до конца строки
                while i < n and text[i] != '\n':
                    i += 1
                continue
            if text[i:i+2] == '/*':
                level += 1
                i += 2
                continue
            # string literal "..."
            if text[i] == '"':
                i += 1
                while i < n:
                    if text[i] == '\\':
                        i += 2
                        continue
                    if text[i] == '"':
                        i += 1
                        break
                    i += 1
                continue
            # char literal '...'
            if text[i] == "'":
                i += 1
                while i < n:
                    if text[i] == '\\':
                        i += 2
                        continue
                    if text[i] == "'":
                        i += 1
                        break
                    i += 1
                continue
            i += 1
            continue
    if level != 0:
        findings.append((pos_line, 'UNBALANCED', f'final level={level}'))
    return findings


def main():
    args = sys.argv[1:]
    if args and args[0] in ('-h', '--help'):
        print(__doc__)
        return 0

    if args and not args[0].startswith('-'):
        files = args
    else:
        # все .kt и .kts в проекте (кроме build/, .git/)
        files = []
        for pattern in ['app/src/main/java/**/*.kt',
                        'app/src/test/**/*.kt',
                        'app/src/androidTest/**/*.kt',
                        'buildSrc/**/*.kt',
                        '*.kts', 'app/*.kts']:
            files.extend(glob.glob(pattern, recursive=True))
        files = sorted(set(files))

    if not files:
        print('No .kt/.kts files found.')
        return 0

    total_issues = 0
    files_with_issues = 0
    for f in files:
        if not os.path.isfile(f):
            continue
        try:
            text = open(f, encoding='utf-8').read()
        except Exception as e:
            print(f'{f}: READ ERROR: {e}')
            continue
        findings = find_dangerous_patterns(text)
        if findings:
            files_with_issues += 1
            total_issues += len(findings)
            print(f'\n❌ {f} — {len(findings)} issue(s):')
            for line, kind, content in findings:
                if kind == 'NESTED_OPEN':
                    print(f'   line {line}: nested `/*` inside comment block')
                    print(f'     │ {content.rstrip()}')
                    print(f'     └ fix: replace `/*` with `…` or `/<path>`, '
                          f'or use `//` line-comment')
                elif kind == 'UNBALANCED':
                    print(f'   line {line}: UNBALANCED block comments ({content})')
                    print(f'     └ fix: ensure every `/*` has a matching `*/`')

    print(f'\n{"=" * 60}')
    print(f'Scanned {len(files)} file(s).')
    if total_issues == 0:
        print('✅ ALL CLEAN — no nested /* in KDoc, no unbalanced comments.')
        return 0
    else:
        print(f'❌ {total_issues} issue(s) in {files_with_issues} file(s). '
              f'See CODING_STYLE.md → «KDoc и вложенные комментарии».')
        return 1


if __name__ == '__main__':
    sys.exit(main())
