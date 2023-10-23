FROM registry.access.redhat.com/ubi9-minimal:latest as builder

ARG WAR_FILE

RUN microdnf -y update && \
    microdnf install -y java-17-openjdk-devel wget tar && \
    microdnf clean all

USER root

# Prepare Tomcat
RUN wget https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.76/bin/apache-tomcat-9.0.76.tar.gz; \
    tar xzf apache-tomcat-9.0.76.tar.gz; \
    mkdir /opt/tomcat; \
    mv apache-tomcat-9.0.76/* /opt/tomcat/

# Prepare Candlepin
RUN mkdir -p /app/build
WORKDIR /app/build
COPY ${WAR_FILE} ./candlepin.war
RUN jar xf $(find . -name 'candlepin*.war' | head -n 1); \
    sed -i 's/jss4.jar/jss.jar/g' ./META-INF/context.xml

# Prepare development certs
RUN mkdir -p /app/certs
WORKDIR /app/certs
COPY ./bin/deployment/gen_certs.sh .
RUN ./gen_certs.sh --cert_out ./candlepin-ca.crt --key_out ./candlepin-ca.key --hostname candlepin; \
    rm gen_certs.sh;

################################# Production Image #################################

FROM registry.access.redhat.com/ubi9-minimal:latest as production

ARG BUILD_DATE
ARG VERSION

LABEL org.opencontainers.image.title="Candlepin" \
    org.opencontainers.image.description="Candlepin is an open source subscription & entitlement engine \
which is designed to manage software subscriptions from both vendor's & customer's perspectives" \
    org.opencontainers.image.documentation="https://www.candlepinproject.org/docs/candlepin/overview.html" \
    org.opencontainers.image.licenses="GNU GPL v2.0" \
    org.opencontainers.image.version=${VERSION} \
    org.opencontainers.image.source="https://github.com/candlepin/candlepin" \
    org.opencontainers.image.created=${BUILD_DATE}

USER root

# Update and install dependencies
RUN microdnf -y update && \
    microdnf -y update ca-certificates && \
    microdnf install -y java-17-openjdk-headless jss initscripts && \
    microdnf clean all

ENV JAVA_HOME=/usr/lib/jvm/jre-17-openjdk
ENV JRE_HOME=/usr/lib/jvm/jre-17-openjdk
ENV CATALINA_OPTS=-Djavax.net.ssl.trustStore=$JAVA_HOME/lib/security/cacerts

# Tomcat Setup
COPY --from=builder /opt/tomcat/ /opt/tomcat/
RUN mkdir -p /etc/candlepin/certs; \
    ln -s /etc/candlepin/certs/* /etc/pki/ca-trust/source/anchors; \
    mkdir -p /var/cache/candlepin/sync; \
    groupadd -g 10000 tomcat; \
    useradd -g tomcat -u 10001 tomcat; \
    chown -R tomcat.tomcat /opt/tomcat; \
    chown -R tomcat.tomcat /var/log/; \
    chown -R tomcat.tomcat /var/lib/; \
    chown -R tomcat.tomcat /etc/candlepin/; \
    chown -R tomcat:tomcat /var/cache/; \
    chown -R tomcat:tomcat  /etc/pki/; \
    chmod -R 775 /opt/tomcat/webapps; \
    chmod -R 775 /var/log/;

# Candlepin install
COPY --from=builder /app/build /opt/tomcat/webapps/candlepin

WORKDIR /opt/tomcat/bin

USER tomcat

# Expose ports for tomcat, candlepin, postgres and mariadb
EXPOSE 8080 8443 5432 3306

ENTRYPOINT ["/opt/tomcat/bin/catalina.sh", "run"]

################################# Development Image ################################

FROM production as development

LABEL org.opencontainers.image.title="Candlepin Development Image" \
    org.opencontainers.image.description="Candlepin is an open source subscription & entitlement engine \
which is designed to manage software subscriptions from both vendor's & customer's perspectives. \
    This is a development image not intended for production use."

USER root

# Add default Candlepin configurations
RUN echo "jpa.config.hibernate.dialect=org.hibernate.dialect.PostgreSQL92Dialect" > /etc/candlepin/candlepin.conf; \
    echo "jpa.config.hibernate.connection.driver_class=org.postgresql.Driver" >> /etc/candlepin/candlepin.conf; \
    echo "jpa.config.hibernate.connection.url=jdbc:postgresql://localhost/candlepin" >> /etc/candlepin/candlepin.conf; \
    echo "jpa.config.hibernate.connection.username=candlepin" >> /etc/candlepin/candlepin.conf; \
    echo "jpa.config.hibernate.connection.password=candlepin" >> /etc/candlepin/candlepin.conf; \
    echo "candlepin.auth.trusted.enable=true" >> /etc/candlepin/candlepin.conf; \
    echo "candlepin.auth.oauth.enable=true" >> /etc/candlepin/candlepin.conf; \
    echo "candlepin.auth.oauth.consumer.rspec.secret=rspec-oauth-secret" >> /etc/candlepin/candlepin.conf; \
    echo "candlepin.db.database_manage_on_startup=Manage" >> /etc/candlepin/candlepin.conf; \
    echo "candlepin.refresh.orphan_entity_grace_period=0" >> /etc/candlepin/candlepin.conf; \
    echo "candlepin.standalone=true" >> /etc/candlepin/candlepin.conf;

# Setup development certificate and key
WORKDIR /etc/candlepin/certs
COPY --from=builder /app/certs /etc/candlepin/certs
# Add the certificate to the Java trust store
RUN ln -s /etc/candlepin/certs/*.crt /etc/pki/ca-trust/source/anchors --force; \
    update-ca-trust;

COPY ./containers/server.xml /opt/tomcat/conf

WORKDIR /opt/tomcat/bin

USER tomcat

# Expose ports for tomcat, candlepin, postgres and mariadb
EXPOSE 8080 8443 5432 3306

ENTRYPOINT ["/opt/tomcat/bin/catalina.sh", "run"]
