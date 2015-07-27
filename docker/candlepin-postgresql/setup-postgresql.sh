#! /bin/bash

set -e

setup_postgresql() {
    dnf install -y postgresql postgresql-server postgresql-jdbc
    echo 'NETWORKING=yes' > /etc/sysconfig/network
    cat > /etc/supervisor/conf.d/postgres.conf <<POSTGRES_SUPERVISOR
[program:postgres]
user=postgres
environment=PGDATA="/var/lib/pgsql/data"
command=/usr/bin/postgres
stopsignal=INT
redirect_stderr=true
POSTGRES_SUPERVISOR

    export PGSETUP_INITDB_OPTIONS="--auth=trust"

    /root/postgresql-setup initdb

    /usr/bin/supervisord -c /etc/supervisord.conf &
    sleep 5
    su - postgres -c 'createuser -dls candlepin'
    supervisorctl stop postgres
}

setup_postgresql
