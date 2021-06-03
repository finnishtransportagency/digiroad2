#!/bin/sh

#
# 1 UpdateIncompleteLinkList
# 2 DataFixture with only task
# 3 DataFixture with task and TRAFFICSIGNGROUP
# 4 AssetValidatorProcess $assetForValidation
# 5 TierekisteriDataImporter  tierekisteriAction tierekisteriAction TRAFFICSIGNGROUP
#

if ! [ $batchenv = "true" ]
  then
    echo "Server mode"
    echo $batchenv
    java $javaParameter -jar /digiroad2.jar
  else
    echo "Batch mode"
    if [ $batchMode = "UpdateIncompleteLinkList" ]; then
      echo "UpdateIncompleteLinkList"
      printf YES | java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.UpdateIncompleteLinkList
    elif [ $batchMode = "DataFixture" ]; then
      echo "DataFixture"
      printf YES | java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.DataFixture $batcAction
    elif [ $batchMode = "DataFixture" ] && [ "$batchArgument" = 2 ] ; then
      # merge_additional_panels_to_trafficSigns
      # traffic_sign_extract
      echo "DataFixture"
      printf YES | java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.DataFixture $batcAction $trafficSignGroup
    elif [ $batchMode = "AssetValidatorProcess" ]; then
      echo "AssetValidatorProcess"
      printf YES | java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.AssetValidatorProcess $assetForValidation
    elif [ $batchMode = "TierekisteriDataImporter" ]; then
      echo "TierekisteriDataImporter"
      printf YES | java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.TierekisteriDataImporter $tierekisteriAction $tierekisteriAsset $trafficSignGroup
    else
      echo "Wrong mode"
  fi
fi