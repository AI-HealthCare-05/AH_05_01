param([switch]$Offline, [switch]$PrepareOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$rootPrefix = $projectRoot.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
$manifestPath = Join-Path $PSScriptRoot 'legacy-sensor-files.json'
if (!(Test-Path -LiteralPath (Join-Path $projectRoot 'android/gradlew.bat'))) {
    throw 'PR #21 저장소 루트에 ZIP 내용물을 먼저 덮어써 주세요.'
}
if (!(Test-Path -LiteralPath $manifestPath)) { throw 'legacy-sensor-files.json 파일이 없습니다.' }

function Get-SourceHash([string]$Path, [bool]$IsText) {
    $data = [IO.File]::ReadAllBytes($Path)
    if ($IsText) { $data = [Text.Encoding]::UTF8.GetBytes([Text.Encoding]::UTF8.GetString($data).Replace("`r`n", "`n")) }
    $hasher = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($hasher.ComputeHash($data))).Replace('-', '').ToLowerInvariant() }
    finally { $hasher.Dispose() }
}

$obsolete = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$ready = @()
foreach ($item in $obsolete) {
    $target = [IO.Path]::GetFullPath((Join-Path $projectRoot $item.path))
    if (!$target.StartsWith($rootPrefix, [StringComparison]::OrdinalIgnoreCase) -or $item.path -notlike 'android/*') {
        throw '정리 대상 경로가 Android 소스 범위를 벗어났습니다.'
    }
    # 링크를 경유하여 저장소 밖 파일을 제거하지 않는다.
    $parent = $target
    while ($parent.Length -gt $projectRoot.Length) {
        if ((Test-Path -LiteralPath $parent) -and ((Get-Item -LiteralPath $parent -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw "링크 경로는 자동 정리하지 않습니다: $parent"
        }
        $parent = Split-Path -Parent $parent
    }
    if (Test-Path -LiteralPath $target) {
        $hash = Get-SourceHash $target $item.text
        if ($hash -notin $item.allowed_sha256) { throw "팀원이 수정한 이전 파일입니다. 직접 병합해 주세요: $($item.path)" }
        $ready += @{ Path = $target; Relative = $item.path; Hash = $hash; IsText = $item.text }
    }
}
if ($ready.Count -gt 0) {
    $backupRoot = Join-Path $projectRoot ('.handoff-backups/sensor-' + (Get-Date -Format 'yyyyMMdd-HHmmss-ffff'))
    foreach ($item in $ready) {
        if ((Get-SourceHash $item.Path $item.IsText) -ne $item.Hash) { throw '검사 후 파일이 변경되어 정리를 중단했습니다.' }
        $backup = Join-Path $backupRoot $item.Relative
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $backup) | Out-Null
        Copy-Item -LiteralPath $item.Path -Destination $backup
    }
    foreach ($item in $ready) { Remove-Item -LiteralPath $item.Path }
    Write-Host "폐기된 센서·카드 파일 $($ready.Count)개 백업 후 정리: $backupRoot"
}
if ($PrepareOnly) { Write-Host '덮어쓰기 소스 준비 완료'; exit 0 }
Push-Location (Join-Path $projectRoot 'android')
try {
    $buildArgs = @(':app:assembleDebug', '--no-configuration-cache', '--console=plain')
    if ($Offline) { $buildArgs += '--offline' }
    & .\gradlew.bat @buildArgs
    if ($LASTEXITCODE -ne 0) { throw 'APK 빌드에 실패했습니다. 위 오류를 확인해 주세요.' }
    Write-Host "일반 앱 APK: $(Join-Path $projectRoot 'android/app/build/outputs/apk/debug/app-debug.apk')"
} finally { Pop-Location }
