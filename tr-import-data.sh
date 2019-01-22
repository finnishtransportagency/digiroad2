#!/bin/sh
./sbt ${4} 'project digiroad2-oracle' "test:run-main fi.liikennevirasto.digiroad2.util.TierekisteriDataImporter ${1} ${2} ${3}"
