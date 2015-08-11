#/bin/sh -e

source /etc/profile.d/oracle_profile.sh
/etc/init.d/oracle-xe configure responseFile=/root/xe.rsp

rm -rf /root/oracle

