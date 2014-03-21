#SBT_OPTS="-Xms1512M -Xmx2536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=1024M"
SBT_OPTS="-Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=256M"
java $SBT_OPTS -jar `dirname $0`/sbt-launch.jar "$@"
