#!/usr/bin/env bash
set -e
JAVA_OPTS="-Xms1512M -Xmx4096m -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=1024M -XX:+HeapDumpOnOutOfMemoryError"
echo YES|./sbt ${1} "project digiroad2-viite" "test:run-main fi.liikennevirasto.viite.util.DataFixture import_road_addresses" > `dirname $0`/logs/road_address_import.log
echo YES|./sbt ${1} 'project digiroad2-viite' "test:run-main fi.liikennevirasto.viite.util.DataFixture update_road_addresses_geometry" > `dirname $0`/logs/address_geometry.log
echo YES|./sbt ${1} "project digiroad2-viite" "test:run-main fi.liikennevirasto.viite.util.DataFixture recalculate_addresses" > `dirname $0`/logs/recalculate_addresses.log
