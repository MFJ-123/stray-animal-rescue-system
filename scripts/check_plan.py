"""Validate collaboration records only, not application behavior."""
from pathlib import Path
from urllib.parse import unquote
import csv
import hashlib
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
errors = []

def check(condition, message):
    if not condition:
        errors.append(message)

def records(name):
    with (ROOT/'docs'/name).open(encoding='utf-8-sig', newline='') as f:
        return list(csv.DictReader(f))

def check_ids(rows, column, expected):
    ids = [r[column] for r in rows]
    check(len(ids) == len(set(ids)), f'Duplicate identifiers: {column}')
    check(set(ids) == expected, f'Missing or extra identifiers: {column}')

def check_people(rows, column):
    for r in rows:
        check(r[column] in set('ABCDE'), f'Invalid owner: {r.get(column)}')
        if r.get('协作者'):
            check(set(r['协作者'].split(';')) <= set('ABCDE'), 'Invalid collaborator')

req = records('requirements.csv')
tc = records('test-matrix.csv')
nfr = records('quality-matrix.csv')
tasks = records('backlog.csv')
check_ids(req, '需求编号', {f'SAR-SYS-{i:04d}' for i in range(1,41)})
check_ids(tc, '用例编号', {f'TC-{i:02d}' for i in range(1,22)})
check_ids(nfr, '指标编号', {f'NFR-{i:02d}' for i in range(1,11)})
check_people(req, '负责人')
check_people(tc, '牵头人')
check_people(nfr, '牵头人')
check_people(tasks, '负责人')
valid_tc = {r['用例编号'] for r in tc}
for row in req:
    check(set(row['验收用例'].split(';')) <= valid_tc, 'Invalid linked test')
    check(row['默认复核人'] != row['负责人'], 'Author must not be sole reviewer')
    if row['状态'] == '已验收':
        check(bool(row['证据'] and row['PR或提交']), 'Accepted requirement missing evidence')
for row in tc:
    if row['状态'] == '已通过':
        check(bool(row['证据'] and row['环境与提交']), 'Passed test missing evidence')

task_ids = {r['本地任务号'] for r in tasks}
check(len(task_ids) == len(tasks), 'Duplicate task identifiers')
graph = {}
for row in tasks:
    deps = set() if row['依赖任务'] == '无' else set(row['依赖任务'].split(';'))
    check(deps <= task_ids, 'Unknown dependency')
    graph[row['本地任务号']] = deps
done = set()
while True:
    ready = {k for k,v in graph.items() if v <= done} - done
    if not ready:
        break
    done |= ready
check(done == task_ids, 'Cyclic task dependencies')

manifest = json.loads((ROOT/'docs/baseline/manifest.json').read_text(encoding='utf-8'))
for item in manifest['files']:
    p = ROOT/item['file']
    check(p.is_file(), f'Missing baseline: {p.name}')
    if p.is_file():
        check(hashlib.sha256(p.read_bytes()).hexdigest() == item['sha256'], f'Changed baseline hash: {p.name}')

for p in ROOT.rglob('*.md'):
    if '.git' in p.parts:
        continue
    for target in re.findall(r'\]\(([^)]+)\)', p.read_text(encoding='utf-8')):
        if re.match(r'\w+://', target) or target.startswith('#'):
            continue
        file = unquote(target.split('#')[0])
        check((p.parent/file).exists(), f'Broken link in {p.relative_to(ROOT)}: {target}')

if errors:
    for error in errors:
        print('FAIL:', error)
    sys.exit(1)
print(f'PASS: {len(req)} requirements, {len(tc)} acceptance cases, {len(nfr)} quality targets, {len(tasks)} tasks, baseline hashes and links.')
print('This is documentation validation only. Application code and acceptance results are not implemented or verified by this check.')
