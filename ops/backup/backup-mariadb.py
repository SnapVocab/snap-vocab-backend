#!/usr/bin/env python3
"""Root-run MariaDB snapshot; upload verified files to private MinIO backups/."""
import datetime
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import urllib.parse


def main():
    os.umask(0o077)
    state = Path('/var/backups/snapvocab')
    state.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (state / '.backup.lock').open('w') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        timestamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%dT%H-%M-%SZ')
        folder = state / timestamp
        folder.mkdir(mode=0o700)
        archive = folder / 'snapvocab.sql.gz'
        pending = folder / 'snapvocab.sql.gz.partial'
        with (folder / 'dump.stderr').open('wb') as errors, gzip.open(pending, 'wb') as output:
            process = subprocess.Popen([
                'mariadb-dump', '--protocol=SOCKET', '--user=root',
                '--single-transaction', '--quick', '--routines', '--events', '--triggers',
                '--hex-blob', '--default-character-set=utf8mb4', '--databases', 'snapvocab',
            ], stdout=subprocess.PIPE, stderr=errors)
            try:
                with process.stdout:
                    shutil.copyfileobj(process.stdout, output)
            finally:
                code = process.wait()
            if code:
                raise RuntimeError('mariadb-dump failed; see local dump.stderr')
        with gzip.open(pending, 'rb') as source:
            uncompressed = 0
            while block := source.read(1024 * 1024):
                uncompressed += len(block)
        if uncompressed == 0:
            raise RuntimeError('Empty database dump')
        pending.rename(archive)
        with archive.open('rb') as handle:
            digest = hashlib.file_digest(handle, 'sha256').hexdigest()
        checksum = folder / 'SHA256SUMS'
        checksum.write_text(digest + '  snapvocab.sql.gz\n')
        metadata = folder / 'metadata.json'
        metadata.write_text(json.dumps({
            'database': 'snapvocab', 'started_at_utc': timestamp,
            'dump_bytes': uncompressed, 'archive_bytes': archive.stat().st_size,
            'sha256': digest, 'dump_options': 'single-transaction, routines, events, triggers, hex-blob',
        }, indent=2) + '\n')

        # Credentials stay inside this root process and the child environment.
        # Use localhost so backups do not depend on Cloudflare/WAF availability.
        container = json.loads(subprocess.check_output(['docker', 'inspect', 'minio']))[0]
        values = dict(item.split('=', 1) for item in container['Config']['Env'] if '=' in item)
        env = os.environ.copy()
        env['MC_HOST_backup'] = 'http://{}:{}@127.0.0.1:9000'.format(
            urllib.parse.quote(values['MINIO_ROOT_USER'], safe=''),
            urllib.parse.quote(values['MINIO_ROOT_PASSWORD'], safe=''))
        destination = 'backup/snapvocab/backups/' + timestamp + '/'

        def mc(*args):
            result = subprocess.run(['/usr/local/bin/mc', *args], env=env,
                                    capture_output=True, text=True, timeout=300)
            if result.returncode:
                raise RuntimeError('MinIO ' + args[0] + ' failed; local backup retained')
            return result.stdout

        for file in [archive, checksum, metadata]:
            mc('cp', str(file), destination + file.name)
            remote = json.loads(mc('stat', '--json', destination + file.name))
            if remote['size'] != file.stat().st_size:
                raise RuntimeError('MinIO upload size mismatch; local backup retained')
        # Read back the uploaded dump and verify its SHA-256, including multipart uploads.
        downloaded = folder / 'verify.sql.gz'
        try:
            mc('cp', destination + archive.name, str(downloaded))
            with downloaded.open('rb') as handle:
                if hashlib.file_digest(handle, 'sha256').hexdigest() != digest:
                    raise RuntimeError('Uploaded dump checksum mismatch')
        finally:
            downloaded.unlink(missing_ok=True)
        (folder / 'uploaded.ok').write_text(destination + '\n')
        print('Backup verified: snapvocab/backups/' + timestamp + '/ (' + str(archive.stat().st_size) + ' bytes)', flush=True)


if __name__ == '__main__':
    main()
