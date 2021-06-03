#!/bin/sh

if ! [ $batchEnv = "true" ]
  then
    echo "Server mode"
    java $javaParameter -jar /digiroad2.jar
  else
    echo "Batch mode"
    if [ $batchMode = "UpdateIncompleteLinkList" ]; then
      echo "UpdateIncompleteLinkList"
      printf YES | java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.UpdateIncompleteLinkList
    elif [ $batchMode = "DataFixture" ] &&  [[ ! -z $trafficSignGroup ]]; then
      echo "DataFixture with trafficSignGroup"
      printf YES | java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.DataFixture $batcAction $trafficSignGroup
    elif [ $batchMode = "DataFixture" ]; then
      echo "DataFixture"
      printf YES | java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.DataFixture $batcAction
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