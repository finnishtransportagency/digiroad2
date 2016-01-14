#!/bin/sh
if [ $# -ne 5 ]
  then
    echo "Wrong number of arguments"
    echo "usage: ./performance-tests.sh [hostname] [username] [user count] [http proxy hostname] [noproxyhost1,noproxyhost2,...]"
    exit 1
fi
./sbt -Dhost=$1 -Dusername=$2 -Dusers=$3 -DproxyHost=$4 -DnoProxyFor=$5 'project gatling' test
