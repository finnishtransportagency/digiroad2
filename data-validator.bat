if [%2]==[] sbt "project digiroad2-oracle" "test:run-main fi.liikennevirasto.digiroad2.util.AssetValidatorProcess %1"
if [%2]<>[] sbt %2 "project digiroad2-oracle" "test:run-main fi.liikennevirasto.digiroad2.util.AssetValidatorProcess %1"
