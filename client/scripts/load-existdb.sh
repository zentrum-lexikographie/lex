#!/bin/bash

[ -f data/backup.properties ] || exit 1

#docker exec exist\
#       java -jar start.jar client --no-gui\
#       --xpath "system:restore('/exist/production-data', '', '')"

curl -u admin: -T scripts/dwdswb-collection.xconf\
     http://localhost:8080/exist/webdav/db/system/config/db/dwdswb/collection.xconf

docker exec exist\
       java -jar start.jar client --no-gui\
       --xpath "xmldb:reindex('/db/dwdswb')"
