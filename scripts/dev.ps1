# Run a command in the Folkhold dev container. With no command, open a shell.
# Examples:
#   .\scripts\dev.ps1
#   .\scripts\dev.ps1 ./gradlew build
Push-Location (Join-Path $PSScriptRoot '..')
try {
    docker compose run --rm dev @args
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
