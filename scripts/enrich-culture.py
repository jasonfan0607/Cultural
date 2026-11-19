"""Curated public culture import. Dry run by default; --apply backs up before a transaction.

Requires Python, PyYAML and the mysql CLI. Credentials stay in the child environment.
Only published matching records are edited; media, IDs, counters and user data are preserved.
"""
import argparse
import datetime as dt
import html
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'backend/src/main/resources/db/culture-library-2026-11.json'
TABLES = {'heritage': 'heritage', 'art': 'traditional_art'}

def literal(value):
    if value is None:
        return 'NULL'
    return "CONVERT(0x" + str(value).encode('utf8').hex() + " USING utf8mb4)" if str(value) else "''"

def render(entry):
    parts = []
    for heading, body in entry['sections']:
        parts.append('<h3>' + html.escape(heading) + '</h3>')
        parts.extend('<p>' + html.escape(p) + '</p>' for p in body.split('\n') if p.strip())
    parts.append('<h3>参考资料与编辑说明</h3><ul>')
    for source in entry['sources']:
        parts.append('<li><a href="' + html.escape(source['url'], quote=True) + '" target="_blank" rel="noopener noreferrer">'
                     + html.escape(source['title']) + '</a>：' + html.escape(source.get('scope', '主题背景参考')) + '</li>')
    parts.append('</ul><p>平台整理：2026年11月16日。本文为综合介绍；设计应用部分为编辑建议，不代表历史原作、非遗定级或机构授权。</p>')
    return ''.join(parts)

def load_entries():
    entries = json.loads(SOURCE.read_text(encoding='utf8'))['entries']
    keys = set()
    for entry in entries:
        key = (entry['type'], entry['name'], entry['city'])
        assert key not in keys, f'Duplicate curated entry: {key}'
        keys.add(key)
        assert entry['type'] in TABLES
        assert 0 < len(entry['summary']) <= 500
        assert len(entry['alias']) <= 200
        assert len(entry['sections']) >= 4 and entry['sources']
        assert all(s['url'].startswith('https://') for s in entry['sources'])
        entry['content'] = render(entry)
    return entries

class Database:
    def __init__(self):
        import yaml
        config = yaml.safe_load((ROOT / 'backend/application-local.yml').read_text(encoding='utf8')) or {}
        db = config.get('spring', {}).get('datasource', {})
        self.env = os.environ.copy()
        password = self.env.get('DB_PASSWORD') or db.get('password', '')
        if str(password).startswith('${'):
            raise ValueError('Set DB_PASSWORD in the environment')
        self.env['MYSQL_PWD'] = str(password)
        self.command = ['mysql', '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names',
                        '-h', '127.0.0.1', '-u', db.get('username', 'root'), '-D', 'cultural_db']

    def execute(self, sql):
        result = subprocess.run(self.command, input=sql.encode('utf8'), env=self.env, capture_output=True)
        if result.returncode:
            # Never print SQL or configuration; connection closes and rolls back on batch error.
            error = result.stderr.decode('utf8', errors='replace')
            code = re.search(r'ERROR (\d+)', error)
            raise RuntimeError('MySQL operation failed' + (' (code ' + code[1] + ')' if code else '') + '; transaction not committed')
        return result.stdout.decode('utf8')

    def rows(self, table, where='1=1'):
        assert table in {*TABLES.values(), 'city', 'category'}
        columns = self.execute('SHOW COLUMNS FROM `' + table + '`;').splitlines()
        names = [line.split('\t')[0] for line in columns]
        fields = ','.join(literal(name) + ',`' + name + '`' for name in names)
        output = self.execute(f'SELECT JSON_OBJECT({fields}) FROM `{table}` WHERE {where};')
        return [json.loads(line) for line in output.splitlines() if line.strip()]

def plan(entries, snapshots):
    changes, skipped = [], []
    for entry in entries:
        table = TABLES[entry['type']]
        existing = [row for row in snapshots[table] if row['name'] == entry['name'] and row['city'] == entry['city'] and not row['deleted']]
        if not existing:
            changes.append(('insert', entry, None))
        elif any(row['status'] != 1 for row in existing):
            skipped.append(entry['name'] + '：存在未公开记录，保留人工审核状态')
        else:
            for row in existing:
                if any(row.get(field) != entry[target] for field, target in [('description','summary'),('content','content'),('alias','alias')]):
                    changes.append(('update', entry, row))
    return changes, skipped

def migration_sql(changes, cities, categories):
    # MySQL 8 CHECK constraints abort the batch on a stale snapshot; no --force is used.
    statements = ["CREATE TEMPORARY TABLE culture_import_guard(ok INT NOT NULL CHECK(ok=1));",
                  "INSERT INTO culture_import_guard VALUES(GET_LOCK('cultural_library_enrichment',10));",
                  'SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;', 'START TRANSACTION;']
    for action, entry, old in changes:
        table = TABLES[entry['type']]
        match = f"name={literal(entry['name'])} AND city={literal(entry['city'])} AND deleted=0"
        if action == 'update':
            checks = [f'id={int(old["id"])}', 'status=1', 'deleted=0']
            for field in ['name','city','description','content','alias']:
                checks.append(f'BINARY `{field}` <=> BINARY {literal(old.get(field))}')
            assignments = ','.join(f'`{field}`={literal(entry[target])}' for field,target in [('description','summary'),('content','content'),('alias','alias')])
            statements.append(f'UPDATE `{table}` SET {assignments} WHERE ' + ' AND '.join(checks) + ';')
        else:
            statements.append(f'INSERT INTO culture_import_guard SELECT IF(COUNT(*)=0,1,0) FROM `{table}` WHERE {match};')
            city = next((c for c in cities if c['name'].removesuffix('市') == entry['city'] and not c['deleted']), None)
            if city is None:
                raise ValueError('City missing: ' + entry['city'])
            category = next((c for c in categories if c['code'] == entry['categoryCode'] and not c['deleted']), None)
            fields = ['name','city','city_id','category','category_id','alias','description','content','status','deleted']
            values = [literal(entry['name']),literal(entry['city']),str(city['id']),literal(entry['category']),
                      str(category['id']) if category else 'NULL',literal(entry['alias']),literal(entry['summary']),literal(entry['content']),'1','0']
            if table == 'heritage':
                fields.append('level'); values.append('NULL') # Do not invent a national/provincial designation.
            statements.append(f'INSERT INTO `{table}` (`' + '`,`'.join(fields) + '`) VALUES (' + ','.join(values) + ');')
        statements.append('INSERT INTO culture_import_guard VALUES(IF(ROW_COUNT()=1,1,0));')
    statements += ['COMMIT;', "SELECT RELEASE_LOCK('cultural_library_enrichment');"]
    return '\n'.join(statements)

def main():
    sys.stdout.reconfigure(encoding='utf8')
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--validate', action='store_true', help='Validate content only; no database connection')
    args = parser.parse_args()
    entries = load_entries()
    lengths = [sum(len(body) for _,body in e['sections']) for e in entries]
    print(json.dumps({'topics':len(entries),'cities':sorted({e['city'] for e in entries}),
                      'bodyChars':{'min':min(lengths),'max':max(lengths),'total':sum(lengths)}}, ensure_ascii=False))
    if args.validate:
        return
    db = Database()
    snapshots = {table: db.rows(table) for table in TABLES.values()}
    changes, skipped = plan(entries, snapshots)
    print(json.dumps({'update':sum(a=='update' for a,_,_ in changes),'insert':sum(a=='insert' for a,_,_ in changes),'skipped':skipped},ensure_ascii=False))
    if not args.apply or not changes:
        return
    sql = migration_sql(changes, db.rows('city'), db.rows('category'))
    backup = ROOT / 'backend/target/culture-backups' / (dt.datetime.now().strftime('%Y%m%d-%H%M%S-%f') + '.json')
    backup.parent.mkdir(parents=True, exist_ok=True)
    backup.write_text(json.dumps({'before':snapshots,'planned':[{'action':a,'type':e['type'],'name':e['name'],'city':e['city'],'id':old['id'] if old else None} for a,e,old in changes]},ensure_ascii=False,indent=2),encoding='utf8')
    print('Backup: ' + str(backup))
    db.execute(sql)
    remaining, _ = plan(entries, {t: db.rows(t) for t in TABLES.values()})
    assert not remaining, 'Post-import verification failed'
    print('Applied and verified. Repeat import has zero changes.')

if __name__ == '__main__':
    main()
