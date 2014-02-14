#!/bin/sh
jarfile="target/scala-2.10/digiroad2-assembly-0.1.0-SNAPSHOT.jar"
javaopts="-javaagent:newrelic.jar -jar $jarfile"
logfile="digiroad2.boot.log"

nohup java $javaopts > $logfile &