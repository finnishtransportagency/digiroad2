set SCRIPT_DIR=%~dp0
java -Xmx512M -Dfile.encoding=UTF8 -jar "%SCRIPT_DIR%sbt-launch.jar" %*
