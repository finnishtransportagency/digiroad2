#!/bin/bash

# checkout revision if given
git checkout $1

git clean -d -f

rm -rf bower_components && bower install

rsync -av --exclude-from 'copy_exclude.txt' --progress UI/ src/main/webapp/
rsync -av --progress bower_components src/main/webapp/

grunt && rsync -av dist/ src/main/webapp/ && ./sbt assembly

tar -cvzf file.tar.gz target/scala-2.10/digiroad2-assembly-0.1.0-SNAPSHOT.jar src/main/webapp/
scp file.tar.gz karjalainenr@10.129.47.148:.

ssh karjalainenr@10.129.47.148 'killall java'
ssh karjalainenr@10.129.47.148 'tar -C release/ -xvzf file.tar.gz'
ssh karjalainenr@10.129.47.148 'cp -r release/src .'
ssh karjalainenr@10.129.47.148 'java -jar release/target/scala-2.10/digiroad2-assembly-0.1.0-SNAPSHOT.jar &'
