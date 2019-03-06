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
          db/system/**\
          db/__lost_and_found__/**\
          **/collection.xconf\
          -d data

    rm $temp_backup_file
fi
