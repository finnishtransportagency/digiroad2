#!/bin/sh
./sbt ${3} 'project digiroad2-oracle' "test:run-main fi.liikennevirasto.digiroad2.util.DataFixture ${1} ${2}"
