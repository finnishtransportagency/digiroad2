#!/bin/sh

if ! [ "$batchMode" = "true" ]; then
  echo "Server mode"
  java $javaParameter -jar /digiroad2.jar
else
  echo "Batch mode"
    if  [[ -z "$batchRunType" ]]; then
      echo "batchRunType env is not defined"
      exit 1
    else
      if [ "$batchRunType" = "UpdateIncompleteLinkList" ]; then

        echo "UpdateIncompleteLinkList"
        java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.UpdateIncompleteLinkList
      elif [ "$batchRunType" = "DataFixture" ] &&  [[ ! -z "$trafficSignGroup" ]]; then

        echo "DataFixture with trafficSignGroup"
         if  [[ ! -z "$batchAction" ]]; then
            java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.DataFixture "$batchAction" "$trafficSignGroup"
         else
            echo "batchAction env is not defined"
            exit 1
         fi
      elif [ "$batchRunType" = "DataFixture" ]; then

        echo "DataFixture"
        if [[ ! -z "$batchAction" ]]; then
           java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.DataFixture "$batchAction"
        else
         echo "batchAction env is not defined"
         exit 1
        fi
      elif [ "$batchRunType" = "AssetValidatorProcess" ]; then

        echo "AssetValidatorProcess"
        if [[ ! -z "$assetForValidation" ]]; then
          java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.AssetValidatorProcess "$assetForValidation"
        else
         echo "assetForValidation env is not defined"
         exit 1
        fi
      elif [ "$batchRunType" = "TierekisteriDataImporter" ]; then

        echo "TierekisteriDataImporter"
        if [[ ! -z "$tierekisteriAction" ]] && [[ ! -z "$tierekisteriAsset" ]] && [[ ! -z "$trafficSignGroup" ]]; then
          java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.TierekisteriDataImporter "$tierekisteriAction" "$tierekisteriAsset" "$trafficSignGroup"
        else
          echo "tierekisteriAction or tierekisteriAsset or trafficSignGroup env is not defined"
          exit 1
        fi
      else
        echo "Wrong mode"
        exit 1
      fi
    fi
fi
