#!/bin/sh
./sbt ${1} 'project digiroad2' "run-main fi.liikennevirasto.digiroad2.util.UpdateIncompleteLinkList"
