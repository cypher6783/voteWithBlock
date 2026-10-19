@echo off
setlocal enabledelayedexpansion

title Votetrust Ledger - Full System Launcher

echo ========================================================
echo        Votetrust Ledger - Full Startup Launcher
echo   (App Stack + Go Engine + Hyperledger Fabric Network)
echo ========================================================
echo.

:: 1. Verify Docker is running
echo [1/4] Checking Docker Desktop status...
docker info >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Docker Desktop is not running or not accessible.
    echo Please start Docker Desktop and wait until it is running, then re-run this script.
    pause
    exit /b 1
)
echo [OK] Docker Desktop is running.
echo.

:: 2. Ensure fabric_test network exists
echo [2/4] Initializing Docker network for blockchain and gateway...
docker network inspect fabric_test >nul 2>&1
if %ERRORLEVEL% neq 0 (
    docker network create fabric_test >nul 2>&1
)
echo [OK] fabric_test network is ready.
echo.

:: 3. Start Hyperledger Fabric Network (Orderers, Peers, CAs)
echo [3/4] Starting Hyperledger Fabric blockchain nodes...
set DOCKER_SOCK=/var/run/docker.sock
docker compose -f "Blockchain Network/fabric-samples/test-network/compose/compose-test-net.yaml" -f "Blockchain Network/fabric-samples/test-network/compose/docker/docker-compose-test-net.yaml" -f "Blockchain Network/fabric-samples/test-network/compose/compose-ca.yaml" up -d
if %ERRORLEVEL% neq 0 (
    echo [WARNING] Failed to start one or more Fabric nodes. Checking running status...
) else (
    echo [OK] Hyperledger Fabric nodes started successfully.
)
echo.

:: 4. Start Application Stack (Database, AI Service, Core Gateway, Frontend UI)
echo [4/4] Starting Application Services (Gateway, Frontend, AI, Database)...
docker-compose up -d --no-build
if %ERRORLEVEL% neq 0 (
    echo [WARNING] Docker Compose reported an error. Attempting start with default build...
    docker-compose up -d
)
echo.

:: 5. Print Status and Access URLs
echo ========================================================
echo               All Services Are Running!
echo ========================================================
echo.
echo  Application Endpoints:
echo    - Frontend Web UI:      http://localhost:3000
echo    - Core Gateway (API):   http://localhost:8080
echo    - AI Biometric Service: http://localhost:8000
echo    - PostgreSQL Database:  localhost:5432
echo.
echo  Blockchain Network Nodes:
echo    - Fabric Orderer:       localhost:7050
echo    - Fabric Peer Org1:     localhost:7051
echo    - Fabric Peer Org2:     localhost:9051
echo    - Fabric CA Org1:       localhost:7054
echo    - Fabric CA Org2:       localhost:8054
echo    - Fabric CA Orderer:    localhost:9054
echo.
echo  Demo Credentials:
echo    - Voter National ID:    US-98420-A
echo    - Voter Password:       DemoVoterPass2026!
echo    - Admin Username:       admin
echo    - Admin Password:       Configured in your .env file
echo.
echo ========================================================
pause
