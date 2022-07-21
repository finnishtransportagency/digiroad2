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
      if [ "$batchRunType" = "DataFixture" ] &&  [[ ! -z "$trafficSignGroup" ]]; then
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

      elif [ "$batchRunType" = "MainLaneStartDateImporter" ] &&  [[ ! -z "$bucketName" ]] && [[ ! -z "$objectKey" ]]; then
        echo "MainLaneStartDateImporter"
        java $javaParameter -cp /digiroad2.jar fi.liikennevirasto.digiroad2.util.MainLaneStartDateImporter "$bucketName" "$objectKey"
      else
        echo "Wrong mode"
        exit 1
      fi
    fi
fi
