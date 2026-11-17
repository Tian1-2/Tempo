"""Test-only bridge: execute the production Java store against real in-memory SQLite."""
import base64,sqlite3,sys
conn=sqlite3.connect(':memory:',isolation_level=None)
def dec(v):
    if v=='n':return None
    if v.startswith('s'):return base64.b64decode(v[1:]).decode()
    if v.startswith('i'):return int(v[1:])
    return float(v[1:])
def enc(v):
    if v is None:return 'n'
    if isinstance(v,int):return 'i'+str(v)
    if isinstance(v,float):return 'f'+str(v)
    return 's'+base64.b64encode(str(v).encode()).decode()
for line in sys.stdin:
    try:
        mode,sql,*args=line.rstrip('\n').split('\t')
        cur=conn.execute(base64.b64decode(sql).decode(),[dec(v) for v in args])
        if mode=='q':
            rows=cur.fetchall();cols=[v[0] for v in cur.description]
            print('OK\t'+str(len(cols))+'\t'+str(len(rows)))
            print('\t'.join(enc(v) for v in cols))
            for row in rows:print('\t'.join(enc(v) for v in row))
        else:print('OK\t'+str(cur.rowcount)+'\t'+str(cur.lastrowid or 0))
    except Exception as e:print('ERR\t'+base64.b64encode(str(e).encode()).decode())
    sys.stdout.flush()
