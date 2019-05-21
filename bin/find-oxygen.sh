#!/bin/bash

function oxygen_dir () {
    find $1 -maxdepth 1 -name "$2" -type d | sort -r | head -1
}

OXYGEN_LOCAL=$(oxygen_dir /usr/local 'Oxygen\ XML\ Editor*')
OXYGEN_HOME=$(oxygen_dir $HOME 'oxygen')

for dir in "$OXYGEN_HOME" "$OXYGEN_LOCAL"; do
    [ "$dir" ] && echo -n $dir && exit 0
done

exit 1
