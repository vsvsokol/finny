import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pytest
from fastapi.testclient import TestClient
from server import create_app, Settings

ORIGIN = 'https://finny.mango-kokos.ru'
PASSWORD = 'test-password-for-finny'


@pytest.fixture
def app(tmp_path):
    return create_app(Settings(database=tmp_path/'finney.sqlite3', rate_multiplier=100))


@pytest.fixture
def client(app):
    with TestClient(app, base_url=ORIGIN, headers={'Origin': ORIGIN}) as client:
        yield client


def signup(client, login='player_one'):
    response = client.post('/api/auth/register', json={'login': login, 'password': PASSWORD})
    assert response.status_code == 201, response.text
    result = response.json()
    client.headers['X-CSRF-Token'] = result['csrfToken']
    return result


def new_client(app):
    return TestClient(app, base_url=ORIGIN, headers={'Origin': ORIGIN})


def snapshot(profile_id=-1):
    return {
        'profile': {'id': profile_id, 'petName': 'Финни', 'petCharacter': 'PUSHISTIK',
                    'bodyColor': 'A', 'eyes': 'ROUND', 'isDemo': False, 'createdAt': 12345},
        'pet': {'profileId': profile_id, 'satiety': 75, 'hygiene': 80, 'mood': 90, 'energy': 100},
        'periods': [{'profileId': profile_id, 'number': 1, 'stage': 1, 'phase': 'PLANNING'}],
        'ledger': [{'id': 1, 'profileId': profile_id, 'periodNumber': 1, 'type': 'INCOME',
                    'balanceDelta': 100, 'savingsDelta': 0, 'unplanned': False, 'createdAt': 12345}],
        'attempts': [], 'uses': [],
    }


def create_profile(client, key='create_profile_0001', data=None):
    response = client.post('/api/profiles', headers={'Idempotency-Key': key},
                           json={'version': 15, 'snapshot': data or snapshot()})
    assert response.status_code == 201, response.text
    return response.json()
