#!/bin/bash
./flyway migrate -configFile=conf/dev.flyway.properties -user=$1 -password=$2
