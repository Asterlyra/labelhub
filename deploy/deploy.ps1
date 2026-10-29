$RootDir = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $RootDir "deploy/docker.env"
$ExampleFile = Join-Path $RootDir "deploy/docker.env.example"

if (-not (Test-Path $EnvFile)) {
    Copy-Item $ExampleFile $EnvFile
    Write-Host "Created $EnvFile from template. Review it before the next deployment if needed."
}

Set-Location $RootDir
docker compose --env-file $EnvFile build
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

docker compose --profile migrate --env-file $EnvFile run --rm agent-migrate
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

docker compose --env-file $EnvFile up -d
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
