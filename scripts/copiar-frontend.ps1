param(
    [string]$Dist
)

$ErrorActionPreference = "Stop"

$raiz = Split-Path -Parent $PSScriptRoot

if (-not $Dist) {
    $hermano = Join-Path (Split-Path -Parent $raiz) "taller-mecanico-frontend\dist\frontend\browser"
    $Dist = $hermano
}

if (-not (Test-Path (Join-Path $Dist "index.html"))) {
    throw "No se encontro el build de Angular en $Dist. Ejecute 'npm.cmd run build' en el repositorio del frontend o pase -Dist."
}

$static = Join-Path $raiz "src\main\resources\static"
Write-Host "Copiando frontend compilado..." -ForegroundColor Cyan
Write-Host "  Desde: $Dist"
Write-Host "  Hacia: $static"

if (Test-Path $static) {
    $esperado = [System.IO.Path]::GetFullPath((Join-Path $raiz "src\main\resources\static"))
    $destinoReal = (Resolve-Path -LiteralPath $static).Path
    if ($destinoReal -ne $esperado -or (Get-Item -LiteralPath $static).LinkType) {
        throw "El destino de los archivos estaticos no es una carpeta local del backend."
    }
    Remove-Item -LiteralPath $destinoReal -Recurse -Force
}
New-Item -ItemType Directory -Path $static -Force | Out-Null
Copy-Item (Join-Path $Dist "*") $static -Recurse -Force

$archivos = (Get-ChildItem $static -Recurse -File).Count
Write-Host "Listo: $archivos archivos copiados." -ForegroundColor Green
Write-Host "Recompile el backend para incluirlos en el jar." -ForegroundColor Yellow
