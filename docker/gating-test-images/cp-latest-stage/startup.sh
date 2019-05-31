#! /bin/bash
#
# Script that runs candlepin in a container.
# Depends on a container running postgresql locally (in the same pod).

set -e

retry() {
    local -r -i max_attempts="$1"; shift
    local -r name="$1"; shift
    local -r cmd="$@"
    local -i attempt_num=1
    echo -n "Waiting for $name to start..."
    until ${cmd}
    do
        if (( attempt_num == max_attempts ))
        then
            echo "Attempt $attempt_num failed and there are no more attempts left!"
            return 1
        else
            echo -n '.'
            sleep $(( attempt_num++ ))
        fi
    done
    echo
}

evalrc() {
    if [ "$1" -ne "0" ]; then
        echo "$2"
        exit $1
    fi
}

# Need to remove the default empty conf file so that cpsetup will create it with the appropriate properties
rm /etc/candlepin/candlepin.conf

retry 20 "postgres" pg_isready -h 127.0.0.1
evalrc $? "Postgres did not start in time. Exiting..."

retry 20 "postgres" /usr/share/candlepin/cpsetup -u postgres --dbhost 127.0.0.1 --dbport 5432 --skip-service
evalrc $? "Postgres was not available in time. Exiting..."

/usr/bin/supervisord -c /etc/supervisord.conf
