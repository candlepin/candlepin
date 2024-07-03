FROM quay.io/centos/centos:stream9 AS builder

ARG WAR_FILE

USER root

# Update and install dependencies
RUN dnf -y --setopt install_weak_deps=False update && \
    dnf -y --setopt install_weak_deps=False install java-17-openjdk-devel wget tar openssl && \
    dnf clean all

ENV JAVA_HOME=/usr/lib/jvm/jre-17-openjdk
ENV JRE_HOME=/usr/lib/jvm/jre-17-openjdk

# Prepare Tomcat
RUN wget https://archive.apache.org/dist/tomcat/tomcat-9/v9.0.76/bin/apache-tomcat-9.0.76.tar.gz; \
    tar xzf apache-tomcat-9.0.76.tar.gz; \
    mkdir /opt/tomcat; \
    mv apache-tomcat-9.0.76/* /opt/tomcat/

# Prepare Candlepin
RUN mkdir -p /app/build
WORKDIR /app/build
COPY ${WAR_FILE} ./candlepin.war
RUN jar xf $(find . -name 'candlepin.war' | head -n 1); \
    sed -i 's/jss4.jar/jss.jar/g' ./META-INF/context.xml; \
    rm candlepin.war

# Prepare development certs
RUN mkdir -p /app/certs
WORKDIR /app/certs
COPY ./bin/deployment/gen_certs.sh .
RUN ./gen_certs.sh --cert_out ./candlepin-ca.crt --key_out ./candlepin-ca.key --hostname candlepin; \
    rm gen_certs.sh;

FROM registry.access.redhat.com/ubi9-minimal:9.2-691
LABEL author="Josh Albrecht <jalbrech@redhat.com>"

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

# Setup development certificate and key
WORKDIR /etc/candlepin/certs
COPY --from=builder /app/certs /etc/candlepin/certs
# Add the certificate to the Java trust store
RUN ln -s /etc/candlepin/certs/*.crt /etc/pki/ca-trust/source/anchors --force; \
    update-ca-trust;

COPY ./.github/containers/server.xml /opt/tomcat/conf

WORKDIR /opt/tomcat/bin

USER tomcat

# Expose ports for tomcat, candlepin, postgres and mariadb
EXPOSE 8080 8443 5432 3306

ENTRYPOINT ["/opt/tomcat/bin/catalina.sh", "run"]
