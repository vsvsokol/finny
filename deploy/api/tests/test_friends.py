import sqlite3

from conftest import create_profile, new_client, signup
from server import create_app


def friends(client):
    response = client.get('/api/friends')
    assert response.status_code == 200, response.text
    return response.json()['friends']


def change(client, friendship_id, action):
    return client.post(f'/api/friends/{friendship_id}', json={'action': action})


def test_friend_requests_and_management(app, client):
    signup(client, 'sam')
    with new_client(app) as friend, new_client(app) as stranger:
        signup(friend, 'alex')
        assert stranger.get('/api/friends').status_code == 401
        signup(stranger, 'pat')
        assert client.post('/api/friends', json={'login': 'SAM'}).json()['error'] == 'friend_is_self'
        assert client.post('/api/friends', json={'login': 'missing'}).status_code == 404
        assert client.post('/api/friends', headers={'X-CSRF-Token': ''}, json={'login': 'alex'}).status_code == 403

        request = client.post('/api/friends', json={'login': 'ALEX'})
        assert request.status_code == 200
        friendship_id = request.json()['friends'][0]['id']
        outgoing = {'id': friendship_id, 'login': 'alex', 'status': 'outgoing'}
        incoming = {'id': friendship_id, 'login': 'sam', 'status': 'incoming'}
        assert client.post('/api/friends', json={'login': 'alex'}).json()['friends'] == [outgoing]
        assert friends(friend) == [incoming]
        assert friends(stranger) == []
        assert change(stranger, friendship_id, 'accept').status_code == 404
        assert change(client, friendship_id, 'accept').status_code == 409
        assert friend.post('/api/friends', json={'login': 'sam'}).json()['error'] == 'friend_request_incoming'

        assert change(friend, friendship_id, 'decline').json()['friends'] == []
        assert friends(client) == []
        client.post('/api/friends', json={'login': 'alex'}).raise_for_status()
        second_id = friends(client)[0]['id']
        assert change(friend, friendship_id, 'accept').status_code == 404
        assert change(client, second_id, 'cancel').json()['friends'] == []
        assert friends(friend) == []

        client.post('/api/friends', json={'login': 'alex'}).raise_for_status()
        accepted_id = friends(friend)[0]['id']
        assert change(friend, accepted_id, 'accept').status_code == 200
        assert friends(client) == [{'id': accepted_id, 'login': 'alex', 'status': 'friend'}]
        assert friends(friend) == [{'id': accepted_id, 'login': 'sam', 'status': 'friend'}]
        assert change(client, accepted_id, 'cancel').status_code == 409
        assert change(client, accepted_id, 'remove').json()['friends'] == []
        assert friends(friend) == []


def test_existing_database_migrates_without_losing_saves_or_sessions(app, client):
    signup(client)
    profile_id = create_profile(client)['id']
    with sqlite3.connect(app.state.settings.database) as db:
        db.execute('DROP TABLE friendships')

    upgraded = create_app(app.state.settings)
    with new_client(upgraded) as migrated_client:
        migrated_client.cookies.update(client.cookies)
        assert migrated_client.get('/api/health').json()['schemaVersion'] == 1
        assert migrated_client.get('/api/auth/me').json()['user']['login'] == 'player_one'
        assert migrated_client.get(f'/api/profiles/{profile_id}').json()['snapshot']['profile']['petName'] == 'Финни'
        assert friends(migrated_client) == []
