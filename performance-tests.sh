#!/bin/sh
if [ $# -ne 4 ]
  then
    echo "Wrong number of arguments"
    echo "usage: ./performance-tests.sh [hostname] [username] [user count] [http proxy hostname]"
    exit 1
fi
./sbt -Dhost=$1 -Dusername=$2 -Dusers=$3 -DproxyHost=$4 'project gatling' test
