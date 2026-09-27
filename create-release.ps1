param(
    [Parameter(Mandatory = $true)][string]$Token,
    [string]$Tag = 'v1.0.0',
    [string]$NotesFile = 'RELEASE.md',
    [string]$Prox = ''
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-Location $PSScriptRoot

$repo = 'Yuyaniel/music-artist-cover'
$apk = 'app\build\outputs\apk\release\app-release.apk'
$assetName = "music-artist-cover-$Tag.apk"

if (-not (Test-Path $apk)) { throw "APK not found: $apk (run build-apk.cmd first)" }
if (-not (Test-Path $NotesFile)) { throw "Release notes not found: $NotesFile" }

$headers = @{
    Authorization = "Bearer $Token"
    'User-Agent'  = 'music-artist-cover'
}

$common = @{}
if ($Prox -ne '') { $common['Proxy'] = $Prox }

# 必须以 [string] 强转：Get-Content 返回的字符串带 PSPath 等文件属性，
# 直接进 ConvertTo-Json 会变成 {"value": ...} 对象，GitHub 会拒收。
[string]$body = Get-Content -Raw -Encoding UTF8 $NotesFile

$payload = @{
    tag_name   = $Tag
    name       = $Tag
    body       = $body
    draft      = $false
    prerelease = $false
} | ConvertTo-Json -Depth 4

$bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)

Write-Host "[1/2] create release $Tag ..."
$release = Invoke-RestMethod -Method Post `
    -Uri "https://api.github.com/repos/$repo/releases" `
    -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes @common
Write-Host "      id  = $($release.id)"
Write-Host "      url = $($release.html_url)"

Write-Host "[2/2] upload $assetName ..."
$uploadUrl = "https://uploads.github.com/repos/$repo/releases/$($release.id)/assets?name=$assetName"
$asset = Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers $headers `
    -ContentType 'application/vnd.android.package-archive' -InFile $apk @common
Write-Host "      size = $([math]::Round($asset.size / 1MB, 2)) MB"

Write-Host ""
Write-Host "done: $($release.html_url)"
