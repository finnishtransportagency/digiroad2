#!/bin/bash

# checkout revision if given
git checkout $1

git clean -d -f

rm -rf bower_components && bower install

rsync -av --exclude-from 'copy_exclude.txt' --progress UI/ src/main/webapp/
rsync -av --progress bower_components src/main/webapp/

grunt && rsync -av dist/ src/main/webapp/ && ./sbt assembly