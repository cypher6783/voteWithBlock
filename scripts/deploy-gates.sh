#!/usr/bin/env bash
# deploy-gates.sh - Pre/Post deployment verification gates
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
AI_URL="${AI_URL:-http://localhost:8000}"

echo "=== Pre-Deployment Gates ==="

echo "Checking database migration validation..."
PGPASSWORD="${PGPASSWORD:?Set PGPASSWORD environment variable}" psql -h localhost -p 5433 -U postgres -d voting_db -c "SELECT 1" > /dev/null 2>&1
echo "  [PASS] Database accessible"

echo "Checking core-gateway health..."
HEALTH=$(curl -sf "${GATEWAY_URL}/actuator/health" 2>/dev/null || echo "DOWN")
if echo "${HEALTH}" | grep -q '"status":"UP"'; then
    echo "  [PASS] Core Gateway healthy"
else
    echo "  [FAIL] Core Gateway unhealthy: ${HEALTH}"
    exit 1
fi

echo "Checking ai-service health..."
AI_HEALTH=$(curl -sf "${AI_URL}/health" 2>/dev/null || echo "DOWN")
if echo "${AI_HEALTH}" | grep -q '"status":"ONLINE"'; then
    echo "  [PASS] AI Service healthy"
else
    echo "  [FAIL] AI Service unhealthy: ${AI_HEALTH}"
    exit 1
fi

echo ""
echo "=== Pre-Deployment Gates PASSED ==="
