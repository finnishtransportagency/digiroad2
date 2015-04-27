#!/usr/bin/env bash
set -e
./sbt ${1} 'project digiroad2' "runMain fi.liikennevirasto.digiroad2.util.ValluExport"
zip digiroad_stops digiroad_stops.csv
set -- $(<ftp.conf)
curl -u $1:$2 -T digiroad_stops.zip ftp://$3/all.zip
date "+%Y%m%d%H%M%S" > flag.txt
curl -u $1:$2 -T flag.txt ftp://$3/flag.txt
VALLU_HISTORY_PATH=$HOME/vallu_export_history
test -d $VALLU_HISTORY_PATH || mkdir $VALLU_HISTORY_PATH
mv digiroad_stops.zip $VALLU_HISTORY_PATH/digiroad_stops_$(date +%Y%m%d%H%M%S).zip
