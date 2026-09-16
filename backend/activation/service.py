"""Activation service. Run behind an HTTPS reverse proxy; administration is CLI-only."""
import argparse
import hashlib
import json
import secrets
import sqlite3
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class ActivationStore:
    def __init__(self, path):
        self.path = path
        with self.connect() as db:
            db.executescript('''
                CREATE TABLE IF NOT EXISTS codes (
                    digest TEXT PRIMARY KEY, seats INTEGER NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1);
                CREATE TABLE IF NOT EXISTS devices (
                    digest TEXT NOT NULL, device TEXT NOT NULL, created INTEGER NOT NULL,
                    PRIMARY KEY(digest, device));
                CREATE TABLE IF NOT EXISTS attempts (
                    source TEXT PRIMARY KEY, started INTEGER NOT NULL, count INTEGER NOT NULL);
            ''')

    def connect(self):
        return sqlite3.connect(self.path, timeout=15)

    @staticmethod
    def digest(code):
        return hashlib.sha256(code.strip().upper().encode()).hexdigest()

    def issue(self, seats):
        if not 1 <= seats <= 1000:
            raise ValueError('seats must be between 1 and 1000')
        code = 'KH-' + secrets.token_hex(16).upper()
        with self.connect() as db:
            db.execute('INSERT INTO codes(digest,seats) VALUES(?,?)', (self.digest(code), seats))
        return code

    def activate(self, code, device, source):
        if not isinstance(code, str) or not isinstance(device, str) or not 16 <= len(device) <= 128 or len(code) > 128:
            return 400, {'status': 'invalid_request'}
        digest = self.digest(code)
        device = hashlib.sha256(device.encode()).hexdigest()
        source = hashlib.sha256(source.encode()).hexdigest()
        now = int(time.time())
        with self.connect() as db:
            # Seats and retries are checked in the same write transaction.
            db.execute('BEGIN IMMEDIATE')
            db.execute('DELETE FROM attempts WHERE started < ?', (now - 600,))
            row = db.execute('SELECT count FROM attempts WHERE source=?', (source,)).fetchone()
            if row and row[0] >= 10:
                return 429, {'status': 'too_many_attempts'}
            db.execute('INSERT INTO attempts VALUES(?,?,1) ON CONFLICT(source) DO UPDATE SET count=count+1', (source, now))
            row = db.execute('SELECT seats,enabled FROM codes WHERE digest=?', (digest,)).fetchone()
            if not row or not row[1]:
                return 403, {'status': 'invalid_code'}
            if db.execute('SELECT 1 FROM devices WHERE digest=? AND device=?', (digest, device)).fetchone():
                return 200, {'status': 'activated'}
            count = db.execute('SELECT count(*) FROM devices WHERE digest=?', (digest,)).fetchone()[0]
            if count >= row[0]:
                return 409, {'status': 'device_limit'}
            db.execute('INSERT INTO devices VALUES(?,?,?)', (digest, device, now))
            return 200, {'status': 'activated'}

    def disable(self, code):
        with self.connect() as db:
            db.execute('UPDATE codes SET enabled=0 WHERE digest=?', (self.digest(code),))

    def release_device(self, code, device):
        with self.connect() as db:
            db.execute('DELETE FROM devices WHERE digest=? AND device=?',
                       (self.digest(code), hashlib.sha256(device.encode()).hexdigest()))


def handler(store):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass  # Do not log codes or device identifiers.

        def do_POST(self):
            status, body = 404, {'status': 'not_found'}
            if self.path == '/activate':
                try:
                    length = int(self.headers.get('Content-Length', '0'))
                    if not 0 < length <= 2048:
                        raise ValueError()
                    self.connection.settimeout(10)
                    data = json.loads(self.rfile.read(length))
                    if not isinstance(data, dict):
                        raise ValueError()
                    status, body = store.activate(data.get('code'), data.get('device'), self.client_address[0])
                except (ValueError, OSError):
                    status, body = 400, {'status': 'invalid_request'}
                except sqlite3.Error:
                    status, body = 503, {'status': 'unavailable'}
            payload = json.dumps(body).encode()
            self.send_response(status)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Content-Length', str(len(payload)))
            self.send_header('Cache-Control', 'no-store')
            self.end_headers()
            self.wfile.write(payload)
    return Handler


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--db', default='activation.sqlite3')
    sub = parser.add_subparsers(dest='command', required=True)
    issue = sub.add_parser('issue')
    issue.add_argument('--seats', type=int, default=1)
    sub.add_parser('disable')
    sub.add_parser('release-device')
    serve = sub.add_parser('serve')
    serve.add_argument('--port', type=int, default=8080)
    args = parser.parse_args()
    store = ActivationStore(args.db)
    if args.command == 'issue':
        print(store.issue(args.seats))
    elif args.command == 'disable':
        import getpass
        store.disable(getpass.getpass('Code: '))
    elif args.command == 'release-device':
        import getpass
        store.release_device(getpass.getpass('Code: '), input('Installation ID: ').strip())
    else:
        ThreadingHTTPServer(('127.0.0.1', args.port), handler(store)).serve_forever()
