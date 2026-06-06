param(
    [switch]$SkipJarBuild
)

$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $projectRoot

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker не найден в PATH. Установите Docker Desktop и перезапустите PowerShell."
}

if (-not (Test-Path "cookie.txt")) {
    throw "Файл cookie.txt не найден в корне проекта."
}

if (-not (Test-Path "proxies.txt")) {
    throw "Файл proxies.txt не найден в корне проекта."
}

if (-not $SkipJarBuild) {
    $env:GRADLE_USER_HOME = Join-Path (Get-Location) ".gradle-user-home"
    .\gradlew.bat clean bootJar -x test --no-daemon
}

if (-not (Test-Path "build\libs\StockForecasting-1.0-SNAPSHOT.jar")) {
    throw "JAR не найден: build\libs\StockForecasting-1.0-SNAPSHOT.jar"
}

docker compose -f docker-compose.jar.yml up --build -d
docker compose -f docker-compose.jar.yml ps

Write-Host ""
Write-Host "Приложение: http://localhost:8080"
Write-Host "Админ по умолчанию: admin / admin"
Write-Host "Логи: docker compose -f docker-compose.jar.yml logs -f app"
