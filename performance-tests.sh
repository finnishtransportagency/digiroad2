#!/bin/sh
if [ -z "$1" ]
  then
    echo "No target host specified"
    exit 1
fi
./sbt -Dhost=$1 'project gatling' test
