#!/usr/bin/env bash
# verify-backup.sh - Verify backup integrity
set -euo pipefail

BACKUP_FILE="${1:?Usage: verify-backup.sh <backup-file-path>}"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "ERROR: Backup file not found: ${BACKUP_FILE}"
    exit 1
fi

echo "Verifying backup: ${BACKUP_FILE}"

pg_restore -l "${BACKUP_FILE}" > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "Backup verification PASSED"
    echo "File: ${BACKUP_FILE}"
    echo "Size: $(stat -c%s "${BACKUP_FILE}" 2>/dev/null || stat -f%z "${BACKUP_FILE}") bytes"
    echo "SHA-256: $(sha256sum "${BACKUP_FILE}" | awk '{print $1}')"
else
    echo "Backup verification FAILED"
    exit 1
fi
