"""Согласованная копия SQLite; путь назначения должен быть новым файлом."""
import argparse
from contextlib import closing
import os
from pathlib import Path
import sqlite3


def backup(source: Path, destination: Path):
    if not source.is_file():
        raise ValueError('Исходная база не найдена')
    if source.resolve() == destination.resolve():
        raise ValueError('Копия должна находиться в отдельном файле')
    destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    # O_EXCL исключает случайную перезапись базы или готовой копии.
    fd = os.open(destination, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
    os.close(fd)
    try:
        with closing(sqlite3.connect(source.resolve().as_uri()+'?mode=ro', uri=True)) as src:
            with closing(sqlite3.connect(destination)) as dst:
                src.backup(dst)
                if dst.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
                    raise RuntimeError('Нарушена целостность резервной копии')
    except BaseException:
        destination.unlink(missing_ok=True)
        raise
    return destination


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    backup(args.source, args.destination)
    print('Резервная копия создана; integrity_check: ok')
