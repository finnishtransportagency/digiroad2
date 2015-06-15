#!/bin/sh
SBT_OPTS="-Xms1512M -Xmx2536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=1024M"
java $SBT_OPTS -jar `dirname $0`/sbt-launch.jar "$@"
