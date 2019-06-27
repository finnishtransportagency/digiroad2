#!/bin/sh
if [ -e ~/.sbt.opts ]; then
  source ~/.sbt.opts
else
  SBT_OPTS="-Xms1512M -Xmx2536M -Xss1M -Dfile.encoding=utf-8"
fi
SBT_OPTS="$SBT_OPTS -XX:+CMSClassUnloadingEnabled"
echo $SBT_OPTS
java $SBT_OPTS -jar `dirname $0`/sbt-launch.jar "$@"
