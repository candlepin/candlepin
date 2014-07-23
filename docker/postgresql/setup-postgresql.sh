#! /bin/bash

setup_postgresql() {
    yum install -y postgresql postgresql-server postgresql-jdbc
    echo 'NETWORKING=yes' > /etc/sysconfig/network
    cat > /etc/supervisor/conf.d/postgres.conf <<POSTGRES_SUPERVISOR
[program:postgres]
user=postgres
environment=PGDATA="/var/lib/pgsql/data"
command=/usr/bin/postgres
stopsignal=INT
redirect_stderr=true
POSTGRES_SUPERVISOR

    /root/postgresql-setup initdb --auth=trust

    /usr/bin/supervisord -c /etc/supervisord.conf &
    sleep 5
    su - postgres -c 'createuser -dls candlepin'
    supervisorctl stop postgres
}

setup_postgresql
