$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$dir = Join-Path $PSScriptRoot "lib\html"
New-Item $dir -ItemType Directory -Force | Out-Null
$version = "1.19.1"
$name = "jsoup-$version.jar"
$target = Join-Path $dir $name
if (-not (Test-Path $target)) {
    $url = "https://repo1.maven.org/maven2/org/jsoup/jsoup/$version/$name"
    Write-Host "Downloading $name..."
    & curl.exe -L --fail --retry 2 -o $target $url
    if ($LASTEXITCODE -ne 0) { throw "Failed to download $name" }
}
Write-Host "Jsoup ready: $target"