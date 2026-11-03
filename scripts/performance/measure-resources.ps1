[CmdletBinding()]
param(
    [ValidateRange(1, 86400)]
    [int]$DurationSeconds = 600,
    [ValidateRange(1, 300)]
    [int]$IntervalSeconds = 5,
    [string]$OutputPath = ""
)

$ErrorActionPreference = "Stop"

if (-not $OutputPath) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $OutputPath = Join-Path (Get-Location) "docs/performance/results/resources-$stamp.csv"
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
if (Test-Path -LiteralPath $resolvedOutput) {
    throw "Tệp đầu ra đã tồn tại; chọn đường dẫn mới để tránh ghi đè: $resolvedOutput"
}

$dockerVersion = docker version --format "{{.Server.Version}}" 2>$null
if ($LASTEXITCODE -ne 0) {
    throw "Không kết nối được Docker Engine. Hãy khởi động Docker Desktop rồi thử lại."
}

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$start = [DateTimeOffset]::UtcNow
$end = $start.AddSeconds($DurationSeconds)
$rows = [System.Collections.Generic.List[object]]::new()
$hasHeader = $false

while ([DateTimeOffset]::UtcNow -lt $end) {
    $capturedAt = [DateTimeOffset]::UtcNow.ToString("o")
    $os = Get-CimInstance Win32_OperatingSystem
    $freeRamMiB = [math]::Round($os.FreePhysicalMemory / 1024, 1)
    $wslProcesses = @(Get-Process -Name "vmmemWSL" -ErrorAction SilentlyContinue)
    $wslRamMiB = if ($wslProcesses.Count) {
        [math]::Round((($wslProcesses | Measure-Object -Property WorkingSet64 -Sum).Sum) / 1MB, 1)
    } else { $null }

    $statsJson = docker stats --no-stream --format "{{json .}}" 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "docker stats thất bại trong lúc đo; dữ liệu chưa được lưu đầy đủ."
    }

    foreach ($line in $statsJson) {
        if (-not $line) { continue }
        $stat = $line | ConvertFrom-Json
        $memoryMatch = [regex]::Match([string]$stat.MemUsage, '^\s*(?<value>[\d.]+)\s*(?<unit>KiB|MiB|GiB|kB|MB|GB|B)')
        $memoryMiB = $null
        if ($memoryMatch.Success) {
            $value = [double]::Parse($memoryMatch.Groups['value'].Value, [Globalization.CultureInfo]::InvariantCulture)
            $factor = switch -Regex ($memoryMatch.Groups['unit'].Value) {
                '^(KiB|kB)$' { 1 / 1024; break }
                '^(GiB|GB)$' { 1024; break }
                '^B$' { 1 / 1MB; break }
                default { 1 }
            }
            $memoryMiB = [math]::Round($value * $factor, 2)
        }

        $inspectJson = docker inspect --format '{{.Config.Image}}|{{.Id}}' $stat.ID 2>$null
        $image = ""
        $imageId = ""
        if ($LASTEXITCODE -eq 0 -and $inspectJson) {
            $image, $imageId = $inspectJson -split '\|', 2
        }

        $rows.Add([pscustomobject]@{
            timestamp_utc = $capturedAt
            docker_server_version = $dockerVersion
            container = $stat.Name
            container_id = $stat.ID
            image = $image
            image_id = $imageId
            memory_usage = $stat.MemUsage
            memory_mib = $memoryMiB
            memory_percent = $stat.MemPerc
            cpu_percent = $stat.CPUPerc
            pids = $stat.PIDs
            windows_free_ram_mib = $freeRamMiB
            wsl_working_set_mib = $wslRamMiB
        })
    }

    if ($rows.Count -gt 0) {
        if ($hasHeader) {
            $rows | Export-Csv -LiteralPath $resolvedOutput -NoTypeInformation -Encoding utf8 -Append
        } else {
            $rows | Export-Csv -LiteralPath $resolvedOutput -NoTypeInformation -Encoding utf8
            $hasHeader = $true
        }
    }
    $rows.Clear()
    Start-Sleep -Seconds $IntervalSeconds
}

Write-Host "Đã lưu số đo tài nguyên vào $resolvedOutput"
