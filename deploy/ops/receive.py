#!/usr/bin/python3
"""Установка релиза, проверенного GitHub CI."""
import urllib.request
import fcntl, hashlib, json, os, pathlib, re, subprocess, sys, tarfile, tempfile, time
ROOT=pathlib.Path('/home/lemonke67/finney')
os.umask(0o077)
lock=open(ROOT/'deploy.lock','w'); fcntl.flock(lock,fcntl.LOCK_EX)
def run(*args): return subprocess.run(args,check=True)
def compose(*args): return run('docker','compose','--env-file',str(ROOT/'deploy.env'),'-f',str(ROOT/'compose.yaml'),*args)
with tempfile.TemporaryDirectory(dir=ROOT) as temp:
 base=pathlib.Path(temp); archive=base/'upload.tar.gz'; size=0
 with archive.open('wb') as output:
  while chunk:=sys.stdin.buffer.read(1024*1024):
   size+=len(chunk)
   if size>100*1024*1024: raise ValueError('Архив слишком большой')
   output.write(chunk)
 with tarfile.open(archive) as tar:
  if sum(m.size for m in tar.getmembers())>200*1024*1024: raise ValueError('Релиз слишком большой')
  for m in tar.getmembers():
   p=pathlib.PurePosixPath(m.name)
   if p.is_absolute() or '..' in p.parts or not p.parts or p.parts[0] not in ('web','api','revision.txt') or not (m.isfile() or m.isdir()): raise ValueError('Недопустимый путь')
  tar.extractall(base,filter='data')
 revision=(base/'revision.txt').read_text().strip()
 if not re.fullmatch('[0-9a-f]{40}',revision): raise ValueError('Нужен SHA Git')
 manifest=json.loads((base/'web/release.json').read_text())
 for name,checksum in manifest['assets'].items():
  p=pathlib.PurePosixPath(name)
  if p.is_absolute() or '..' in p.parts: raise ValueError('Некорректный манифест')
  if hashlib.sha256((base/'web'/name).read_bytes()).hexdigest()!=checksum: raise ValueError('SHA-256 не совпадает')
 image='finney-api:'+revision
 run('docker','build','--target','production','-t',image,str(base/'api'))
 run('docker','run','--rm','--network','none','-v',str(ROOT/'data')+':/data','-v',str(ROOT/'backups')+':/backups',image,'python','-c',"from pathlib import Path; from backup import backup; import time; p=Path('/data/finney.sqlite3'); backup(p,Path('/backups/predeploy-'+str(time.time_ns())+'.sqlite3')) if p.exists() else None")
 site=ROOT/'site'; releases=site/'releases'; releases.mkdir(parents=True,exist_ok=True)
 target=releases/revision
 if not target.exists():
  os.rename(base/'web',target)
  for p in target.rglob('*'): p.chmod(0o755 if p.is_dir() else 0o644)
  target.chmod(0o755)
 current=site/'current'; previous=os.readlink(current) if current.is_symlink() else None
 old_env=(ROOT/'deploy.env').read_text() if (ROOT/'deploy.env').exists() else None
 try:
  (ROOT/'deploy.env').write_text('FINNY_API_IMAGE='+image+'\n')
  compose('up','-d','--wait','--wait-timeout','90','api')
  candidate=site/'next'; candidate.unlink(missing_ok=True); candidate.symlink_to('releases/'+revision); candidate.replace(current)
  compose('up','-d','web')
  for attempt in range(30):
   result=subprocess.run(['curl','-fsS','--max-time','10','https://finny.mango-kokos.ru/api/health'],stdout=subprocess.DEVNULL)
   if result.returncode==0:
    try:
     with urllib.request.urlopen('https://finny.mango-kokos.ru/release.json',timeout=10) as response: published=json.load(response)
     if published['release']==manifest['release']: break
    except Exception: pass
   time.sleep(2)
  else: raise RuntimeError('HTTPS healthcheck не прошёл')
 except BaseException:
  if old_env:
   (ROOT/'deploy.env').write_text(old_env); compose('up','-d','--wait','api')
  if previous:
   candidate=site/'next'; candidate.unlink(missing_ok=True); candidate.symlink_to(previous); candidate.replace(current)
  raise
 (ROOT/'deployed.json').write_text(json.dumps({'commit':revision,'release':manifest['release'],'time':int(time.time())})+'\n')
 print('DEPLOYED',revision,manifest['release'])
