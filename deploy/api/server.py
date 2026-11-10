"""API учётных записей и профилей Финни. Публичный вход — через HTTPS reverse proxy."""
import asyncio
from contextlib import contextmanager
from dataclasses import dataclass
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import sqlite3
from threading import BoundedSemaphore
import time
from urllib.parse import urlsplit

from argon2 import PasswordHasher
from argon2.exceptions import VerificationError, InvalidHashError
from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.middleware.trustedhost import TrustedHostMiddleware

from database import Database
from models import Credentials, PasswordChange, Recovery, ConfirmPassword, CreateProfile, SaveProfile, DeleteProfile


def digest(text):
    return hashlib.sha256(text.encode()).hexdigest()


def canonical(data):
    return json.dumps(data, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False)


@dataclass(frozen=True)
class Settings:
    database: Path
    origin: str = "https://finny.mango-kokos.ru"
    secure_cookie: bool = True
    session_seconds: int = 7 * 24 * 3600
    max_profiles: int = 10
    max_body: int = 2 * 1024 * 1024
    rate_multiplier: int = 1

    @classmethod
    def from_env(cls):
        return cls(database=Path(os.environ.get("FINNY_DATABASE", "/data/finney.sqlite3")),
                   origin=os.environ.get("FINNY_ORIGIN", "https://finny.mango-kokos.ru"),
                   secure_cookie=os.environ.get("FINNY_DEV_HTTP") != "1")


class RequestGuard:
    """Ограничивает фактический поток тела, включая запросы без Content-Length."""
    def __init__(self, app, origin, limit):
        self.app, self.origin, self.limit = app, origin, limit

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        headers = {k.lower(): v for k, v in scope["headers"]}
        async def reject(status, code):
            await JSONResponse({"error": code}, status_code=status,
                               headers={"Cache-Control": "no-store"})(scope, receive, send)
        if scope["method"] not in ("GET", "HEAD", "OPTIONS"):
            if headers.get(b"origin", b"").decode("latin1") != self.origin:
                return await reject(403, "origin_required")
            if headers.get(b"content-type", b"").split(b";", 1)[0].strip().lower() != b"application/json":
                return await reject(415, "json_required")
        limit = min(self.limit, 16_384) if scope["path"].startswith("/api/auth/") else self.limit
        try:
            length = int(headers.get(b"content-length", b"0"))
        except ValueError:
            return await reject(400, "invalid_content_length")
        if length < 0:
            return await reject(400, "invalid_content_length")
        if length > limit:
            return await reject(413, "body_too_large")
        chunks, size = [], 0
        async def read():
            nonlocal size
            while True:
                message = await receive()
                if message["type"] == "http.disconnect":
                    return False
                chunk = message.get("body", b"")
                size += len(chunk)
                if size > limit:
                    return False
                chunks.append(chunk)
                if not message.get("more_body", False):
                    return True
        try:
            complete = await asyncio.wait_for(read(), timeout=15)
        except TimeoutError:
            return await reject(408, "body_timeout")
        if not complete:
            return await reject(413 if size > limit else 400, "body_incomplete")
        body = b"".join(chunks)
        delivered = False
        async def replay():
            nonlocal delivered
            if not delivered:
                delivered = True
                return {"type": "http.request", "body": body}
            return await receive()
        await self.app(scope, replay, send)


def create_app(settings: Settings | None = None):
    settings = settings or Settings.from_env()
    origin_url = urlsplit(settings.origin)
    if (not origin_url.hostname or origin_url.path or origin_url.query or origin_url.fragment
            or origin_url.username or origin_url.scheme not in ("https", "http")
            or (settings.secure_cookie and origin_url.scheme != "https")
            or (not settings.secure_cookie and origin_url.hostname not in ("localhost", "127.0.0.1"))):
        raise ValueError("Нужен HTTPS origin; HTTP допускается только для localhost")
    db = Database(settings.database)
    hasher = PasswordHasher(time_cost=3, memory_cost=65536, parallelism=1)
    hash_slots = BoundedSemaphore(2)
    dummy_hash = hasher.hash(secrets.token_urlsafe(32))
    cookie_name = "__Host-finny_session" if settings.secure_cookie else "finny_session"
    app = FastAPI(title="Финни API", docs_url=None, redoc_url=None, openapi_url=None)
    app.state.database = db
    app.state.settings = settings

    @app.exception_handler(RequestValidationError)
    async def invalid_body(request, exc):
        # Стандартный ответ Pydantic может вернуть пароль в поле input.
        return JSONResponse({"error": "invalid_request"}, status_code=422)

    @app.exception_handler(HTTPException)
    async def http_error(request, exc):
        return JSONResponse({"error": exc.detail}, status_code=exc.status_code, headers=exc.headers)

    @app.middleware("http")
    async def private_responses(request, call_next):
        response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        return response

    app.add_middleware(RequestGuard, origin=settings.origin, limit=settings.max_body)
    app.add_middleware(TrustedHostMiddleware, allowed_hosts=[origin_url.hostname])

    def now():
        return int(time.time())

    def rate(request, scope, limit, seconds, identity=None):
        # Не доверяем X-Forwarded-For: peer IP устанавливает Uvicorn только от доверенного nginx.
        subject = identity if identity is not None else (request.client.host if request.client else "unknown")
        key = digest(scope + ":" + subject)
        instant = now()
        blocked = False
        with db.connect(write=True) as conn:
            conn.execute("DELETE FROM rate_limits WHERE expires_at <= ?", (instant,))
            row = conn.execute("SELECT * FROM rate_limits WHERE key=?", (key,)).fetchone()
            if row and row["count"] >= limit * settings.rate_multiplier:
                blocked = True
            elif row:
                conn.execute("UPDATE rate_limits SET count=count+1 WHERE key=?", (key,))
            else:
                conn.execute("INSERT INTO rate_limits VALUES (?,1,?)", (key, instant + seconds))
        if blocked:
            raise HTTPException(429, "too_many_requests", headers={"Retry-After": str(seconds)})

    @contextmanager
    def hashing():
        if not hash_slots.acquire(blocking=False):
            raise HTTPException(429, "auth_busy", headers={"Retry-After": "2"})
        try:
            yield
        finally:
            hash_slots.release()

    def verify(encoded, password):
        try:
            return hasher.verify(encoded, password)
        except (VerificationError, InvalidHashError):
            return False

    def session(conn, request, *, csrf=False):
        token = request.cookies.get(cookie_name, "")
        if not re.fullmatch(r"[A-Za-z0-9_-]{43}", token):
            raise HTTPException(401, "login_required")
        row = conn.execute("""SELECT users.id, users.login, users.password_hash, sessions.token_hash
            FROM sessions JOIN users ON users.id=sessions.user_id
            WHERE token_hash=? AND expires_at>?""", (digest(token), now())).fetchone()
        if row is None:
            raise HTTPException(401, "login_required")
        expected_owner = request.headers.get("X-Account-Id")
        if expected_owner is not None and expected_owner != str(row["id"]):
            raise HTTPException(409, "account_changed")
        if csrf and not secrets.compare_digest(request.headers.get("X-CSRF-Token", "").encode(), digest("finny-csrf:" + token).encode()):
            raise HTTPException(403, "csrf_required")
        return row

    def issue_session(conn, response, user):
        token = secrets.token_urlsafe(32)
        conn.execute("DELETE FROM sessions WHERE expires_at <= ?", (now(),))
        conn.execute("""DELETE FROM sessions WHERE token_hash IN
            (SELECT token_hash FROM sessions WHERE user_id=? ORDER BY created_at DESC LIMIT -1 OFFSET 9)""", (user["id"],))
        conn.execute("INSERT INTO sessions VALUES (?,?,?,?)", (digest(token), user["id"], now()+settings.session_seconds, now()))
        response.set_cookie(cookie_name, token, max_age=settings.session_seconds, secure=settings.secure_cookie,
                            httponly=True, samesite="strict", path="/")
        return {"user": {"id": user["id"], "login": user["login"]}, "csrfToken": digest("finny-csrf:"+token)}

    def auth_limits(request, login, kind="login"):
        rate(request, "auth-ip", 60, 900)
        rate(request, "auth-"+kind, 10, 900, identity=login.lower())

    @app.get("/api/health")
    def health():
        with db.connect() as conn:
            conn.execute("SELECT 1").fetchone()
        return {"status": "ok", "schemaVersion": 1, "saveVersion": 15}

    @app.post("/api/auth/register", status_code=201)
    def register(data: Credentials, request: Request, response: Response):
        login = data.login.lower()
        rate(request, "register-ip", 5, 3600)
        rate(request, "register-global", 200, 86400, identity="all")
        recovery = secrets.token_urlsafe(24)
        with hashing():
            encoded = hasher.hash(data.password)
        with db.connect(write=True) as conn:
            if conn.execute("SELECT count(*) FROM users").fetchone()[0] >= 10_000:
                raise HTTPException(503, "registration_full")
            try:
                cur = conn.execute("INSERT INTO users(login,password_hash,recovery_hash,created_at) VALUES (?,?,?,?)",
                                   (login, encoded, digest(recovery), now()))
            except sqlite3.IntegrityError:
                raise HTTPException(409, "login_unavailable") from None
            result = issue_session(conn, response, {"id": cur.lastrowid, "login": login})
        return {**result, "recoveryCode": recovery}

    @app.post("/api/auth/login")
    def login(data: Credentials, request: Request, response: Response):
        auth_limits(request, data.login)
        with db.connect() as conn:
            user = conn.execute("SELECT * FROM users WHERE login=?", (data.login.lower(),)).fetchone()
        with hashing():
            valid = verify(user["password_hash"] if user else dummy_hash, data.password)
            replacement = hasher.hash(data.password) if valid and user and hasher.check_needs_rehash(user["password_hash"]) else None
        if not user or not valid:
            raise HTTPException(401, "invalid_credentials")
        with db.connect(write=True) as conn:
            current = conn.execute("SELECT * FROM users WHERE id=?", (user["id"],)).fetchone()
            if current["password_hash"] != user["password_hash"]:
                raise HTTPException(401, "invalid_credentials")
            if replacement:
                conn.execute("UPDATE users SET password_hash=? WHERE id=?", (replacement, user["id"]))
            return issue_session(conn, response, user)

    @app.get("/api/auth/me")
    def me(request: Request):
        with db.connect() as conn:
            user = session(conn, request)
        return {"user": {"id": user["id"], "login": user["login"]},
                "csrfToken": digest("finny-csrf:" + request.cookies[cookie_name])}

    @app.post("/api/auth/logout")
    def logout(request: Request, response: Response):
        with db.connect(write=True) as conn:
            user = session(conn, request, csrf=True)
            conn.execute("DELETE FROM sessions WHERE token_hash=?", (user["token_hash"],))
        response.delete_cookie(cookie_name, path="/", secure=settings.secure_cookie, httponly=True, samesite="strict")
        return {"ok": True}

    def confirmed_user(request, password):
        with db.connect() as conn:
            user = session(conn, request, csrf=True)
        auth_limits(request, user["login"], "confirm")
        with hashing():
            valid = verify(user["password_hash"], password)
        if not valid:
            raise HTTPException(401, "invalid_credentials")
        return user

    def unchanged_user(conn, request, before):
        user = session(conn, request, csrf=True)
        if user["password_hash"] != before["password_hash"]:
            raise HTTPException(401, "login_required")
        return user

    @app.post("/api/auth/password")
    def change_password(data: PasswordChange, request: Request, response: Response):
        before = confirmed_user(request, data.currentPassword)
        with hashing():
            encoded = hasher.hash(data.newPassword)
        recovery = secrets.token_urlsafe(24)
        with db.connect(write=True) as conn:
            user = unchanged_user(conn, request, before)
            conn.execute("UPDATE users SET password_hash=?, recovery_hash=? WHERE id=?", (encoded, digest(recovery), user["id"]))
            conn.execute("DELETE FROM sessions WHERE user_id=?", (user["id"],))
            result = issue_session(conn, response, user)
        return {**result, "recoveryCode": recovery}

    @app.post("/api/auth/recovery-code")
    def rotate_recovery(data: ConfirmPassword, request: Request):
        before = confirmed_user(request, data.password)
        recovery = secrets.token_urlsafe(24)
        with db.connect(write=True) as conn:
            user = unchanged_user(conn, request, before)
            conn.execute("UPDATE users SET recovery_hash=? WHERE id=?", (digest(recovery), user["id"]))
        return {"recoveryCode": recovery}

    @app.post("/api/auth/recover")
    def recover(data: Recovery, request: Request, response: Response):
        auth_limits(request, data.login, "recover")
        recovery = secrets.token_urlsafe(24)
        # Даже неверный код выполняет хеширование: ответ не раскрывает существование логина.
        with hashing():
            encoded = hasher.hash(data.newPassword)
        with db.connect(write=True) as conn:
            user = conn.execute("SELECT * FROM users WHERE login=?", (data.login.lower(),)).fetchone()
            if not secrets.compare_digest(user["recovery_hash"] if user else "0"*64, digest(data.recoveryCode)):
                raise HTTPException(401, "invalid_credentials")
            conn.execute("UPDATE users SET password_hash=?, recovery_hash=? WHERE id=?", (encoded, digest(recovery), user["id"]))
            conn.execute("DELETE FROM sessions WHERE user_id=?", (user["id"],))
            result = issue_session(conn, response, user)
        return {**result, "recoveryCode": recovery}

    def profile_row(conn, user, profile_id):
        if not 0 < profile_id <= 9_007_199_254_740_991:
            raise HTTPException(404, "profile_not_found")
        row = conn.execute("SELECT * FROM profiles WHERE id=? AND user_id=?", (profile_id, user["id"])).fetchone()
        if row is None:
            raise HTTPException(404, "profile_not_found")
        return row

    def metadata(row):
        return {"id": row["id"], "revision": row["revision"], "deleted": bool(row["deleted"]), "updatedAt": row["updated_at"]}

    def envelope(row):
        return {**metadata(row), "version": 15, "snapshot": json.loads(row["snapshot"]) if row["snapshot"] else None}

    def mutation(conn, user, request, operation, body):
        key = request.headers.get("Idempotency-Key", "")
        if not re.fullmatch(r"[A-Za-z0-9_-]{16,80}", key):
            raise HTTPException(400, "idempotency_key_required")
        fingerprint = digest(operation + ":" + canonical(body.model_dump()))
        previous = conn.execute("SELECT * FROM mutations WHERE user_id=? AND key=?", (user["id"], key)).fetchone()
        if previous:
            if previous["request_hash"] != fingerprint:
                raise HTTPException(409, "idempotency_key_reused")
            return key, fingerprint, json.loads(previous["result"])
        return key, fingerprint, None

    def record(conn, user, key, fingerprint, row):
        result = metadata(row)
        conn.execute("INSERT INTO mutations VALUES (?,?,?,?,?)", (user["id"], key, fingerprint, canonical(result), now()))
        return result

    def store_history(conn, row):
        conn.execute("INSERT INTO snapshots VALUES (?,?,?,?)", (row["id"], row["revision"], row["snapshot"], now()))
        conn.execute("DELETE FROM snapshots WHERE profile_id=? AND revision NOT IN (SELECT revision FROM snapshots WHERE profile_id=? ORDER BY revision DESC LIMIT 5)", (row["id"], row["id"]))

    def profile_write_limit(request):
        with db.connect() as conn:
            user = session(conn, request, csrf=True)
        rate(request, "profile-write", 120, 60, identity=str(user["id"]))

    @app.get("/api/profiles")
    def profiles(request: Request):
        with db.connect() as conn:
            user = session(conn, request)
            rows = conn.execute("SELECT * FROM profiles WHERE user_id=? ORDER BY id", (user["id"],)).fetchall()
            return {"version": 15, "ownerId": user["id"], "profiles": [envelope(row) for row in rows]}

    @app.get("/api/profiles/{profile_id}")
    def get_profile(profile_id: int, request: Request):
        if not 0 < profile_id <= 9_007_199_254_740_991:
            raise HTTPException(404, "profile_not_found")
        with db.connect() as conn:
            user = session(conn, request)
            return envelope(profile_row(conn, user, profile_id))

    @app.post("/api/profiles", status_code=201)
    def create_profile(data: CreateProfile, request: Request):
        profile_write_limit(request)
        with db.connect(write=True) as conn:
            user = session(conn, request, csrf=True)
            key, fingerprint, replay = mutation(conn, user, request, "create", data)
            if replay is not None:
                return replay
            if conn.execute("SELECT count(*) FROM profiles WHERE user_id=? AND deleted=0", (user["id"],)).fetchone()[0] >= settings.max_profiles:
                raise HTTPException(409, "profile_limit")
            if conn.execute("SELECT count(*) FROM profiles WHERE user_id=?", (user["id"],)).fetchone()[0] >= 200:
                # Tombstone нужны старым устройствам; ограничиваем создание/удаление по кругу.
                raise HTTPException(409, "profile_history_limit")
            cur = conn.execute("INSERT INTO profiles(user_id,revision,updated_at) VALUES (?,1,?)", (user["id"], now()))
            profile_id = cur.lastrowid
            snapshot = canonical(data.snapshot.with_id(profile_id))
            conn.execute("UPDATE profiles SET snapshot=? WHERE id=?", (snapshot, profile_id))
            row = profile_row(conn, user, profile_id)
            store_history(conn, row)
            return record(conn, user, key, fingerprint, row)

    @app.put("/api/profiles/{profile_id}")
    def save_profile(profile_id: int, data: SaveProfile, request: Request):
        profile_write_limit(request)
        with db.connect(write=True) as conn:
            user = session(conn, request, csrf=True)
            key, fingerprint, replay = mutation(conn, user, request, "save:"+str(profile_id), data)
            if replay is not None:
                return replay
            row = profile_row(conn, user, profile_id)
            if row["revision"] != data.expectedRevision or row["deleted"]:
                raise HTTPException(409, {"code": "revision_conflict", "current": envelope(row)})
            if data.snapshot.profile.id != profile_id:
                raise HTTPException(422, "profile_id_mismatch")
            updated = data.snapshot.with_id(profile_id)
            old = json.loads(row["snapshot"])
            if data.mode == "append":
                for name in ("ledger", "attempts", "uses"):
                    if updated[name][:len(old[name])] != old[name]:
                        raise HTTPException(409, "journal_not_append_only")
            conn.execute("UPDATE profiles SET revision=revision+1,snapshot=?,updated_at=? WHERE id=?", (canonical(updated), now(), profile_id))
            row = profile_row(conn, user, profile_id)
            store_history(conn, row)
            return record(conn, user, key, fingerprint, row)

    @app.delete("/api/profiles/{profile_id}")
    def delete_profile(profile_id: int, data: DeleteProfile, request: Request):
        profile_write_limit(request)
        with db.connect(write=True) as conn:
            user = session(conn, request, csrf=True)
            key, fingerprint, replay = mutation(conn, user, request, "delete:"+str(profile_id), data)
            if replay is not None:
                return replay
            row = profile_row(conn, user, profile_id)
            if row["revision"] != data.expectedRevision or row["deleted"]:
                raise HTTPException(409, {"code": "revision_conflict", "current": envelope(row)})
            conn.execute("UPDATE profiles SET revision=revision+1,snapshot=NULL,deleted=1,updated_at=? WHERE id=?", (now(), profile_id))
            return record(conn, user, key, fingerprint, profile_row(conn, user, profile_id))

    return app
