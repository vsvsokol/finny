#!/usr/bin/python3
"""Берёт публичный пакет только после успешного CI текущей web-port."""
import time
import fcntl, json, pathlib, subprocess, tempfile, urllib.request
ROOT=pathlib.Path('/home/lemonke67/finney')
lock=open(ROOT/'pull.lock','w')
try: fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
except BlockingIOError: raise SystemExit(0)
def get(path):
 req=urllib.request.Request('https://api.github.com/repos/vsvsokol/finny/'+path,headers={'User-Agent':'finny-deploy'})
 with urllib.request.urlopen(req,timeout=30) as response: return json.load(response)
sha=subprocess.check_output(['git','ls-remote','https://github.com/vsvsokol/finny.git','refs/heads/web-port'],text=True,timeout=30).split()[0]
if (ROOT/'deployed.json').exists() and json.loads((ROOT/'deployed.json').read_text())['commit']==sha: raise SystemExit(0)
stamp=ROOT/'last-poll.json'
if stamp.exists():
 last=json.loads(stamp.read_text())
 if last['sha']==sha and time.time()-last['time']<300: raise SystemExit(0)
stamp.write_text(json.dumps({'sha':sha,'time':time.time()}))
runs=get('actions/workflows/web.yml/runs?branch=web-port&status=success&per_page=10')['workflow_runs']
if not any(run['head_sha']==sha and run['event'] in ('push','workflow_dispatch') for run in runs): raise SystemExit(0)
try: release=get('releases/tags/web-build-'+sha)
except urllib.error.HTTPError as e:
 if e.code==404: raise SystemExit(0)
 raise
asset=next(a for a in release['assets'] if a['name']=='finny-web.tar.gz')
url=asset['browser_download_url']
if not url.startswith('https://github.com/vsvsokol/finny/releases/download/'): raise ValueError('Неожиданный URL пакета')
with tempfile.TemporaryFile() as package:
 with urllib.request.urlopen(url,timeout=120) as response:
  size=0
  while chunk:=response.read(1024*1024):
   size+=len(chunk)
   if size>100*1024*1024: raise ValueError('Слишком большой пакет')
   package.write(chunk)
 package.seek(0)
 subprocess.run(['/usr/bin/python3',str(ROOT/'receive.py')],stdin=package,check=True)
