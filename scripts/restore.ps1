param([Parameter(Mandatory=$true)][string]$BackupDirectory)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$compose = Join-Path $root 'infrastructure\compose\docker-compose.yml'
$envFile = Join-Path $root '.env'
if (-not (Test-Path -LiteralPath $envFile)) { throw 'Missing .env. Run scripts/bootstrap.ps1 first.' }
$backupPath = [System.IO.Path]::GetFullPath($BackupDirectory)
$composeArgs = @('--context','default','compose','--env-file',$envFile,'-f',$compose)
$databases = @('identity_db','patient_db','scheduling_db','clinical_db','notification_db')
foreach ($database in $databases) {
    $dump = Join-Path $backupPath "$database.dump"
    if (-not (Test-Path -LiteralPath $dump)) { throw "Missing backup file: $dump" }
    $containerPath = "/tmp/$database.restore.dump"
    & docker @composeArgs cp $dump "postgres:$containerPath"
    if ($LASTEXITCODE -ne 0) { throw "Copying backup failed for $database" }
    & docker @composeArgs exec -T postgres pg_restore -U clinic_admin --clean --if-exists -d $database $containerPath
    if ($LASTEXITCODE -ne 0) { throw "Restore failed for $database" }
    & docker @composeArgs exec -T postgres rm -f $containerPath
}
$backupVolume = "${backupPath}:/backup:ro"
$mirrorCommand = 'mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" && mc mirror --overwrite /backup/clinical-files local/clinical-files'
& docker @composeArgs run --rm --volume $backupVolume --entrypoint /bin/sh minio-client -ec $mirrorCommand
if ($LASTEXITCODE -ne 0) { throw 'MinIO object restore failed' }
Write-Host "Database and object restore completed from $backupPath"
