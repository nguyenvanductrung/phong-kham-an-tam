param([string]$OutputDirectory = (Join-Path (Split-Path -Parent $PSScriptRoot) ("backups\" + (Get-Date -Format 'yyyyMMdd-HHmmss'))))
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root 'infrastructure\compose\docker-compose.yml'
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Missing .env. Run scripts/bootstrap.ps1 first.' }
$outputPath = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$composeArgs = @('--context','default','compose','--env-file',$envFile,'-f',$compose)
$databases = @('identity_db','patient_db','scheduling_db','clinical_db','notification_db')
foreach ($database in $databases) {
    $containerPath = "/tmp/$database.dump"
    & docker @composeArgs exec -T postgres pg_dump -U clinic_admin -Fc -d $database -f $containerPath
    if ($LASTEXITCODE -ne 0) { throw "pg_dump failed for $database" }
    & docker @composeArgs cp "postgres:$containerPath" (Join-Path $outputPath "$database.dump")
    if ($LASTEXITCODE -ne 0) { throw "Copying backup failed for $database" }
    & docker @composeArgs exec -T postgres rm -f $containerPath
}
$backupVolume = "${outputPath}:/backup"
$mirrorCommand = 'mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" && mc mirror --overwrite local/clinical-files /backup/clinical-files'
& docker @composeArgs run --rm --volume $backupVolume --entrypoint /bin/sh minio-client -ec $mirrorCommand
if ($LASTEXITCODE -ne 0) { throw 'MinIO object backup failed' }
Write-Host "Backup saved to $outputPath"
