FROM registry.access.redhat.com/ubi9-minimal:9.2-691 as builder

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

################################# Production Image #################################

FROM registry.access.redhat.com/ubi9-minimal:9.2-691 as production

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
