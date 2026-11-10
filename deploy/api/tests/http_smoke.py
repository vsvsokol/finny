"""Проверка реального HTTP-процесса только в одноразовом контейнере с пустой БД."""
import json
import secrets
import time
import urllib.error
import urllib.request
from http.cookies import SimpleCookie

BASE = 'http://127.0.0.1:8000'
ORIGIN = 'https://finny.mango-kokos.ru'
headers = {'Host': 'finny.mango-kokos.ru', 'Origin': ORIGIN, 'Content-Type': 'application/json'}


def call(path, data=None, method=None):
    request = urllib.request.Request(BASE+path, headers=headers, method=method,
        data=json.dumps(data).encode() if data is not None else None)
    with urllib.request.urlopen(request, timeout=3) as response:
        if response.headers.get('Set-Cookie'):
            cookie = SimpleCookie(response.headers['Set-Cookie'])
            headers['Cookie'] = '; '.join(f'{key}={item.value}' for key, item in cookie.items())
        return response.status, json.load(response)


for attempt in range(20):
    try:
        assert call('/api/health')[0] == 200
        break
    except (urllib.error.URLError, ConnectionError):
        time.sleep(0.25)
else:
    raise RuntimeError('API не запустился')

_, registered = call('/api/auth/register', {'login': 'smoke_'+secrets.token_hex(4), 'password': secrets.token_urlsafe(24)})
headers['X-CSRF-Token'] = registered['csrfToken']
assert call('/api/auth/me')[0] == 200
headers['Idempotency-Key'] = secrets.token_urlsafe(20)
status, created = call('/api/profiles', {'version': 15, 'snapshot': {
    'profile': {'id': -1, 'petName': 'Тестовый Финни', 'petCharacter': 'PUSHISTIK',
                'bodyColor': 'A', 'eyes': 'ROUND', 'isDemo': False, 'createdAt': 1}}})
assert status == 201
status, profile = call('/api/profiles/'+str(created['id']))
assert profile['snapshot']['profile']['petName'] == 'Тестовый Финни'
assert call('/api/auth/logout', {})[0] == 200
print('HTTP smoke: health, регистрация, cookie/CSRF, создание и чтение профиля, выход — OK')
