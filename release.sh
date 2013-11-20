#!/bin/bash

# checkout revision if given
git checkout $1

git clean -d -f

rm -rf bower_components && bower install

rsync -av --exclude-from 'copy_exclude.txt' --progress UI/ src/main/webapp/
rsync -av --progress bower_components src/main/webapp/

grunt && rsync -av dist/ src/main/webapp/ && ./sbt assembly

cp digiroad2-oracle/newrelic* .
tar -cvzf file.tar.gz target/scala-2.10/digiroad2-assembly-0.1.0-SNAPSHOT.jar src/main/webapp/ newrelic*

scp file.tar.gz gateway:.

git clean -d -f

ssh gateway 'killall java'
ssh gateway 'tar -C release/ -xvzf file.tar.gz'
ssh gateway 'cp -r release/src .'
ssh gateway 'java -javaagent:release/newrelic.jar -jar release/target/scala-2.10/digiroad2-assembly-0.1.0-SNAPSHOT.jar &'