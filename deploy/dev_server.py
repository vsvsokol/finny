"""Локальный стенд: один origin для статики и API, только 127.0.0.1."""
import os
from pathlib import Path
import sys

from fastapi.staticfiles import StaticFiles

sys.path.insert(0, str(Path(__file__).resolve().parent/'api'))
from server import create_app, Settings


def create_dev_app():
    static = Path(os.environ['FINNY_STATIC'])
    if not (static/'sw.js').is_file():
        raise ValueError('Сначала упакуйте релиз через prepare_release.py')
    app = create_app(Settings(database=Path(os.environ['FINNY_DATABASE']),
        origin='http://127.0.0.1:'+os.environ.get('FINNY_DEV_PORT','18086'), secure_cookie=False))
    app.mount('/', StaticFiles(directory=static, html=True), name='static')
    return app
