#!/bin/bash

[ -f data/backup.properties ] &&\
    docker-compose exec basex basexclient -Uadmin -Padmin\
               -c'SET ADDRAW true'\
               -c'SET FTINDEX true'\
               -c'SET CHOP false'\
               -c'CREATE DB db /srv/production-data/db'
