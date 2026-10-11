<#
.SYNOPSIS
Copies the Folkhold mod jars into a Prism Launcher instance.

.DESCRIPTION
Build the mods first (.\scripts\dev.ps1 ./gradlew build).
The script removes the old folkhold_*.jar files from the instance, then copies the new jars.

.EXAMPLE
.\scripts\deploy-client.ps1 -Instance "$env:APPDATA\PrismLauncher\instances\Folkhold-dev"
#>
param(
    [Parameter(Mandatory = $true)]
    [string] $Instance
)

$ErrorActionPreference = 'Stop'
$root = Resolve-Path (Join-Path $PSScriptRoot '..')

# Prism uses 'minecraft' (new instances) or '.minecraft' (old instances).
$gameDir = @('minecraft', '.minecraft') |
    ForEach-Object { Join-Path $Instance $_ } |
    Where-Object { Test-Path $_ } |
    Select-Object -First 1
if (-not $gameDir) {
    throw "No 'minecraft' or '.minecraft' folder in '$Instance'. Start the instance one time in Prism Launcher first."
}

$modsDir = Join-Path $gameDir 'mods'
New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
Get-ChildItem -Path $modsDir -Filter 'folkhold_*.jar' | Remove-Item

$jars = Get-ChildItem -Path (Join-Path $root '*\build\libs\folkhold_*.jar') |
    Where-Object { $_.Name -notlike '*-sources.jar' }
if (-not $jars) {
    throw "No jar found. Run '.\scripts\dev.ps1 ./gradlew build' first."
}
$jars | Copy-Item -Destination $modsDir
$jars | ForEach-Object { Write-Host "Copied $($_.Name) to $modsDir" }
