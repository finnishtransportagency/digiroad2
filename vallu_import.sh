#!/usr/bin/env bash
./sbt ${1} 'project digiroad2' "runMain fi.liikennevirasto.digiroad2.util.ValluImport"
zip digiroad_stops digiroad_stops.csv
set -- $(<ftp.conf)
curl -u $1:$2 -T digiroad_stops.zip ftp://$3/all.zip
date "+%Y%m%d%H%M%S" > flag.txt
curl -u $1:$2 -T flag.txt ftp://$3/flag.txt
