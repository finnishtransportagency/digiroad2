if [%3]==[] sbt "project digiroad2-oracle" "test:run-main fi.liikennevirasto.digiroad2.util.TierekisteriDataImporter %1 %2"
if [%3]<>[] sbt %3 "project digiroad2-oracle" "test:run-main fi.liikennevirasto.digiroad2.util.TierekisteriDataImporter %1 %2"
