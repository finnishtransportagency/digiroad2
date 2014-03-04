#!/bin/sh
if [ "$#" -ne 3 ]
then
    echo "Usage: import-users-from-csv.sh <digiroad2-server-address:server-port> <user-name> <csv-file>"
    echo "where <user-name> is the user name of administrator account to be used to make modifications in digiroad service users"
    echo "and <csv-file> is the csv (columns separated by semicolons) file containing user information to be added and / or created"
else
    curl -H "OAM_REMOTE_USER:${2}" -T ${3} http://${1}/api/userconfig/municipalitiesbatch
fi
