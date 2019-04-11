#!/bin/sh
if [[ -e ~/.java.opts ]]; then
  source ~/.java.opts
else
  javaopts="-Xms2048m -Xmx4096m"
fi
jarfile="target/scala-2.11/digiroad2-assembly-0.1.0-SNAPSHOT.jar"
javaopts="$javaopts -Dfile.encoding=UTF8 -Djava.security.egd=file:///dev/urandom -jar $jarfile"
logfile="ci-test.boot.log"

nohup java $javaopts > $logfile