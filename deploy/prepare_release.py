"""Упаковывает статику и полный кэш офлайн-версии в новый каталог."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil


def prepare(source: Path, destination: Path):
    if not (source/'index.html').is_file() or not list(source.glob('*.wasm')):
        raise ValueError('Нужен каталог готовой сборки Kotlin/Wasm')
    if destination.exists():
        raise FileExistsError('Каталог назначения должен быть новым')
    if source.resolve() in destination.resolve().parents:
        raise ValueError('Назначение не должно быть внутри исходной сборки')
    shutil.copytree(source, destination, ignore=shutil.ignore_patterns('*.map'))
    template = (destination/'offline-worker.js').read_text()
    (destination/'offline-worker.js').unlink()
    assets = sorted(str(path.relative_to(destination)) for path in destination.rglob('*') if path.is_file())
    checksums = {name: hashlib.sha256((destination/name).read_bytes()).hexdigest() for name in assets}
    release = hashlib.sha256(json.dumps(checksums, sort_keys=True).encode()).hexdigest()[:20]
    (destination/'sw.js').write_text('const FINNY_CACHE = '+json.dumps('finny-static-'+release)+';\nconst FINNY_ASSETS = '+json.dumps(assets)+';\n'+template)
    (destination/'release.json').write_text(json.dumps({'release': release, 'assets': checksums}, indent=2)+'\n')
    return release


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('destination', type=Path)
    args = parser.parse_args()
    print('Упакован релиз:', prepare(args.source, args.destination))
