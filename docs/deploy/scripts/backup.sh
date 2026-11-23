#!/usr/bin/env bash
# Back up the database AND the uploads folder together (doc 14 §6.4, doc 08 §7.2, ADR-06).
#   BACKUP_DIR=/var/backups/smart-grievance ./backup.sh
# Needs ~/.my.cnf (chmod 600) for the grievance_backup user:
#   [mysqldump]
#   user=grievance_backup
#   password="..."        <- quotes needed: a # in an unquoted value starts a comment
set -euo pipefail
DB="${DB_NAME:-grievance}"
UPLOAD_DIR="${UPLOAD_DIR:-/var/lib/smart-grievance/uploads}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/smart-grievance}"
STAMP="$(TZ=Asia/Kolkata date +%Y%m%d-%H%M)"
mkdir -p "$BACKUP_DIR"
umask 077
SQL="$BACKUP_DIR/$DB-$STAMP.sql.gz"
FILES="$BACKUP_DIR/uploads-$STAMP.tar.gz"
trap 'rm -f "$SQL.part" "$FILES.part"' EXIT
mysqldump --single-transaction --no-tablespaces --routines --triggers \
  --set-gtid-purged=OFF --default-character-set=utf8mb4 "$DB" | gzip > "$SQL.part"
# right after the dump, so rows and files match (a file newer than the dump is harmless)
tar -czf "$FILES.part" -C "$(dirname "$UPLOAD_DIR")" "$(basename "$UPLOAD_DIR")"
mv "$SQL.part" "$SQL"
mv "$FILES.part" "$FILES"
# keep 7 daily copies (+ copy one per week elsewhere by hand or with a second cron line)
find "$BACKUP_DIR" -name "*.gz" -mtime +7 -delete
echo "Backup written: $SQL and $FILES"
