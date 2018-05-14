#!/bin/sh
./sbt ${2} 'project digiroad2-oracle' "test:run-main fi.liikennevirasto.viite.util.DataFixture ${1}"
