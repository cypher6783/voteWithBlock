# start-all.ps1
# Starts the Votetrust Ledger application stack via Docker Desktop (Windows).
# NOTE: Hyperledger Fabric must be started separately using Git Bash or WSL2
# with Docker integration enabled in Docker Desktop settings.

Write-Host "======================================" -ForegroundColor Cyan
Write-Host " Votetrust Ledger - Startup Script" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Cyan

# Check Docker Desktop is running
Write-Host "`nChecking Docker Desktop is running..." -ForegroundColor Yellow
try {
    docker info | Out-Null
    Write-Host "Docker Desktop is running." -ForegroundColor Green
} catch {
    Write-Error "Docker Desktop is not running or not accessible. Please start Docker Desktop and try again."
    exit 1
}

# Print Fabric instructions (must be done manually via WSL/Git Bash with Docker integration enabled)
Write-Host "`n[Optional] Hyperledger Fabric Network:" -ForegroundColor Yellow
Write-Host "  If you want the blockchain layer, open Git Bash or WSL2 and run:" -ForegroundColor Gray
Write-Host "  cd 'Blockchain Network/fabric-samples/test-network'" -ForegroundColor Gray
Write-Host "  ./network.sh up createChannel -c mychannel -ca" -ForegroundColor Gray
Write-Host "  (Requires WSL2 Docker integration enabled in Docker Desktop settings)" -ForegroundColor Gray

Write-Host "`nStarting application services (Gateway, Frontend, AI, Database)..." -ForegroundColor Cyan
docker-compose up --build -d

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nAll services started successfully!" -ForegroundColor Green
    Write-Host "  Frontend:     http://localhost:3000" -ForegroundColor White
    Write-Host "  Core Gateway: http://localhost:8080" -ForegroundColor White
    Write-Host "  AI Service:   http://localhost:8000" -ForegroundColor White
    Write-Host "`nRun 'docker ps' to verify all containers are healthy." -ForegroundColor Gray
} else {
    Write-Error "docker-compose failed. Check the output above for errors."
}
