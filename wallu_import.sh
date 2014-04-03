#!/usr/bin/env bash
./sbt ${1} 'project digiroad2' "runMain fi.liikennevirasto.digiroad2.util.WalluImport"
zip vallu_import vallu_import.csv
set -- $(<ftp.conf)
curl -u $1:$2 -T vallu_import.zip ftp://$3/vallu_import.zip
date "+%Y%m%d%H%M%S" >> flag.txt
curl -u $1:$2 -T flag.txt ftp://$3/flag.txt
