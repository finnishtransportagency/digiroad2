#!/bin/sh

if ! [ $batchMode = "true" ]; then
  echo "Server mode"
  java $javaParameter -jar /digiroad2.jar
  else
    echo "Batch mode"
    if [ $batchRunType = "UpdateIncompleteLinkList" ]; then
      echo "UpdateIncompleteLinkList"
      java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.UpdateIncompleteLinkList
    elif [ $batchRunType = "DataFixture" ] &&  [[ ! -z $trafficSignGroup ]]; then
      echo "DataFixture with trafficSignGroup"
      java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.DataFixture $batcAction $trafficSignGroup
    elif [ $batchRunType = "DataFixture" ]; then
      echo "DataFixture"
      java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.DataFixture $batcAction
    elif [ $batchRunType = "AssetValidatorProcess" ]; then
      echo "AssetValidatorProcess"
      java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.AssetValidatorProcess $assetForValidation
    elif [ $batchRunType = "TierekisteriDataImporter" ]; then
      echo "TierekisteriDataImporter"
      java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.TierekisteriDataImporter $tierekisteriAction $tierekisteriAsset $trafficSignGroup
    else
      echo "Wrong mode"
  fi
fi
