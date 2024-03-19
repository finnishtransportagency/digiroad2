param(
    [Parameter()][string]$sourcePassword,
    [Parameter()][string]$sourceUser,
    [Parameter()][string]$sourceDB,
    [Parameter()][string]$sourcePort,
    [Parameter()][string]$destinationPassword,
    [Parameter()][string]$municipalities,
    [Parameter()][Boolean]$truncateBoolean
)

# Before running this script set your postgress installation bin(C:\'Program Files'\PostgreSQL\13\bin\) folder into PATH env

$absolutePath = Get-Location
# .\importRoadlink.ps1 -municipalities  "20,10" -sourceUser digiroad2dbuser -sourcePassword password  -sourceDB digiroad2 -sourcePort 9999 -destinationPassword digiroad2 -truncateBoolean 1
$datasource = "postgresql://${sourceUser}:${sourcePassword}@localhost:${sourcePort}/${sourceDB}"
$destinationpoint = "postgresql://digiroad2:${destinationPassword}@localhost:5432/digiroad2"

$truncateroadlink   = "psql -c 'truncate kgv_roadlink;' ${destinationpoint}"
$truncatecomplimentary  = "psql -c 'truncate qgis_roadlinkex;' ${destinationpoint}"
$truncatelinktype   = "psql -c 'truncate link_type;' ${destinationpoint}"
$truncatefunctionalclass   = "psql -c 'truncate functional_class;' ${destinationpoint}"

$roadlink1 = "psql -c '\COPY (SELECT * FROM kgv_roadlink WHERE municipalitycode in (${municipalities})) TO ''${absolutePath}\tempSQL.sql'' (ENCODING ''UTF8'');' ${datasource}"
$roadlink2 = "psql -c '\COPY kgv_roadlink FROM ''${absolutePath}\tempSQL.sql'';' ${destinationpoint}"

$complimentary1 = "psql -c '\COPY (SELECT * FROM qgis_roadlinkex WHERE municipalitycode in (${municipalities})) TO ''${absolutePath}\tempSQL2.sql'' (ENCODING ''UTF8'');' ${datasource}"
$complimentary2 = "psql -c '\COPY qgis_roadlinkex FROM ''${absolutePath}\tempSQL2.sql'';' ${destinationpoint}"

$linktype1 = "psql -c '\COPY (SELECT lt.* FROM link_type lt LEFT JOIN kgv_roadlink kr on kr.linkid = lt.link_id LEFT JOIN qgis_roadlinkex qr on qr.linkid = lt.link_id WHERE kr.municipalitycode in (${municipalities}) or qr.municipalitycode in (${municipalities})) TO ''${absolutePath}\tempSQL3.sql'' (ENCODING ''UTF8'');' ${datasource}"
$linktype2 = "psql -c '\COPY link_type FROM ''${absolutePath}\tempSQL3.sql'';' ${destinationpoint}"

$functionalclass1 = "psql -c '\COPY (SELECT fc.* FROM functional_class fc LEFT JOIN kgv_roadlink kr on kr.linkid = fc.link_id LEFT JOIN qgis_roadlinkex qr on qr.linkid = fc.link_id WHERE kr.municipalitycode in (${municipalities}) or qr.municipalitycode in (${municipalities})) TO ''${absolutePath}\tempSQL4.sql'' (ENCODING ''UTF8'');' ${datasource}"
$functionalclass2 = "psql -c '\COPY functional_class FROM ''${absolutePath}\tempSQL4.sql'';' ${destinationpoint}"
if ($truncateBoolean)
{
    Write-Output "truncate"
    Invoke-expression $truncateroadlink
    Invoke-expression $truncatecomplimentary
    Invoke-expression $truncatelinktype
    Invoke-expression $truncatefunctionalclass
}
Write-Output "importing ${municipalities}"
Invoke-expression $roadlink1
Invoke-expression $roadlink2

Invoke-expression $complimentary1
Invoke-expression $complimentary2

Invoke-expression $linktype1
Invoke-expression $linktype2

Invoke-expression $functionalclass1
Invoke-expression $functionalclass2
Write-Output "finish"