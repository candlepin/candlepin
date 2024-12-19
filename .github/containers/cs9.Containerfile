FROM quay.io/centos/centos:stream9 as builder

ARG WAR_FILE

USER root

# Update and install dependencies
RUN dnf -y --setopt install_weak_deps=False update && \
    dnf -y --setopt install_weak_deps=False install java-17-openjdk-devel openssl wget unzip && \
    dnf clean all

# Prepare Tomcat
COPY apache-tomcat-9.0.76.tar.gz /tmp/
RUN tar xzf /tmp/apache-tomcat-9.0.76.tar.gz -C /tmp && \
    mkdir /opt/tomcat && \
    mv /tmp/apache-tomcat-9.0.76/* /opt/tomcat/

# Prepare Candlepin
RUN mkdir -p /app/build
WORKDIR /app/build
COPY ${WAR_FILE} ./candlepin.war

# Prepare development certs
RUN mkdir -p /app/certs
WORKDIR /app/certs
COPY ./bin/deployment/gen_certs.sh .
RUN ./gen_certs.sh --cert_out ./candlepin-ca.crt --key_out ./candlepin-ca.key --hostname candlepin; \
    rm gen_certs.sh;

FROM quay.io/centos/centos:stream9
LABEL author="Josh Albrecht <jalbrech@redhat.com>"

USER root

# Update and install dependencies
RUN dnf -y update && \
    dnf -y update ca-certificates && \
    dnf install -y java-17-openjdk-headless openssl initscripts && \
    dnf clean all

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
COPY --from=builder /app/build /opt/tomcat/webapps

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
