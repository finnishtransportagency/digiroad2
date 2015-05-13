#!/usr/bin/env bash
set -e
./sbt ${1} 'project digiroad2' "runMain fi.liikennevirasto.digiroad2.util.UpdateIncompleteLinkList"

