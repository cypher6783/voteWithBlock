#!/usr/bin/env bash
# restore-database.sh - Restore PostgreSQL backup
set -euo pipefail

BACKUP_FILE="${1:?Usage: restore-database.sh <backup-file-path>}"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "ERROR: Backup file not found: ${BACKUP_FILE}"
    exit 1
fi

echo "Restoring database from: ${BACKUP_FILE}"

pg_restore -h localhost -p 5433 -U postgres -d voting_db --clean --if-exists "${BACKUP_FILE}"

echo "Database restored successfully."
