#! /bin/sh

# Need to adjust hostname for the running container:
mkdir -p /run/lock/subsys
source /etc/profile
sed -i -E "s/HOST = [^)]+/HOST = $HOSTNAME/g" /u01/app/oracle/product/11.2.0/xe/network/admin/listener.ora
sed -i -E "s/HOST = [^)]+/HOST = $HOSTNAME/g" /u01/app/oracle/product/11.2.0/xe/network/admin/tnsnames.ora

/etc/init.d/oracle-xe start
