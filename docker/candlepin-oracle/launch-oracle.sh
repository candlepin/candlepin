#! /bin/sh

# Oracle is annoying.  I could not find a way to run it in the foreground
# so normal supervisor options are not appropriate.  Nor could I find a way
# to get it to write a PID file so using supervisor with pidproxy won't
# work either.

# This script will start Oracle and then remain in the foreground as long
# as Oracle is running.  When it receives SIGINT or SIGTERM it will stop
# Oracle.

stop_oracle() {
    /etc/init.d/oracle-xe stop
    exit $?
}

trap stop_oracle SIGINT SIGTERM

# Need to adjust hostname for the running container:
mkdir -p /run/lock/subsys
source /etc/profile
sed -i -E "s/HOST = [^)]+/HOST = $HOSTNAME/g" /u01/app/oracle/product/11.2.0/xe/network/admin/listener.ora
sed -i -E "s/HOST = [^)]+/HOST = $HOSTNAME/g" /u01/app/oracle/product/11.2.0/xe/network/admin/tnsnames.ora

/etc/init.d/oracle-xe start

SID="XE"
# Taken from the Oracle init script.
while $(ps -ef | grep "pmon_$SID" | grep -v grep); do
    sleep 5
done
