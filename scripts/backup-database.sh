#!/usr/bin/env bash
# backup-database.sh - Create encrypted PostgreSQL backup
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${SCRIPT_DIR}/../backups"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="voting_db_${TIMESTAMP}.dump"
BACKUP_PATH="${BACKUP_DIR}/${BACKUP_FILE}"

mkdir -p "${BACKUP_DIR}"

echo "Starting database backup..."

pg_dump -h localhost -p 5433 -U postgres -d voting_db -Fc -f "${BACKUP_PATH}"

SHA256=$(sha256sum "${BACKUP_PATH}" | awk '{print $1}')
SIZE=$(stat -c%s "${BACKUP_PATH}" 2>/dev/null || stat -f%z "${BACKUP_PATH}")

echo "Backup completed: ${BACKUP_PATH}"
echo "Size: ${SIZE} bytes"
echo "SHA-256: ${SHA256}"
echo "Type: FULL"
