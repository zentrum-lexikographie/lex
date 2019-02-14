#!/bin/bash

if [ ! -d data ]; then
    db_host=spock.dwds.de

    backup_file=$(ssh $db_host\
                      ls /home/eXist-db-2.2/webapp/WEB-INF/data/export/full*.zip |\
                      sort -r | head -1)

    temp_backup_file=$(mktemp --suffix=.zip)

    scp ${db_host}:${backup_file} $temp_backup_file

    mkdir data
    unzip -W $temp_backup_file\
          -x\
          db/apps/**\
          db/dwdswb/xquery/collection.xconf\
          db/system/config/db/apps/**\
          db/system/config/db/dwdswb/**\
          db/system/security/**\
          db/system/versions/**\
          db/__lost_and_found__/**\
          -d data

    rm $temp_backup_file
fi

docker exec exist\
       java -jar start.jar client --no-gui\
       --xpath "repo:install-and-deploy('http://exist-db.org/xquery/versioning', 'http://demo.exist-db.org/exist/apps/public-repo/modules/find.xql')"

docker cp data exist:/exist/webapp/WEB-INF/data/prod
docker exec exist\
       java -jar start.jar client --no-gui\
       --xpath "system:restore('/exist/webapp/WEB-INF/data/prod', '', '')"
