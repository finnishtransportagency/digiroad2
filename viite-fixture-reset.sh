#!/bin/sh
./sbt ${2} 'project digiroad2-viite' "test:run-main fi.liikennevirasto.viite.util.DataFixture ${1}"
