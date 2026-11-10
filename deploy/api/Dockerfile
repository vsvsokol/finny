FROM python:3.13-slim@sha256:e48f59cdba69a3cbd9ed2cf0f348d90d45a8d350d98134cf1070bc106ee9ed90 AS runtime
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_DISABLE_PIP_VERSION_CHECK=1
WORKDIR /app
COPY requirements.lock ./
RUN pip install --no-cache-dir -r requirements.lock && pip check \
    && groupadd --gid 10001 finny && useradd --uid 10001 --gid 10001 --no-create-home finny \
    && mkdir /data && chown 10001:10001 /data && chmod 700 /data
COPY server.py models.py database.py backup.py ./
USER 10001:10001
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 CMD python -c "import os,urllib.request,urllib.parse; origin=os.environ.get('FINNY_ORIGIN','https://finny.mango-kokos.ru'); r=urllib.request.Request('http://127.0.0.1:8000/api/health',headers={'Host':urllib.parse.urlsplit(origin).netloc}); urllib.request.urlopen(r,timeout=2)"
CMD ["python", "-m", "uvicorn", "server:create_app", "--factory", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--no-access-log", "--no-proxy-headers", "--limit-concurrency", "16", "--timeout-keep-alive", "5"]

FROM runtime AS test
USER root
COPY requirements-test.lock ./
RUN pip install --no-cache-dir -r requirements-test.lock && pip check
COPY tests/ ./tests/
USER 10001:10001
CMD ["python", "-m", "pytest", "-q", "-p", "no:cacheprovider", "tests"]

FROM runtime AS production
