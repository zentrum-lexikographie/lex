#!/bin/bash

host='spock.dwds.de'
dumps='/home/eXist-db-2.2/webapp/WEB-INF/data/export'
dest=${1:-exist-db.zip}

last_dump=$(ssh $host find "$dumps" -name 'full*zip' | sort -r | head -1)
scp $host:$last_dump $dest
