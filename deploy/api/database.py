"""SQLite: один атомарный коммит на изменение, ревизию и запись повторного запроса."""
from contextlib import contextmanager
from pathlib import Path
import sqlite3

SCHEMA = """
CREATE TABLE users (
 id INTEGER PRIMARY KEY AUTOINCREMENT, login TEXT NOT NULL UNIQUE,
 password_hash TEXT NOT NULL, recovery_hash TEXT NOT NULL, created_at INTEGER NOT NULL
);
CREATE TABLE sessions (
 token_hash TEXT PRIMARY KEY, user_id INTEGER NOT NULL REFERENCES users(id),
 expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL
);
CREATE INDEX sessions_user ON sessions(user_id);
CREATE TABLE profiles (
 id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL REFERENCES users(id),
 revision INTEGER NOT NULL, snapshot TEXT, deleted INTEGER NOT NULL DEFAULT 0,
 updated_at INTEGER NOT NULL
);
CREATE INDEX profiles_user ON profiles(user_id);
CREATE TABLE snapshots (
 profile_id INTEGER NOT NULL REFERENCES profiles(id), revision INTEGER NOT NULL,
 snapshot TEXT NOT NULL, created_at INTEGER NOT NULL,
 PRIMARY KEY(profile_id, revision)
);
CREATE TABLE mutations (
 user_id INTEGER NOT NULL REFERENCES users(id), key TEXT NOT NULL,
 request_hash TEXT NOT NULL, result TEXT NOT NULL, created_at INTEGER NOT NULL,
 PRIMARY KEY(user_id, key)
);
CREATE TABLE rate_limits (
 key TEXT PRIMARY KEY, count INTEGER NOT NULL, expires_at INTEGER NOT NULL
);
PRAGMA user_version=1;
"""

# The previous API only opens schema version 1. This additive migration leaves
# that version in place so deployment can roll back without restoring a database.
FRIENDS_SCHEMA = """
CREATE TABLE IF NOT EXISTS friendships (
 id INTEGER PRIMARY KEY AUTOINCREMENT,
 sender_id INTEGER NOT NULL REFERENCES users(id),
 recipient_id INTEGER NOT NULL REFERENCES users(id),
 accepted INTEGER NOT NULL DEFAULT 0 CHECK(accepted IN (0, 1)),
 CHECK(sender_id <> recipient_id)
);
CREATE UNIQUE INDEX IF NOT EXISTS friendships_pair ON friendships(
 min(sender_id, recipient_id), max(sender_id, recipient_id)
);
CREATE INDEX IF NOT EXISTS friendships_sender ON friendships(sender_id);
CREATE INDEX IF NOT EXISTS friendships_recipient ON friendships(recipient_id);
"""


class Database:
    def __init__(self, path: Path):
        self.path = path
        path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        db = sqlite3.connect(path, timeout=10, isolation_level=None)
        try:
            db.execute("PRAGMA journal_mode=WAL")
            db.execute("BEGIN IMMEDIATE")
            version = db.execute("PRAGMA user_version").fetchone()[0]
            if version not in (0, 1):
                raise RuntimeError("Неизвестная версия базы; нужна совместимая версия API")
            migration = SCHEMA + FRIENDS_SCHEMA if version == 0 else FRIENDS_SCHEMA
            for statement in migration.split(";"):
                if statement.strip():
                    db.execute(statement)
            db.commit()
        finally:
            db.close()
        path.chmod(0o600)

    @contextmanager
    def connect(self, *, write=False):
        db = sqlite3.connect(self.path, timeout=10, isolation_level=None)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA synchronous=FULL")
        try:
            db.execute("BEGIN IMMEDIATE" if write else "BEGIN")
            yield db
            db.commit()
        except BaseException:
            db.rollback()
            raise
        finally:
            db.close()
