# Run a command in the Folkhold dev container. With no command, open a shell.
# Examples:
#   .\scripts\dev.ps1
#   .\scripts\dev.ps1 ./gradlew build
#
# If Docker Desktop is installed but does not run, the script starts it, waits until the engine is ready,
# runs the command, then stops Docker Desktop again. To keep Docker Desktop running after the command,
# set the environment variable FOLKHOLD_KEEP_DOCKER to 1:
#   $env:FOLKHOLD_KEEP_DOCKER = '1'

$requirements = @'
Prerequisites (see docs/getting-started.md):
  - Docker Desktop with the WSL 2 back end: https://www.docker.com/products/docker-desktop/
  - Git: https://git-scm.com/downloads
'@

function Test-DockerEngine {
    docker info *> $null
    return $LASTEXITCODE -eq 0
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host 'Docker is not installed (the docker command is not available).' -ForegroundColor Red
    Write-Host $requirements
    exit 1
}

$startedDocker = $false
if (-not (Test-DockerEngine)) {
    $desktopExe = Join-Path $env:ProgramFiles 'Docker\Docker\Docker Desktop.exe'
    if (-not (Test-Path $desktopExe)) {
        Write-Host 'The Docker engine does not run, and Docker Desktop is not at its default location.' -ForegroundColor Red
        Write-Host 'Start Docker Desktop, then run the command again.'
        Write-Host $requirements
        exit 1
    }
    Write-Host 'Docker Desktop does not run. The script starts it...' -ForegroundColor Yellow
    Start-Process -FilePath $desktopExe
    $startedDocker = $true
    $deadline = (Get-Date).AddMinutes(3)
    while (-not (Test-DockerEngine)) {
        if ((Get-Date) -gt $deadline) {
            Write-Host 'The Docker engine is not ready after 3 minutes. Check Docker Desktop, then try again.' -ForegroundColor Red
            exit 1
        }
        Start-Sleep -Seconds 3
    }
    Write-Host 'The Docker engine is ready.' -ForegroundColor Green
}

$exitCode = 1
Push-Location (Join-Path $PSScriptRoot '..')
try {
    docker compose run --rm dev @args
    $exitCode = $LASTEXITCODE
}
finally {
    Pop-Location
    if ($startedDocker -and $env:FOLKHOLD_KEEP_DOCKER -ne '1') {
        Write-Host 'The script started Docker Desktop: it stops it now.' -ForegroundColor Yellow
        docker desktop stop *> $null
        if ($LASTEXITCODE -ne 0) {
            # Docker Desktop versions before 4.37 have no "docker desktop stop" command.
            Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue | Stop-Process
        }
    }
}
exit $exitCode
