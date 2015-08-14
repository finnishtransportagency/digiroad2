#!/bin/sh
if [ $# -ne 3 ]
  then
    echo "Wrong number of arguments"
    echo "usage: ./performance-tests.sh [hostname] [username] [user count]"
    exit 1
fi
./sbt -Dhost=$1 -Dusername=$2 -Dusers=$3 'project gatling' test
