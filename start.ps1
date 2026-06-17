# ============================================================================
#  Arranca el microservicio (demo) y la Spring Cloud Gateway a la vez.
#  Cada uno en su propia ventana para que se vean los dos logs por separado.
#
#  Uso:   .\start.ps1
# ============================================================================

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

Write-Host "==> Arrancando MICROSERVICIO (demo) en http://localhost:8080 ..." -ForegroundColor Cyan
Start-Process -FilePath "cmd.exe" -ArgumentList @(
    "/k",
    "title MICROSERVICIO (demo:8080) && cd /d `"$root\demo`" && mvnw.cmd spring-boot:run"
)

# Pequena espera para que el micro empiece a levantar antes que la gateway.
Start-Sleep -Seconds 3

Write-Host "==> Arrancando GATEWAY en http://localhost:8081 ..." -ForegroundColor Cyan
Start-Process -FilePath "cmd.exe" -ArgumentList @(
    "/k",
    "title GATEWAY (8081) && cd /d `"$root\gateway`" && mvnw.cmd spring-boot:run"
)

Write-Host ""
Write-Host "------------------------------------------------------------" -ForegroundColor Green
Write-Host " Todo arrancando. Pruebas (cuando ambos pongan 'Started'):" -ForegroundColor Green
Write-Host ""
Write-Host "   Acceso DIRECTO al micro   : http://localhost:8080/api/ingredients"
Write-Host "   A traves del GATEWAY      : http://localhost:8081/api/ingredients"
Write-Host ""
Write-Host " Las dos URLs devuelven lo mismo: el Gateway reenvia /api/** al micro."
Write-Host " Cierra cada ventana (o Ctrl+C en ella) para parar cada servicio."
Write-Host "------------------------------------------------------------" -ForegroundColor Green
