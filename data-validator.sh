#!/bin/sh
./sbt ${2} 'project digiroad2-oracle' "test:run-main fi.liikennevirasto.digiroad2.util.AssetValidatorProcess ${1}"
