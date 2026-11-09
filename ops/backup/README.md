# Daily MariaDB backup

The Oracle host runs `snapvocab-backup.timer` every day at **00:00 UTC**
(07:00 Asia/Ho_Chi_Minh). `Persistent=true` catches up after host downtime.
The root-run script uses MariaDB socket authentication and MinIO's existing
container credentials internally; no passwords are committed or logged.

Each run creates this private prefix in the `snapvocab` bucket:

```text
backups/2026-11-10T00-00-00Z/
  snapvocab.sql.gz
  SHA256SUMS
  metadata.json
```

The dump includes schema, data, routines, events and triggers. It uses
`--single-transaction --quick` for an InnoDB snapshot. Do not run schema changes
while a dump is in progress. The script validates gzip and reads the uploaded
dump back to verify SHA-256. Backups stay private; the `public/` rule does not
apply to `backups/`. No remote or local retention/deletion is configured.

Installed files:

- `/usr/local/sbin/snapvocab-backup`: this Python script (Python 3.11+).
- `/usr/local/bin/mc`: MinIO Client.
- `/etc/systemd/system/snapvocab-backup.service` and `.timer`.
- `/var/backups/snapvocab/<UTC timestamp>/`: local dump, checksum, metadata,
  diagnostics and an `uploaded.ok` marker after successful verification.

```bash
sudo systemctl start snapvocab-backup.service
sudo systemctl status snapvocab-backup.service --no-pager
sudo systemctl list-timers snapvocab-backup.timer --all
sudo journalctl -u snapvocab-backup.service --since today --no-pager
```

Failures remain visible in systemd/journald and preserve the local dump.
Concurrent runs are prevented with a filesystem lock. No automated restore is
performed; restore into an isolated database before using a backup for recovery.
