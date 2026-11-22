#!/usr/bin/env python3
"""Runs V1 + V2 + sample-data.sql + key-queries.sql on MySQL and on H2 (MySQL mode).

Usage: run-verification.py <h2.jar> <java> <workdir>
Needs a local MySQL reachable with the `mysql` client (no password, e.g. root via socket).
"""
import os, re, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
MIG = os.path.join(HERE, '..', 'migration')
h2jar, java, work = sys.argv[1:4]
H2URL = f"jdbc:h2:file:{work}/h2verify;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
DB = 'grievance_verify'

def blocks():
    text = open(os.path.join(HERE, 'key-queries.sql')).read()
    for part in re.split(r'^-- @', text, flags=re.M)[1:]:
        lines = part.splitlines()
        title = lines[0]
        params = {}
        m = re.match(r'-- params:(.*)', lines[1])
        if m and m.group(1).strip():
            for kv in re.findall(r"(\w+)=('[^']*'|[^,\s]+)", m.group(1)):
                params[kv[0]] = kv[1]
        sql = '\n'.join(l for l in lines[2:] if not l.startswith('--')).strip()
        for k in sorted(params, key=len, reverse=True):
            sql = re.sub(r':' + k + r'\b', params[k], sql)
        yield title, sql

def mysql(sql, db=DB):
    r = subprocess.run(['mysql', '-t', '-vv', db], input=sql, text=True, capture_output=True)
    return (r.stdout + r.stderr).strip()

def h2(sql):
    r = subprocess.run([java, '-cp', h2jar, 'org.h2.tools.Shell', '-url', H2URL, '-user', 'sa'],
                       input=sql + '\nexit\n', text=True, capture_output=True)
    out = [l for l in (r.stdout + r.stderr).splitlines()
           if not l.startswith(('Picked up', 'Welcome to H2', 'Exit with', 'Commands are', 'help or ?', 'list or l', 'maxwidth', 'autocommit', 'history', 'quit or exit', 'Type the same'))
           and l.strip() not in ('sql>', '...>')]
    return '\n'.join(out).strip()

def setup():
    subprocess.run(['mysql', '-e', f'DROP DATABASE IF EXISTS {DB}; CREATE DATABASE {DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'], check=True)
    for f in [os.path.join(MIG, 'V1__schema.sql'), os.path.join(MIG, 'V2__reference_data.sql'), os.path.join(HERE, 'sample-data.sql')]:
        subprocess.run(['mysql', DB], stdin=open(f), check=True)

def setup_h2():
    for ext in ('.mv.db', '.trace.db'):
        p = f'{work}/h2verify{ext}'
        if os.path.exists(p):
            os.remove(p)
    for f in [os.path.join(MIG, 'V1__schema.sql'), os.path.join(MIG, 'V2__reference_data.sql'), os.path.join(HERE, 'sample-data.sql')]:
        subprocess.run([java, '-cp', h2jar, 'org.h2.tools.RunScript', '-url', H2URL, '-user', 'sa', '-script', f], check=True,
                       capture_output=True)

setup()
setup_h2()
print('Schema, reference data and sample rows loaded on MySQL and H2.\n')
for title, sql in blocks():
    print('=' * 100)
    print('@' + title)
    print('-' * 40, 'MySQL')
    print(mysql(sql))
    print('-' * 40, 'H2 (MySQL mode)')
    print(h2(sql))
