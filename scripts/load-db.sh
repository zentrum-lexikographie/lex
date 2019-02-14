#!/bin/bash

if [ ! -f data/backup.properties ]; then
    db_host=spock.dwds.de

    backup_file=$(ssh $db_host\
                      ls /home/eXist-db-2.2/webapp/WEB-INF/data/export/full*.zip |\
                      sort -r | head -1)

    temp_backup_file=$(mktemp --suffix=.zip)

    scp ${db_host}:${backup_file} $temp_backup_file

    unzip -W $temp_backup_file\
          -x\
          db/apps/**\
          db/system/config/db/apps/**\
          db/system/security/exist/accounts/admin.xml\
          db/system/security/exist/accounts/eXide.xml\
          db/system/security/exist/accounts/guest.xml\
          db/system/security/exist/accounts/monex.xml\
          db/system/security/exist/groups/eXide.xml\
          db/system/security/exist/groups/monex.xml\
          db/system/versions/**\
          db/__lost_and_found__/**\
          -d data

    mv data/db/dwdswb/xquery/collection.xconf\
       data/db/dwdswb/xquery/collection.xconf.bak
    mv data/db/system/config/db/dwdswb/collection.xconf\
       data/db/system/config/db/dwdswb/collection.xconf.bak
    rm $temp_backup_file
fi

docker exec exist\
       java -jar start.jar client --no-gui\
       --xpath "repo:install-and-deploy('http://exist-db.org/xquery/versioning', 'http://demo.exist-db.org/exist/apps/public-repo/modules/find.xql')"

docker exec exist\
       java -jar start.jar client --no-gui\
       --xpath "system:restore('/exist/webapp/WEB-INF/data/production', '', '')"

# TODO: upload corrected data/db/system/config/db/dwdswb/collection.xconf.bak

docker exec exist\
       java -jar start.jar client --no-gui\
       --xpath "xmldb:reindex('/db/dwdsdb')"

