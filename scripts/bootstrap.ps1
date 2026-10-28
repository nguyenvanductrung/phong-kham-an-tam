param([switch]$Quiet)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $root '.env'
$examplePath = Join-Path $root '.env.example'
if (-not (Test-Path -LiteralPath $envPath)) { Copy-Item -LiteralPath $examplePath -Destination $envPath }
$content = Get-Content -Raw -LiteralPath $envPath
function New-Secret([int]$bytes = 32) {
    $buffer = New-Object byte[] $bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
    return [Convert]::ToBase64String($buffer).TrimEnd('=').Replace('+','-').Replace('/','_')
}
function Set-EnvValue([string]$name,[string]$value) {
    $script:content = [regex]::Replace($script:content,"(?m)^$name=.*$",[System.Text.RegularExpressions.MatchEvaluator]{ param($m) "$name=$value" })
}
function Ensure-Secret([string]$name,[string]$value) {
    if ($script:content -match "(?m)^$name=(GENERATE_WITH_scripts_bootstrap\.ps1)?$") { Set-EnvValue $name $value }
}
Ensure-Secret 'POSTGRES_PASSWORD' (New-Secret)
Ensure-Secret 'IDENTITY_DB_PASSWORD' (New-Secret)
Ensure-Secret 'PATIENT_DB_PASSWORD' (New-Secret)
Ensure-Secret 'SCHEDULING_DB_PASSWORD' (New-Secret)
Ensure-Secret 'CLINICAL_DB_PASSWORD' (New-Secret)
Ensure-Secret 'NOTIFICATION_DB_PASSWORD' (New-Secret)
Ensure-Secret 'REDIS_PASSWORD' (New-Secret)
Ensure-Secret 'RABBITMQ_PASSWORD' (New-Secret)
Ensure-Secret 'MINIO_ROOT_PASSWORD' (New-Secret)
Ensure-Secret 'DEMO_LOGIN_PASSWORD' ("Antam-" + [Convert]::ToHexString([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(8)))
if ($script:content -match '(?m)^IDENTITY_JWT_PRIVATE_KEY=$') {
    $rsa = [System.Security.Cryptography.RSA]::Create(2048)
    try {
        Set-EnvValue 'IDENTITY_JWT_PRIVATE_KEY' ([Convert]::ToBase64String($rsa.ExportPkcs8PrivateKey()))
        Set-EnvValue 'IDENTITY_JWT_PUBLIC_KEY' ([Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo()))
    } finally { $rsa.Dispose() }
}
Set-Content -LiteralPath $envPath -Value $script:content -Encoding utf8
$demoPassword = ([regex]::Match($script:content,'(?m)^DEMO_LOGIN_PASSWORD=(.*)$')).Groups[1].Value
if (-not $Quiet) {
    Write-Host "Đã tạo cấu hình local trong .env. Mật khẩu demo: $demoPassword"
    Write-Host 'Tài khoản demo: receptionist@antam.local, doctor@antam.local, patient@antam.local, manager@antam.local'
    Write-Host 'Giữ .env riêng tư; không commit tệp này.'
}

