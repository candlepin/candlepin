#! /bin/bash

set -e

setup_supervisor() {
    pip install meld3==1.0.0
    pip install supervisor
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
#    cat > /etc/supervisor/conf.d/rsyslog.conf <<RSYSLOG_SUPERVISOR
#[program:rsyslog]
#command=/sbin/rsyslogd -n -c 5
#RSYSLOG_SUPERVISOR
}

setup_tomcat() {
    cat > /etc/supervisor/conf.d/tomcat.conf <<TOMCAT_SUPERVISOR
[program:tomcat]
user=tomcat
environment=CATALINA_PID="/tmp/tomcat.pid",JAVA_HOME="/usr/lib/jvm/jre"
command=java -classpath /usr/share/tomcat/bin/bootstrap.jar:/usr/share/tomcat/bin/tomcat-juli.jar:/usr/share/java/commons-daemon.jar -Dcatalina.base=/usr/share/tomcat -Dcatalina.home=/usr/shareomcat -Djava.endorsed.dirs= -Djava.io.tmpdir=/var/cache/tomcat/temp -Djava.util.logging.config.file=/usr/share/tomcat/conf/logging.properties -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager org.apache.catalina.startup.Bootstrap start
stopsignal=TERM
redirect_stderr=true
TOMCAT_SUPERVISOR
}


setup_supervisor
setup_tomcat

# Install just the deps for candlepin, not the package itself? This would allow
# us to install the rpm on startup instead of embedding in container.
#yum deplist candlepin | grep provider | awk '{print $2}' | sort | uniq | grep -v candlepin | sed ':a;N;$!ba;s/\n/ /g' | xargs yum -y install
