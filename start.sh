#!/bin/sh
if [[ -e ~/.java.opts ]]; then
  source ~/.java.opts
else
  javaopts="-Xms2048m -Xmx4096m"
fi
javaopts="-javaagent:newrelic.jar $javaopts -Dfile.encoding=UTF8 -Djava.security.egd=file:///dev/urandom -jar $jarfile"
logfile="digiroad2.boot.log"

nohup java $javaopts > $logfile &