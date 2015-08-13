#!/bin/sh
if [ $# -ne 2 ]
  then
    echo "Wrong number of arguments"
    echo "usage: ./performance-tests.sh [hostname] [user count]"
    exit 1
fi
./sbt -Dhost=$1 -Dusers=$2 'project gatling' test
