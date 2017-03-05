#!/bin/bash
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
exec "$DIR/pgsql/bin/pg_ctl" "-D$DIR/data" $1
