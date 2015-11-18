#! /bin/bash

set -e

setup_mysql() {
    yum install -y mariadb-server mysql-connector-java

    export CP_MYSQL_CONFIG="/etc/supervisor/conf.d/mysqld.conf"
    echo "[program:mysqld]" > $CP_MYSQL_CONFIG
    echo "user=mysql" >> $CP_MYSQL_CONFIG
    echo "stdout_logfile=/var/log/mariadb/supervisor.log" >> $CP_MYSQL_CONFIG
    echo "stderr_logfile=/var/log/mariadb/supervisor.error_log" >> $CP_MYSQL_CONFIG
    echo "command=/usr/bin/pidproxy /var/run/mysqld/mysqld.pid /usr/bin/mysqld_safe" >> $CP_MYSQL_CONFIG
    echo "redirect_stderr=true" >> $CP_MYSQL_CONFIG
    echo "autostart=true" >> $CP_MYSQL_CONFIG
    echo "autorestart=false" >> $CP_MYSQL_CONFIG

    su - mysql -c "mysql_install_db" -s "/bin/bash"
    /usr/bin/supervisord -c /etc/supervisord.conf
    sleep 5

    mysql --user=root mysql --execute="CREATE USER 'candlepin'@'localhost'; GRANT ALL PRIVILEGES on candlepin.* TO 'candlepin'@'localhost' WITH GRANT OPTION"
    mysql --user=root mysql --execute="CREATE USER 'gutterball'@'localhost'; GRANT ALL PRIVILEGES on gutterball.* TO 'gutterball'@'localhost' WITH GRANT OPTION"
    mysqladmin --user="candlepin" create candlepin
    mysqladmin --user="gutterball" create gutterball

    echo "USE_MYSQL=\"1\"" >> /root/.candlepinrc
}

setup_mysql
