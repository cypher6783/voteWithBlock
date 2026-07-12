# generate-secret.ps1
# Generates a 64-character cryptographically secure random secret

$length = 64
$bytes = New-Object byte[] $length
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$secret = [Convert]::ToBase64String($bytes).Substring(0, $length)

Write-Host "Your 64-character secure secret:"
Write-Host $secret -ForegroundColor Green
