param(
    [string]$Server = "179.236.81.150",
    [string]$IdentityFile = "$HOME\.ssh\id_ed25519"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $projectRoot "target\connectify-solutions-0.0.1-SNAPSHOT.jar"

if (-not (Test-Path -LiteralPath $jar)) {
    throw "No existe el JAR. Ejecuta primero: mvn clean package"
}

scp -i $IdentityFile $jar "root@${Server}:/opt/connectify/connectify.jar.new"
ssh -i $IdentityFile "root@${Server}" "chown connectify:connectify /opt/connectify/connectify.jar.new && mv /opt/connectify/connectify.jar.new /opt/connectify/connectify.jar && systemctl restart connectify"

Write-Host "Connectify actualizado en $Server"
