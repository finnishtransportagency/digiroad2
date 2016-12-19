#!/bin/sh
if [ -e ~/.sbt.opts ]; then
  source ~/.sbt.opts
else
  SBT_OPTS="-Xms1512M -Xmx2536M -Xss1M -XX:MaxPermSize=1024M -Dfile.encoding=utf-8"
fi
SBT_OPTS="$SBT_OPTS -XX:+CMSClassUnloadingEnabled"
java $SBT_OPTS -jar `dirname $0`/sbt-launch.jar "$@"
