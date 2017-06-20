#! /bin/bash

set -e

source /root/dockerlib.sh

setup_supervisor() {
    yum install -y supervisor
    mkdir -p /var/log/supervisor
    mkdir -p /etc/supervisor/conf.d
    cat > /etc/supervisord.conf <<SUPERVISOR
[supervisord]
nodaemon=false
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
environment=CATALINA_PID="/tmp/tomcat.pid",JAVA_HOME="/usr/lib/jvm/jre"
command=/usr/libexec/tomcat/server start
stopsignal=TERM
redirect_stderr=true
TOMCAT_SUPERVISOR
}

setup_qpidd() {
# qpid-proton-c-devel is dependency of qpid_proton ruby binding (needed for spec tests)
yum install -y qpid-cpp-server qpid-cpp-client qpid-cpp-server-linearstore qpid-tools sudo qpid-proton-c-devel

cat > /etc/supervisor/conf.d/qpid.conf <<  QPID_SUPERVISOR
[program:qpidd]
user=qpidd
command=/usr/sbin/qpidd --config /etc/qpid/qpidd.conf --data-dir=/var/lib/qpidd
stopsignal=INT
redirect_stderr=true
QPID_SUPERVISOR
}


setup_ssh() {
    yum install -y openssh-server
    echo 'root:redhat' |chpasswd
    RSA_KEY=/etc/ssh/ssh_host_rsa_key
    DSA_KEY=/etc/ssh/ssh_host_dsa_key

    ssh-keygen -q -t dsa -f $DSA_KEY -C '' -N '' >&/dev/null
    chmod 600 $DSA_KEY
    chmod 644 $DSA_KEY.pub

    ssh-keygen -q -t rsa -f $RSA_KEY -C '' -N '' >&/dev/null
    chmod 600 $RSA_KEY
    chmod 644 $RSA_KEY.pub
    cat > /etc/supervisor/conf.d/sshd.conf <<SSH_SUPERVISOR
[program:sshd]
command=/usr/sbin/sshd -D
SSH_SUPERVISOR
}


setup_candlepinrc() {
    cat > /root/.candlepinrc <<CANDLEPINRC
SUPERVISOR=1
CANDLEPINRC
}

set -v
setup_supervisor
setup_ssh
setup_tomcat
setup_qpidd
setup_candlepinrc
cleanup_env
