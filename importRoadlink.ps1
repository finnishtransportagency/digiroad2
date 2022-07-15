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

$truncateroadlink   = "psql -c 'truncate roadlink;' ${destinationpoint}"
$truncatecomplimentary  = "psql -c 'truncate roadlinkex;' ${destinationpoint}"

$roadlink1 = "psql -c 'COPY (SELECT * FROM roadlink WHERE municipalitycode in (${municipalities})) TO STDOUT (ENCODING ''UTF8'');' ${datasource} | Out-File -FilePath .\tempSQL.sql -Encoding 'UTF8'"
$roadlink2 = "psql -c '\COPY roadlink FROM ''${absolutePath}\tempSQL.sql'';' ${destinationpoint}"

$complimentary1 = "psql -c 'COPY (SELECT * FROM roadlinkex WHERE municipalitycode in (${municipalities})) TO STDOUT (ENCODING ''UTF8'');' ${datasource} | Out-File -FilePath .\tempSQL2.sql -Encoding 'UTF8'"
$complimentary2 = "psql -c '\COPY roadlinkex FROM ''${absolutePath}\tempSQL2.sql'';' ${destinationpoint}"

if ($truncateBoolean){
    Write-Output "truncate"
    Invoke-expression $truncateroadlink
    Invoke-expression $truncatecomplimentary
    Write-Output "importing ${municipalities}"
    Invoke-expression $roadlink1
    Invoke-expression $roadlink2
    Invoke-expression $complimentary1
    Invoke-expression $complimentary2
    Write-Output "finish"
}else{
    Write-Output "importing ${municipalities}"
    Invoke-expression $roadlink1
    Invoke-expression $roadlink2

    Invoke-expression $complimentary1
    Invoke-expression $complimentary2
    Write-Output "finish"
}