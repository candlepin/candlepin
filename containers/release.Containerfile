FROM registry.access.redhat.com/ubi9-minimal:latest as builder

ARG WAR_FILE

RUN microdnf -y update && \
    microdnf install -y wget tar gzip openssl && \
    microdnf clean all

USER root

# Prepare Tomcat
ARG TOMCAT_VERSION=9.0.110
RUN wget https://archive.apache.org/dist/tomcat/tomcat-9/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz; \
    tar xzf apache-tomcat-${TOMCAT_VERSION}.tar.gz; \
    mkdir /opt/tomcat; \
    mv apache-tomcat-${TOMCAT_VERSION}/* /opt/tomcat/

# Prepare Candlepin
RUN mkdir -p /app/build/
WORKDIR /app/build
COPY ${WAR_FILE} ./candlepin.war

# Prepare development certs
RUN mkdir -p /app/certs
WORKDIR /app/certs
COPY ./bin/deployment/gen_certs.sh .
RUN ./gen_certs.sh --cert_dir /app/certs --hostname candlepin --force && \
    rm gen_certs.sh

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
    microdnf install -y java-25-openjdk-headless initscripts && \
    microdnf clean all

ENV JAVA_HOME=/usr/lib/jvm/jre-25-openjdk
ENV JRE_HOME=/usr/lib/jvm/jre-25-openjdk
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
COPY --from=builder /app/build /opt/tomcat/webapps

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
This is a development image and not intended for production use."

USER root

ENV CATALINA_OPTS="$CATALINA_OPTS -Xdebug -Xrunjdwp:transport=dt_socket,address=*:8000,server=y,suspend=n"

# Copy the generated candlepin.conf (run ./gradlew generateConfig before docker build)
COPY build/candlepin.conf /etc/candlepin/candlepin.conf
RUN test -s /etc/candlepin/candlepin.conf || \
    (echo "ERROR: build/candlepin.conf is empty. Run ./gradlew generateConfig first." >&2 && exit 1)

# Setup development certificate and key
WORKDIR /etc/candlepin/certs
COPY --from=builder /app/certs /etc/candlepin/certs

# Add the certificate to the Java trust store
RUN ln -s /etc/candlepin/certs/*.crt /etc/pki/ca-trust/source/anchors --force; \
    update-ca-trust;

COPY ./containers/server.xml /opt/tomcat/conf
COPY ./containers/logback.xml /opt/tomcat/logback-override.xml

WORKDIR /opt/tomcat/bin

USER tomcat

# Expose ports for tomcat, candlepin, postgres and mariadb
EXPOSE 8080 8443 5432 3306

ENTRYPOINT ["sh", "-c", "rm -rf /opt/tomcat/webapps/candlepin && /opt/tomcat/bin/catalina.sh run"]
