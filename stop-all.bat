@echo off
title Votetrust Ledger - Shutdown All Services

echo ========================================================
echo        Votetrust Ledger - Stopping All Services
echo ========================================================
echo.

echo [1/2] Stopping Application Stack (Frontend, Gateway, AI, Database)...
docker-compose down

echo.
echo [2/2] Stopping Hyperledger Fabric Blockchain Network...
set DOCKER_SOCK=/var/run/docker.sock
docker compose -f "Blockchain Network/fabric-samples/test-network/compose/compose-test-net.yaml" -f "Blockchain Network/fabric-samples/test-network/compose/docker/docker-compose-test-net.yaml" -f "Blockchain Network/fabric-samples/test-network/compose/compose-ca.yaml" down

echo.
echo ========================================================
echo              All Services Have Been Stopped!
echo ========================================================
pause
