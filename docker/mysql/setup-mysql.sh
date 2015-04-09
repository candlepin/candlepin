#! /bin/bash

setup_mysql() {
    yum install -y mysql-server mysql-connector-java

    export CP_MYSQL_CONFIG="/etc/supervisor/conf.d/mysql_install_db.conf"
    echo "[program:mysql_install_db]" > $CP_MYSQL_CONFIG
    echo "user=mysql" >> $CP_MYSQL_CONFIG
    echo "stdout_logfile=/var/log/mariadb/supervisor.log" >> $CP_MYSQL_CONFIG
    echo "stderr_logfile=/var/log/mariadb/supervisor.error_log" >> $CP_MYSQL_CONFIG
    echo "command=/usr/bin/mysql_install_db" >> $CP_MYSQL_CONFIG
    echo "autostart=false" >> $CP_MYSQL_CONFIG
    echo "autorestart=false" >> $CP_MYSQL_CONFIG

    export CP_MYSQL_CONFIG="/etc/supervisor/conf.d/mysqld.conf"
    echo "[program:mysqld]" > $CP_MYSQL_CONFIG
    echo "user=mysql" >> $CP_MYSQL_CONFIG
    echo "stdout_logfile=/var/log/mariadb/supervisor.log" >> $CP_MYSQL_CONFIG
    echo "stderr_logfile=/var/log/mariadb/supervisor.error_log" >> $CP_MYSQL_CONFIG
    echo "command=/usr/bin/pidproxy /var/run/mysqld/mysqld.pid /usr/bin/mysqld_safe" >> $CP_MYSQL_CONFIG
    echo "redirect_stderr=true" >> $CP_MYSQL_CONFIG
    echo "autostart=false" >> $CP_MYSQL_CONFIG
    echo "autorestart=false" >> $CP_MYSQL_CONFIG
    echo "" >> $CP_MYSQL_CONFIG

    /usr/bin/supervisord -c /etc/supervisord.conf
    /usr/bin/supervisorctl start mysql_install_db
    /usr/bin/supervisorctl start mysqld
    sleep 5

    mysql --user=root mysql --execute="CREATE USER 'candlepin'@'localhost'; GRANT ALL PRIVILEGES on candlepin.* TO 'candlepin'@'localhost' WITH GRANT OPTION"
    mysqladmin --user="candlepin" create candlepin

    export CP_CONFIG_FILE="/etc/candlepin/candlepin.conf"
    echo "jpa.config.hibernate.connection.driver_class=com.mysql.jdbc.Driver" >> $CP_CONFIG_FILE
    echo "jpa.config.hibernate.connection.url=jdbc:mysql:///candlepin" >> $CP_CONFIG_FILE
    echo "jpa.config.hibernate.dialect=org.hibernate.dialect.MySQL5InnoDBDialect" >> $CP_CONFIG_FILE
    echo "jpa.config.hibernate.connection.username=candlepin" >> $CP_CONFIG_FILE
    echo "jpa.config.hibernate.connection.password=" >> $CP_CONFIG_FILE
    echo "" >> $CP_CONFIG_FILE
    echo "org.quartz.jobStore.driverDelegateClass = org.quartz.impl.jdbcjobstore.StdJDBCDelegate" >> $CP_CONFIG_FILE
    echo "org.quartz.dataSource.myDS.driver = com.mysql.jdbc.Driver" >> $CP_CONFIG_FILE
    echo "org.quartz.dataSource.myDS.URL = jdbc:mysql:///candlepin" >> $CP_CONFIG_FILE
    echo "org.quartz.dataSource.myDS.user = candlepin" >> $CP_CONFIG_FILE
    echo "org.quartz.dataSource.myDS.password =" >> $CP_CONFIG_FILE
    echo "org.quartz.dataSource.myDS.maxConnections = 5" >> $CP_CONFIG_FILE

    export CP_MYSQL_CONFIG="/etc/supervisor/conf.d/mysqld.conf"
    echo "[program:mysqld]" > $CP_MYSQL_CONFIG
    echo "user=mysql" >> $CP_MYSQL_CONFIG
    echo "stdout_logfile=/var/log/mariadb/supervisor.log" >> $CP_MYSQL_CONFIG
    echo "stderr_logfile=/var/log/mariadb/supervisor.error_log" >> $CP_MYSQL_CONFIG
    echo "command=/usr/bin/pidproxy /var/run/mysqld/mysqld.pid /usr/bin/mysqld_safe" >> $CP_MYSQL_CONFIG
    echo "redirect_stderr=true" >> $CP_MYSQL_CONFIG
    echo "autostart=true" >> $CP_MYSQL_CONFIG
    echo "autorestart=false" >> $CP_MYSQL_CONFIG
    echo "" >> $CP_MYSQL_CONFIG

    echo "USE_MYSQL=\"1\"" >> /root/.candlepinrc
}

setup_mysql
