param(

    [Parameter()]
    [string]$sourcePassword,
    [Parameter()]
    [string]$sourceUser,

    [Parameter()]
    [string]$sourceDB,

    [Parameter()]
    [string]$sourcePort,

    [Parameter()]
    [string]$destinationPassword,

    [Parameter()]
    [string]$municipalities,

    [Parameter()]
    [Boolean]$truncateBoolean
)

# Before running this script set your postgress installation bin(C:\'Program Files'\PostgreSQL\13\bin\) folder into PATH env

$absolutePath = Get-Location
# .\importRoadlink.ps1 -municipalities  "20,10" -sourceUser digiroad2dbuser -sourcePasstword password  -sourceDB digiroad2 -sourcePort 9999 -destinationPastword digiroad2 -truncateBoolean 1
$datasource = "postgresql://${sourceUser}:${sourcePassword}@localhost:${sourcePort}/${sourceDB}"
$destinationpoint = "postgresql://digiroad2:${destinationPassword}@localhost:5432/digiroad2"
$truncate = "psql -c 'truncate roadlink;' ${destinationpoint}"
$command = "psql -c 'COPY (SELECT * FROM roadlink WHERE municipalitycode in (${municipalities})) TO STDOUT (ENCODING ''UTF8'');' ${datasource} | Out-File -FilePath .\tempSQL.sql -Encoding 'UTF8'"
$command2 = "psql -c '\COPY roadlink FROM ''${absolutePath}\tempSQL.sql'';' ${destinationpoint}"
if ($truncateBoolean){
    Write-Output "truncate"
    Invoke-expression $truncate
    Write-Output "importing ${municipalities}"
    Invoke-expression $command
    Invoke-expression $command2
    Write-Output "finish"
}else{
    Write-Output "importing ${municipalities}"
    Invoke-expression $command
    Invoke-expression $command2
    Write-Output "finish"
}