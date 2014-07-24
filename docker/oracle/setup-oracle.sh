#! /bin/bash

setup_oracle() {

    mkdir -p /var/lock/subsys
    yum install -y bc net-tools
    /usr/sbin/groupadd -r dba
    /usr/sbin/useradd -r -M -g dba -d /u01/app/oracle -s /bin/bash -u 499 oracle

    yum localinstall -y --nogpgcheck /root/oracle/*.rpm
    #yum install -y http://yum.spacewalkproject.org/1.9/RHEL/6/x86_64/spacewalk-repo-1.9-1.el6.noarch.rpm
    #yum install -y oracle-xe-selinux oracle-instantclient-selinux oracle-instantclient-sqlplus-selinux

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

    cd /
    /etc/init.d/oracle-xe configure responseFile=/root/xe.rsp
}

setup_oracle
