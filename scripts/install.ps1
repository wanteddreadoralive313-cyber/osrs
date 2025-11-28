Param(
    [string]$DreamBotHome = "$env:USERPROFILE\\DreamBot",
    [string]$ScriptsDir = ""
)

if (-not $ScriptsDir) {
    $ScriptsDir = Join-Path $DreamBotHome "Scripts"
}

$RootDir = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Definition)
$JarName = "roguesden-1.0-SNAPSHOT-jar-with-dependencies.jar"

if (-not (Test-Path $DreamBotHome)) {
    Write-Host "Creating DreamBot directory at $DreamBotHome"
    New-Item -ItemType Directory -Force -Path $DreamBotHome | Out-Null
}
if (-not (Test-Path $ScriptsDir)) {
    New-Item -ItemType Directory -Force -Path $ScriptsDir | Out-Null
}

& "$RootDir\mvnw.cmd" -B -DskipTests package

$JarPath = Join-Path $RootDir "target" | Join-Path -ChildPath $JarName
if (-not (Test-Path $JarPath)) {
    Write-Error "Build succeeded but $JarPath was not found."
    exit 1
}

Copy-Item -Force $JarPath $ScriptsDir
Write-Host "Copied $JarPath to $ScriptsDir"
