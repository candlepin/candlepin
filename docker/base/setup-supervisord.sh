#! /bin/bash

setup_supervisor() {
    yum install -y supervisor
    mkdir -p /var/log/supervisor
    mkdir -p /etc/supervisor/conf.d
    cat > /etc/supervisord.conf <<SUPERVISOR
[supervisord]
nodaemon=true
logfile=/var/log/supervisor/supervisord.log
pidfile=/var/run/supervisord.pid

[include]
files=/etc/supervisor/conf.d/*.conf

[supervisorctl]
serverurl=unix:///var/run/supervisor.sock

[unix_http_server]
file=/var/run/supervisor.sock

[rpcinterface:supervisor]
supervisor.rpcinterface_factory = supervisor.rpcinterface:make_main_rpcinterface
SUPERVISOR
    cat > /etc/supervisor/conf.d/rsyslog.conf <<RSYSLOG_SUPERVISOR
[program:rsyslog]
command=/sbin/rsyslogd -n -c 5
RSYSLOG_SUPERVISOR
}

setup_tomcat() {
    cat > /etc/supervisor/conf.d/tomcat.conf <<TOMCAT_SUPERVISOR
[program:tomcat]
user=tomcat
#environment=PGDATA="/var/lib/pgsql/data"
command=/usr/bin/tomcat-wrapper
redirect_stderr=true
TOMCAT_SUPERVISOR
}


setup_supervisor
setup_tomcat
