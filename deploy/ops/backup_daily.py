#!/usr/bin/python3
"""Ежедневная согласованная копия на SSD; 14 ежедневных и 20 преддеплойных."""
import subprocess,time
name='daily-'+time.strftime('%Y%m%d-%H%M%S')+'.sqlite3'
subprocess.run(['docker','compose','--env-file','/home/lemonke67/finney/deploy.env','-f','/home/lemonke67/finney/compose.yaml','exec','-T','api','python','-c',"from pathlib import Path; from backup import backup; backup(Path('/data/finney.sqlite3'),Path('/backups/"+name+"')); p=Path('/backups'); [(f.unlink()) for pattern,count in [('daily-*',14),('predeploy-*',20)] for f in sorted(p.glob(pattern),key=lambda f:f.stat().st_mtime,reverse=True)[count:]]"],check=True)
