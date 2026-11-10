from concurrent.futures import ThreadPoolExecutor
from copy import deepcopy
import json
import time

import pytest
from fastapi.testclient import TestClient
from conftest import ORIGIN, PASSWORD, signup, new_client, snapshot, create_profile
from server import create_app, Settings, digest


def put(client, profile, *, revision=None, data=None, key='save_profile_00001', mode='append'):
    profile_id = profile['id']
    saved = data or client.get(f'/api/profiles/{profile_id}').json()['snapshot']
    return client.put(f'/api/profiles/{profile_id}', headers={'Idempotency-Key': key}, json={
        'version': 15, 'snapshot': saved, 'expectedRevision': revision or profile['revision'], 'mode': mode})


def test_registration_session_and_logout(client, app):
    registered = signup(client, 'PLAYER_One')
    assert registered['user']['login'] == 'player_one'
    response = client.get('/api/auth/me')
    assert response.json()['csrfToken'] == registered['csrfToken']
    assert response.headers['Cache-Control'] == 'no-store'
    raw = client.cookies.get('__Host-finny_session')
    with app.state.database.connect() as conn:
        user = conn.execute('SELECT * FROM users').fetchone()
        session = conn.execute('SELECT * FROM sessions').fetchone()
        assert user['password_hash'].startswith('$argon2id$')
        assert user['recovery_hash'] == digest(registered['recoveryCode'])
        assert session['token_hash'] == digest(raw)
        assert PASSWORD not in user['password_hash']
    assert client.post('/api/auth/logout', json={}).status_code == 200
    client.cookies.set('__Host-finny_session', raw)
    assert client.get('/api/auth/me').status_code == 401


def test_cookie_flags(client):
    response = client.post('/api/auth/register', json={'login': 'player', 'password': PASSWORD})
    cookie = response.headers['set-cookie'].lower()
    for flag in ('__host-finny_session=', 'secure', 'httponly', 'samesite=strict', 'path=/'):
        assert flag in cookie
    assert 'domain=' not in cookie


def test_duplicate_login_case_insensitive(client):
    signup(client, 'Player')
    assert client.post('/api/auth/register', json={'login': 'PLAYER', 'password': PASSWORD}).status_code == 409


def test_login_and_generic_failure(client):
    signup(client)
    client.cookies.clear()
    missing = client.post('/api/auth/login', json={'login': 'absent', 'password': PASSWORD})
    wrong = client.post('/api/auth/login', json={'login': 'player_one', 'password': 'wrong-pass-123'})
    assert missing.status_code == wrong.status_code == 401
    assert missing.json() == wrong.json() == {'error': 'invalid_credentials'}
    assert client.post('/api/auth/login', json={'login': 'PLAYER_ONE', 'password': PASSWORD}).status_code == 200


def test_expired_or_forged_session(client, app):
    signup(client)
    with app.state.database.connect(write=True) as conn:
        conn.execute('UPDATE sessions SET expires_at=?', (int(time.time())-1,))
    assert client.get('/api/auth/me').status_code == 401
    client.cookies.clear()
    client.cookies.set('__Host-finny_session', 'forged')
    assert client.get('/api/auth/me').status_code == 401


def test_password_change_revokes_all_sessions_and_rotates_recovery(client, app):
    registered = signup(client)
    old_token = client.cookies.get('__Host-finny_session')
    other = new_client(app)
    assert other.post('/api/auth/login', json={'login': 'player_one', 'password': PASSWORD}).status_code == 200
    response = client.post('/api/auth/password', json={'currentPassword': PASSWORD, 'newPassword': 'new-long-password'})
    assert response.status_code == 200
    assert response.json()['recoveryCode'] != registered['recoveryCode']
    assert other.get('/api/auth/me').status_code == 401
    assert client.get('/api/auth/me').status_code == 200
    client.cookies.clear()
    client.cookies.set('__Host-finny_session', old_token)
    assert client.get('/api/auth/me').status_code == 401
    assert other.post('/api/auth/login', json={'login': 'player_one', 'password': PASSWORD}).status_code == 401
    assert other.post('/api/auth/login', json={'login': 'player_one', 'password': 'new-long-password'}).status_code == 200


def test_wrong_current_password(client):
    signup(client)
    assert client.post('/api/auth/password', json={'currentPassword': 'incorrect', 'newPassword': 'new-long-password'}).status_code == 401
    assert client.post('/api/auth/recovery-code', json={'password': 'incorrect'}).status_code == 401


def test_recovery_once_revokes_sessions_and_rotates_code(client, app):
    registered = signup(client)
    other = new_client(app)
    payload = {'login': 'PLAYER_ONE', 'recoveryCode': registered['recoveryCode'], 'newPassword': 'recovered-password'}
    response = other.post('/api/auth/recover', json=payload)
    assert response.status_code == 200
    assert response.json()['recoveryCode'] != registered['recoveryCode']
    assert client.get('/api/auth/me').status_code == 401
    assert other.get('/api/auth/me').status_code == 200
    assert other.post('/api/auth/recover', json=payload).status_code == 401


def test_recovery_rotation_invalidates_previous(client):
    registered = signup(client)
    response = client.post('/api/auth/recovery-code', json={'password': PASSWORD})
    assert response.status_code == 200
    assert client.post('/api/auth/recover', json={'login': 'player_one', 'recoveryCode': registered['recoveryCode'], 'newPassword': PASSWORD}).status_code == 401
    assert client.post('/api/auth/recover', json={'login': 'player_one', 'recoveryCode': response.json()['recoveryCode'], 'newPassword': PASSWORD}).status_code == 200


@pytest.mark.parametrize('origin', ['', 'https://evil.example', 'https://finny.mango-kokos.ru.evil.example', 'null'])
def test_origin_required(client, origin):
    response = client.post('/api/auth/register', headers={'Origin': origin}, json={'login': 'player', 'password': PASSWORD})
    assert response.status_code == 403


def test_csrf_and_json_required(client):
    signup(client)
    client.headers.pop('X-CSRF-Token')
    assert client.post('/api/auth/logout', json={}).status_code == 403
    assert client.post('/api/profiles', json={'version': 15, 'snapshot': snapshot()}).status_code == 403
    assert client.post('/api/auth/register', content='{}', headers={'Content-Type': 'text/plain'}).status_code == 415
    assert client.get('/api/auth/me', headers={'Host': 'evil.example'}).status_code == 400


@pytest.mark.parametrize('body', [
    {'login': 'bad login', 'password': PASSWORD},
    {'login': 'player', 'password': 'short'},
    {'login': 'player', 'password': PASSWORD, 'extra': 'private'},
])
def test_validation_never_echoes_credentials(client, body):
    response = client.post('/api/auth/register', json=body)
    assert response.status_code == 422
    assert response.json() == {'error': 'invalid_request'}
    assert body['password'] not in response.text


def test_profile_normalizes_local_ids_and_roundtrips(client):
    signup(client)
    created = create_profile(client)
    profile_id = created['id']
    assert profile_id > 0 and created['revision'] == 1
    saved = client.get(f'/api/profiles/{profile_id}').json()
    assert saved['snapshot']['profile']['petName'] == 'Финни'
    assert saved['snapshot']['profile']['id'] == saved['snapshot']['pet']['profileId'] == profile_id
    assert saved['snapshot']['ledger'][0]['profileId'] == profile_id
    assert saved['snapshot']['periods'][0]['profileId'] == profile_id
    assert len(client.get('/api/profiles').json()['profiles']) == 1


def test_ownership_enforced_for_all_profile_operations(client, app):
    signup(client)
    created = create_profile(client)
    other = new_client(app)
    signup(other, 'second_player')
    assert other.get('/api/profiles').json()['profiles'] == []
    assert other.get(f"/api/profiles/{created['id']}").status_code == 404
    assert put(other, created, data=snapshot(created['id'])).status_code == 404
    assert other.request('DELETE', f"/api/profiles/{created['id']}", headers={'Idempotency-Key': 'delete_foreign_001'}, json={'expectedRevision': 1}).status_code == 404


def test_missing_auth(client):
    assert client.get('/api/profiles').status_code == 401
    assert client.post('/api/profiles', json={'version': 15, 'snapshot': snapshot()}).status_code == 401


def test_profile_append_revision_and_stale_write_conflict(client):
    signup(client)
    created = create_profile(client)
    data = client.get(f"/api/profiles/{created['id']}").json()['snapshot']
    data['profile']['petName'] = 'Новый Финни'
    assert put(client, created, data=data).json()['revision'] == 2
    stale = put(client, created, data=data, key='stale_write_00001')
    assert stale.status_code == 409
    assert stale.json()['error']['current']['revision'] == 2
    assert stale.json()['error']['current']['snapshot']['profile']['petName'] == 'Новый Финни'


def test_idempotent_create_save_and_delete(client, app):
    signup(client)
    created = create_profile(client)
    assert create_profile(client) == created
    first = put(client, created)
    repeated = put(client, created)
    assert first.status_code == repeated.status_code == 200
    assert first.json() == repeated.json()
    profile_id = created['id']
    headers = {'Idempotency-Key': 'delete_profile_0001'}
    first = client.request('DELETE', f'/api/profiles/{profile_id}', headers=headers, json={'expectedRevision': 2})
    replay = client.request('DELETE', f'/api/profiles/{profile_id}', headers=headers, json={'expectedRevision': 2})
    assert first.status_code == replay.status_code == 200
    assert first.json() == replay.json() and first.json()['deleted'] is True
    assert first.json()['revision'] == 3
    with app.state.database.connect() as conn:
        assert conn.execute('SELECT count(*) FROM profiles').fetchone()[0] == 1


def test_idempotency_key_cannot_be_reused_for_different_body_or_route(client):
    signup(client)
    created = create_profile(client)
    data = snapshot()
    data['profile']['petName'] = 'Другой'
    response = client.post('/api/profiles', headers={'Idempotency-Key': 'create_profile_0001'}, json={'version': 15, 'snapshot': data})
    assert response.status_code == 409
    assert put(client, created, key='create_profile_0001').status_code == 409
    assert client.post('/api/profiles', json={'version': 15, 'snapshot': data}).status_code == 400


def test_journals_cannot_shrink_or_change_except_replace(client):
    signup(client)
    created = create_profile(client)
    data = client.get(f"/api/profiles/{created['id']}").json()['snapshot']
    changed = deepcopy(data)
    changed['ledger'][0]['balanceDelta'] = 500
    assert put(client, created, data=changed).status_code == 409
    data['ledger'] = []
    assert put(client, created, data=data).status_code == 409
    assert put(client, created, data=data, mode='replace').status_code == 200


def test_append_journals(client):
    signup(client)
    created = create_profile(client)
    data = client.get(f"/api/profiles/{created['id']}").json()['snapshot']
    entry = deepcopy(data['ledger'][0])
    entry.update(id=2, type='PURCHASE', balanceDelta=-10)
    data['ledger'].append(entry)
    assert put(client, created, data=data).status_code == 200
    assert len(client.get(f"/api/profiles/{created['id']}").json()['snapshot']['ledger']) == 2


def test_deleted_profile_cannot_resurrect(client):
    signup(client)
    created = create_profile(client)
    data = client.get(f"/api/profiles/{created['id']}").json()['snapshot']
    url = f"/api/profiles/{created['id']}"
    deleted = client.request('DELETE', url, headers={'Idempotency-Key': 'delete_profile_0001'}, json={'expectedRevision': 1})
    assert deleted.status_code == 200
    assert client.get(url).json()['snapshot'] is None
    stale = put(client, created, data=data, revision=2, mode='replace')
    assert stale.status_code == 409
    assert stale.json()['error']['current']['deleted'] is True
    assert client.get('/api/profiles').json()['profiles'][0]['deleted'] is True


@pytest.mark.parametrize('change', ['demo', 'reference', 'duplicate', 'enum', 'unknown', 'scale'])
def test_snapshot_validation(client, change):
    signup(client)
    data = snapshot()
    if change == 'demo': data['profile']['isDemo'] = True
    if change == 'reference': data['pet']['profileId'] = 99
    if change == 'duplicate': data['ledger'].append(deepcopy(data['ledger'][0]))
    if change == 'enum': data['profile']['eyes'] = 'UNKNOWN'
    if change == 'unknown': data['private'] = 'unexpected'
    if change == 'scale': data['pet']['satiety'] = 101
    response = client.post('/api/profiles', headers={'Idempotency-Key': 'invalid_create_001'}, json={'version': 15, 'snapshot': data})
    assert response.status_code == 422


def test_incompatible_save_format_rejected(client):
    signup(client)
    assert client.post('/api/profiles', headers={'Idempotency-Key': 'invalid_version_01'}, json={'version': 16, 'snapshot': snapshot()}).status_code == 422


def test_huge_path_id_never_overflows_sqlite(client):
    signup(client)
    assert client.get('/api/profiles/'+'9'*100).status_code == 404


def test_profile_limit_and_replacement_after_delete(tmp_path):
    app = create_app(Settings(database=tmp_path/'limit.sqlite3', max_profiles=1))
    with new_client(app) as client:
        signup(client)
        created = create_profile(client)
        response = client.post('/api/profiles', headers={'Idempotency-Key': 'second_profile_001'}, json={'version': 15, 'snapshot': snapshot()})
        assert response.status_code == 409 and response.json()['error'] == 'profile_limit'
        assert client.request('DELETE', f"/api/profiles/{created['id']}", headers={'Idempotency-Key': 'delete_profile_0001'}, json={'expectedRevision': 1}).status_code == 200
        assert create_profile(client, key='second_profile_001')['id'] != created['id']


def test_auth_rate_limit_persists_across_app_restart(tmp_path):
    settings = Settings(database=tmp_path/'rate.sqlite3')
    app = create_app(settings)
    with new_client(app) as client:
        for _ in range(10):
            assert client.post('/api/auth/login', json={'login': 'absent', 'password': PASSWORD}).status_code == 401
        response = client.post('/api/auth/login', json={'login': 'absent', 'password': PASSWORD})
        assert response.status_code == 429 and response.headers['Retry-After']
    with new_client(create_app(settings)) as client:
        assert client.post('/api/auth/login', json={'login': 'absent', 'password': PASSWORD}).status_code == 429


def test_declared_and_chunked_body_limits(client):
    assert client.post('/api/auth/login', content='x'*20_000, headers={'Content-Type': 'application/json'}).status_code == 413
    def chunks():
        for _ in range(3):
            yield b'x'*8_000
    response = client.post('/api/auth/login', content=chunks(), headers={'Content-Type': 'application/json'})
    assert response.status_code == 413
    assert client.post('/api/auth/login', content='{invalid', headers={'Content-Type': 'application/json'}).status_code == 422


def test_concurrent_writes_accept_exactly_one_revision(client, app):
    signup(client)
    created = create_profile(client)
    data = client.get(f"/api/profiles/{created['id']}").json()['snapshot']
    cookies = dict(client.cookies)
    csrf = client.headers['X-CSRF-Token']
    def save(index):
        with new_client(app) as other:
            other.cookies.update(cookies)
            other.headers['X-CSRF-Token'] = csrf
            changed = deepcopy(data)
            changed['profile']['petName'] = f'Финни {index}'
            return put(other, created, data=changed, key=f'concurrent_write_{index}').status_code
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(save, [1, 2]))
    assert sorted(results) == [200, 409]
    assert client.get(f"/api/profiles/{created['id']}").json()['revision'] == 2


def test_history_bounded_and_database_survives_restart(client, app):
    signup(client)
    created = create_profile(client)
    for index in range(8):
        result = put(client, created, revision=index+1, key=f'history_save_{index:05}')
        assert result.status_code == 200
    with app.state.database.connect() as conn:
        assert conn.execute('SELECT count(*) FROM snapshots').fetchone()[0] == 5
        assert conn.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
    with new_client(create_app(app.state.settings)) as other:
        other.cookies.update(dict(client.cookies))
        assert other.get('/api/profiles').json()['profiles'][0]['revision'] == 9


def test_production_refuses_insecure_or_ambiguous_origin(tmp_path):
    for origin in ('http://finny.mango-kokos.ru', 'https://finny.mango-kokos.ru/', 'https://user@finny.mango-kokos.ru'):
        with pytest.raises(ValueError):
            create_app(Settings(database=tmp_path/'bad.sqlite3', origin=origin))


def test_live_backup_restore_including_login_and_progress(client, app, tmp_path):
    from backup import backup
    signup(client)
    created = create_profile(client)
    destination = tmp_path/'backups'/'restored.sqlite3'
    backup(app.state.settings.database, destination)
    restored = create_app(Settings(database=destination))
    with new_client(restored) as other:
        assert other.post('/api/auth/login', json={'login': 'player_one', 'password': PASSWORD}).status_code == 200
        saved = other.get(f"/api/profiles/{created['id']}").json()
        assert saved['snapshot']['profile']['petName'] == 'Финни'
        assert saved['snapshot']['ledger'][0]['balanceDelta'] == 100
    with pytest.raises(FileExistsError):
        backup(app.state.settings.database, destination)
    with pytest.raises(ValueError):
        backup(app.state.settings.database, app.state.settings.database)


def test_unknown_database_version_refused(tmp_path):
    import sqlite3
    path = tmp_path/'future.sqlite3'
    with sqlite3.connect(path) as db:
        db.execute('PRAGMA user_version=99')
    with pytest.raises(RuntimeError):
        create_app(Settings(database=path))


def test_non_ascii_csrf_rejected_without_server_error(client):
    signup(client)
    response = client.post('/api/auth/logout', json={}, headers=[(b'X-CSRF-Token', b'\xff')])
    assert response.status_code == 403


def test_expected_account_blocks_stale_tab_reads_and_writes(client):
    registered = signup(client)
    headers = {'X-Account-Id': str(registered['user']['id']+1)}
    assert client.get('/api/profiles', headers=headers).status_code == 409
    assert client.post('/api/profiles', headers={**headers, 'Idempotency-Key': 'wrong_account_001'}, json={'version': 15, 'snapshot': snapshot()}).status_code == 409
    own = client.get('/api/profiles', headers={'X-Account-Id': str(registered['user']['id'])})
    assert own.json()['ownerId'] == registered['user']['id']
