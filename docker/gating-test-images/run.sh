#!/bin/bash
#
# Script that downloads candlepin & postgres containers from the internal registry, and runs them using
# podman, by creating a pod called 'cp_pod' which has candlepin's 8443 port exposed, and running the two
# containers in that pod.
# The postgresql container has a sql dump with the required test data and imports them
# on startup.

REGISTRY=quay.io/candlepin

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

echo "============ Pulling latest candlepin image from registry... ============ "
podman pull $REGISTRY/cp_latest_stage:latest
evalrc $? "cp_latest_stage:latest image pull was not successful."

echo "============ Pulling latest postgres image from registry... ============ "
podman pull $REGISTRY/cp_postgres:latest
evalrc $? "cp_postgres:latest image pull was not successful."

echo "============ Create SELinux policy for postgres to avoid denial when trying to read the /etc/hosts file... ============ "
ausearch -c 'postgres' --raw | audit2allow -M my-postgres
semodule -X 300 -i my-postgres.pp

echo "============ Deploying candlepin & postgresql containers... ============ "
podman pod create --name cp_pod -p 8443:8443
podman run -d --pod cp_pod --name=db -e POSTGRES_USER="candlepin" -e POSTGRES_INITDB_ARGS="--auth='ident' --auth='trust'" $REGISTRY/cp_postgres:latest
podman run -d --pod cp_pod --name=candlepin $REGISTRY/cp_latest_stage:latest

retry 20 "candlepin" curl -k https://127.0.0.1:8443/candlepin/status
evalrc $? "Candlepin server did not start in time. Exiting..."

echo "Candlepin server is ready at 'https://127.0.0.1:8443/candlepin'..."
