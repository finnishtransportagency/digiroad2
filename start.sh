#!/bin/sh
jarfile="target/scala-2.11/digiroad2-assembly-0.1.0-SNAPSHOT.jar"
javaopts="-javaagent:newrelic.jar -Xms2048m -Xmx4096m -Dfile.encoding=UTF8 -Djava.security.egd=file:///dev/urandom -jar $jarfile"
logfile="digiroad2.boot.log"

nohup java $javaopts > $logfile &