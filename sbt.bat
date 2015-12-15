set SCRIPT_DIR=%~dp0
java -Xmx512M -Dfile.encoding=utf-8 -jar "%SCRIPT_DIR%sbt-launch.jar" %*
