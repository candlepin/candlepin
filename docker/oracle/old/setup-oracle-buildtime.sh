#! /bin/bash

setup_oracle() {
    set -e

    mkdir -p /run/lock/subsys
    /usr/sbin/groupadd -r dba
    /usr/sbin/useradd -r -M -g dba -d /u01/app/oracle -s /bin/bash -u 499 oracle

    # wget http://public-yum.oracle.com/public-yum-ol7.repo -O /etc/yum.repos.d/public-yum-ol7.repo
    # wget http://public-yum.oracle.com/RPM-GPG-KEY-oracle-ol7 -O /etc/pki/rpm-gpg/RPM-GPG-KEY-oracle
    # yum install -y oracle-rdbms-server-11gR2-preinstall

    yum install -y bc net-tools
    yum install -y --nogpgcheck /root/oracle/*.rpm

    cat > /etc/supervisor/conf.d/oracle.conf <<ORACLE_SUPERVISOR
[program:oracle]
environment=ORACLE_HOME=/u01/app/oracle/product/11.2.0/xe,ORACLE_SID=XE
command=/usr/bin/launch-oracle.sh
ORACLE_SUPERVISOR

    mv /root/oracle/init.ora /u01/app/oracle/product/11.2.0/xe/config/scripts
    mv /root/oracle/initXETemp.ora /u01/app/oracle/product/11.2.0/xe/config/scripts

    mkdir -p /root/.m2/repository/com/oracle/ojdbc6/11.2.0
    mv /root/oracle/ojdbc6.jar /root/.m2/repository/com/oracle/ojdbc6/11.2.0/ojdbc6-11.2.0.jar

    echo 'export ORACLE_HOME=/u01/app/oracle/product/11.2.0/xe' >> /etc/profile.d/oracle_profile.sh
    echo 'export PATH=$ORACLE_HOME/bin:$PATH' >> /etc/profile.d/oracle_profile.sh
    echo 'export ORACLE_SID=XE' >> /etc/profile.d/oracle_profile.sh
    echo 'export LD_LIBRARY_PATH=/usr/lib/oracle/11.2/client64/lib:$LD_LIBRARY_PATH' >> /etc/profile.d/oracle_profile.sh

    touch /etc/init.d/functions
}


setup_oracle
