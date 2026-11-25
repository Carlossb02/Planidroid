#!/usr/bin/env python3
"""Prevent accidental commits of private files; never print detected values."""
import fnmatch
import re
import subprocess
import sys
from pathlib import PurePosixPath

BLOCKED_NAMES = (
    'google-services.json', 'googleservice-info.plist', 'local.properties',
    'secrets.properties', 'secrets.json', 'credentials.json', 'credentials.properties',
    'keystore.properties', 'signing.properties', '.env', '.env.*', '*.env', '*.env.*',
    '*service-account*.json', '*firebase-adminsdk*.json', '*client_secret*.json',
    '*client-secret*.json', '*.jks', '*.keystore', '*.p12', '*.pfx', '*.pem', '*.key',
    '*.kdbx', '*.db', '*.db-wal', '*.db-shm', '*.sqlite', '*.sqlite3', '*.apk', '*.aab',
    '*.zip', 'backup*.json', 'planidroid_backup*.json',
)
BLOCKED_DIRS = {'secrets', 'credentials', '.private', 'backups', 'exports'}
# Patterns deliberately identify key-shaped values, not configuration variable names.
PATTERNS = (
    re.compile(rb'AIza[0-9A-Za-z_-]{35}'),
    re.compile(rb'-----BEGIN (?:RSA |EC |DSA |OPENSSH |ENCRYPTED )?PRIVATE KEY-----'),
    re.compile(rb'gh[pousr]_[A-Za-z0-9]{30,}'),
    re.compile(rb'github_pat_[A-Za-z0-9_]{30,}'),
    re.compile(rb'GOCSPX-[A-Za-z0-9_-]{20,}'),
    re.compile(rb'(?i)"type"\s*:\s*"service_account"'),
)


def git(*args):
    return subprocess.check_output(['git', *args])


def paths(*args):
    return [p.decode('utf-8', 'surrogateescape') for p in git(*args).split(b'\0') if p]


def blocked(path):
    p = PurePosixPath(path.lower())
    return any(d in BLOCKED_DIRS for d in p.parts[:-1]) or any(
        fnmatch.fnmatchcase(p.name, pattern) for pattern in BLOCKED_NAMES
    )


def check_snapshot(revision=None):
    files = paths('ls-files', '-z') if revision is None else paths('ls-tree', '-r', '-z', '--name-only', revision)
    failures = []
    for path in files:
        if blocked(path):
            failures.append((path, 'private filename'))
            continue
        # A staged deletion does not appear in ls-files; scan the Git object, not the working copy.
        blob = f':{path}' if revision is None else f'{revision}:{path}'
        data = git('cat-file', 'blob', blob)
        if any(pattern.search(data) for pattern in PATTERNS):
            failures.append((path, 'possible credential'))
    for path, reason in failures:
        print(f'Blocked: {path!r} ({reason}); contents not displayed.', file=sys.stderr)
    return not failures


def main():
    args = sys.argv[1:]
    if args == ['--staged']:
        return 0 if check_snapshot() else 1
    if args == ['--head']:
        return 0 if check_snapshot('HEAD') else 1
    if args == ['--pre-push']:
        revisions = set()
        for line in sys.stdin:
            _, local, _, remote = line.split()
            if set(local) == {'0'}:
                continue  # deletion
            exclusion = [] if set(remote) == {'0'} else ['^' + remote]
            revisions.update(git('rev-list', local, *exclusion).decode().splitlines())
        return 0 if all(check_snapshot(rev) for rev in sorted(revisions)) else 1
    print('Usage: check_secrets.py --staged | --head | --pre-push', file=sys.stderr)
    return 2


if __name__ == '__main__':
    sys.exit(main())
