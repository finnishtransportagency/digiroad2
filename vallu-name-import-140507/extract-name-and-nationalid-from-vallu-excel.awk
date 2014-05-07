#!/usr/bin/awk -f
BEGIN { FS=";"; }
(NR != 1) && ($2 ~ /\w+/) && ($3 ~ /\w+/) { print $2";"$3 }