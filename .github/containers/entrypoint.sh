#!/bin/bash

#
#  Copyright (c) 2009 - ${YEAR} Red Hat, Inc.
#
#  This software is licensed to you under the GNU General Public License,
#  version 2 (GPLv2). There is NO WARRANTY for this software, express or
#  implied, including the implied warranties of MERCHANTABILITY or FITNESS
#  FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
#  along with this software; if not, see
#  http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
#
#  Red Hat trademarks are not licensed under GPLv2. No permission is
#  granted to use or replicate Red Hat trademarks that are incorporated
#  in this software or its documentation.
#

# Define the path to the configuration file
CONF_FILE=/etc/candlepin/candlepin.conf

# Declare an associative array of Candlepin configuration
declare -A conf_candlepin

# Number of attempts to call endpoint status
MAX_TRIES=10

# Add key-value pairs(ENV_VARIABLE-ACTUAL_CONFIG_NAME)
conf_candlepin["JPA_CONFIG_HIBERNATE_CONNECTION_DRIVER_CLASS"]="jpa.config.hibernate.connection.driver_class"
conf_candlepin["JPA_CONFIG_HIBERNATE_CONNECTION_URL"]="jpa.config.hibernate.connection.url"
conf_candlepin["JPA_CONFIG_HIBERNATE_CONNECTION_USERNAME"]="jpa.config.hibernate.connection.username"
conf_candlepin["JPA_CONFIG_HIBERNATE_CONNECTION_PASSWORD"]="jpa.config.hibernate.connection.password"
conf_candlepin["JPA_CONFIG_HIBERNATE_DIALECT"]="jpa.config.hibernate.dialect"
conf_candlepin["CANDLEPIN_AUTH_TRUSTED_ENABLE"]="candlepin.auth.trusted.enable"
conf_candlepin["CANDLEPIN_AUTH_OAUTH_ENABLE"]="candlepin.auth.oauth.enable"
conf_candlepin["CANDLEPIN_AUTH_OAUTH_CONSUMER_RSPEC_SECRET"]="candlepin.auth.oauth.consumer.rspec.secret"
conf_candlepin["CANDLEPIN_AUTH_CLOUD_ENABLE"]="candlepin.auth.cloud.enable"
conf_candlepin["CANDLEPIN_STANDALONE"]="candlepin.standalone"
conf_candlepin["MODULE_CONFIG_HOSTED_CONFIGURATION_MODULE"]="module.config.hosted.configuration.module"

# Function to write variables to the configuration file
write_config() {
    mkdir -p /etc/candlepin
    touch "$CONF_FILE"
    chmod 600 "$CONF_FILE"
    echo "# Configuration generated from env variables" > "$CONF_FILE"
    for key in "${!conf_candlepin[@]}"; do
        value="${conf_candlepin[$key]}"
        key_value=$(env | awk -F '=' -v k="$key" '{ if ($1 == k) print $2 }')
        if [ "$key_value" ]; then
          echo "$value=$key_value" >> "$CONF_FILE"
        fi
    done
}

call_init_endpoint() {
    while true; do
        wget -qO- http://localhost:8080/candlepin/admin/init
        if [ $? -eq 0 ]; then
            echo "Calling init endpoint succeed."
            break
        fi
        sleep 1
    done
}

# Write the configuration to the file
write_config

#Run liquibase
export LIQUIBASE_HOME="/opt/tomcat/webapps/candlepin/WEB-INF"
liquibase=$(find /opt/tomcat/webapps/candlepin/WEB-INF/lib/ -maxdepth 1 -type f -name "liquibase-core*" -printf "%f\n")

java -jar "$LIQUIBASE_HOME/lib/$liquibase" \
  --classpath=$LIQUIBASE_HOME/classes \
  --changeLogFile=db/changelog/changelog-create.xml \
  --url="$JPA_CONFIG_HIBERNATE_CONNECTION_URL" \
  --username="$JPA_CONFIG_HIBERNATE_CONNECTION_USERNAME" \
  --password="$JPA_CONFIG_HIBERNATE_CONNECTION_PASSWORD" \
  update

call_init_endpoint &

# Start tomcat
/opt/tomcat/bin/catalina.sh run

